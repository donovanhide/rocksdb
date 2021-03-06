// Copyright (c) 2014, Facebook, Inc.  All rights reserved.
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree. An additional grant
// of patent rights can be found in the PATENTS file in the same directory.

import java.util.Arrays;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import java.io.IOException;

public class RocksDBSample {
  static {
    System.loadLibrary("rocksdbjni");
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("usage: RocksDBSample db_path");
      return;
    }
    String db_path = args[0];
    String db_path_not_found = db_path + "_not_found";

    System.out.println("RocksDBSample");
    RocksDB db = null;
    Options options = new Options();
    try {
      db = RocksDB.open(options, db_path_not_found);
      assert(false);
    } catch (RocksDBException e) {
      System.out.format("caught the expceted exception -- %s\n", e);
      assert(db == null);
    }

    Filter filter = new BloomFilter(10);
    options.setCreateIfMissing(true)
        .createStatistics()
        .setWriteBufferSize(8 * SizeUnit.KB)
        .setMaxWriteBufferNumber(3)
        .setDisableSeekCompaction(true)
        .setBlockSize(64 * SizeUnit.KB)
        .setMaxBackgroundCompactions(10)
        .setFilter(filter);
    Statistics stats = options.statisticsPtr();

    assert(options.createIfMissing() == true);
    assert(options.writeBufferSize() == 8 * SizeUnit.KB);
    assert(options.maxWriteBufferNumber() == 3);
    assert(options.disableSeekCompaction() == true);
    assert(options.blockSize() == 64 * SizeUnit.KB);
    assert(options.maxBackgroundCompactions() == 10);

    assert(options.memTableFactoryName().equals("SkipListFactory"));
    options.setMemTableConfig(
        new HashSkipListMemTableConfig()
            .setHeight(4)
            .setBranchingFactor(4)
            .setBucketCount(2000000));
    assert(options.memTableFactoryName().equals("HashSkipListRepFactory"));

    options.setMemTableConfig(
        new HashLinkedListMemTableConfig()
            .setBucketCount(100000));
    assert(options.memTableFactoryName().equals("HashLinkedListRepFactory"));

    options.setMemTableConfig(
        new VectorMemTableConfig().setReservedSize(10000));
    assert(options.memTableFactoryName().equals("VectorRepFactory"));

    options.setMemTableConfig(new SkipListMemTableConfig());
    assert(options.memTableFactoryName().equals("SkipListFactory"));

    options.setTableFormatConfig(new PlainTableConfig());
    assert(options.tableFactoryName().equals("PlainTable"));

    try {
      db = RocksDB.open(options, db_path_not_found);
      db.put("hello".getBytes(), "world".getBytes());
      byte[] value = db.get("hello".getBytes());
      assert("world".equals(new String(value)));
    } catch (RocksDBException e) {
      System.out.format("[ERROR] caught the unexpceted exception -- %s\n", e);
      assert(db == null);
      assert(false);
    }
    // be sure to release the c++ pointer
    db.close();

    ReadOptions readOptions = new ReadOptions();
    readOptions.setFillCache(false);

    try {
      db = RocksDB.open(options, db_path);
      db.put("hello".getBytes(), "world".getBytes());
      byte[] value = db.get("hello".getBytes());
      System.out.format("Get('hello') = %s\n",
          new String(value));

      for (int i = 1; i <= 9; ++i) {
        for (int j = 1; j <= 9; ++j) {
          db.put(String.format("%dx%d", i, j).getBytes(),
                 String.format("%d", i * j).getBytes());
        }
      }

      for (int i = 1; i <= 9; ++i) {
        for (int j = 1; j <= 9; ++j) {
          System.out.format("%s ", new String(db.get(
              String.format("%dx%d", i, j).getBytes())));
        }
        System.out.println("");
      }

      value = db.get("1x1".getBytes());
      assert(value != null);
      value = db.get("world".getBytes());
      assert(value == null);
      value = db.get(readOptions, "world".getBytes());
      assert(value == null);

      byte[] testKey = "asdf".getBytes();
      byte[] testValue =
          "asdfghjkl;'?><MNBVCXZQWERTYUIOP{+_)(*&^%$#@".getBytes();
      db.put(testKey, testValue);
      byte[] testResult = db.get(testKey);
      assert(testResult != null);
      assert(Arrays.equals(testValue, testResult));
      assert(new String(testValue).equals(new String(testResult)));
      testResult = db.get(readOptions, testKey);
      assert(testResult != null);
      assert(Arrays.equals(testValue, testResult));
      assert(new String(testValue).equals(new String(testResult)));

      byte[] insufficientArray = new byte[10];
      byte[] enoughArray = new byte[50];
      int len;
      len = db.get(testKey, insufficientArray);
      assert(len > insufficientArray.length);
      len = db.get("asdfjkl;".getBytes(), enoughArray);
      assert(len == RocksDB.NOT_FOUND);
      len = db.get(testKey, enoughArray);
      assert(len == testValue.length);

      len = db.get(readOptions, testKey, insufficientArray);
      assert(len > insufficientArray.length);
      len = db.get(readOptions, "asdfjkl;".getBytes(), enoughArray);
      assert(len == RocksDB.NOT_FOUND);
      len = db.get(readOptions, testKey, enoughArray);
      assert(len == testValue.length);

      db.remove(testKey);
      len = db.get(testKey, enoughArray);
      assert(len == RocksDB.NOT_FOUND);

      // repeat the test with WriteOptions
      WriteOptions writeOpts = new WriteOptions();
      writeOpts.setSync(true);
      writeOpts.setDisableWAL(true);
      db.put(writeOpts, testKey, testValue);
      len = db.get(testKey, enoughArray);
      assert(len == testValue.length);
      assert(new String(testValue).equals(
          new String(enoughArray, 0, len)));
      writeOpts.dispose();

      try {
        for (TickerType statsType : TickerType.values()) {
          stats.getTickerCount(statsType);
        }
        System.out.println("getTickerCount() passed.");
      } catch (Exception e) {
        System.out.println("Failed in call to getTickerCount()");
        assert(false); //Should never reach here.
      }

      try {
        for (HistogramType histogramType : HistogramType.values()) {
          HistogramData data = stats.geHistogramData(histogramType);
        }
        System.out.println("geHistogramData() passed.");
      } catch (Exception e) {
        System.out.println("Failed in call to geHistogramData()");
        assert(false); //Should never reach here.
      }

      Iterator iterator = db.newIterator();

      boolean seekToFirstPassed = false;
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        iterator.status();
        assert(iterator.key() != null);
        assert(iterator.value() != null);
        seekToFirstPassed = true;
      }
      if(seekToFirstPassed) {
        System.out.println("iterator seekToFirst tests passed.");
      }

      boolean seekToLastPassed = false;
      for (iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
        iterator.status();
        assert(iterator.key() != null);
        assert(iterator.value() != null);
        seekToLastPassed = true;
      }

      if(seekToLastPassed) {
        System.out.println("iterator seekToLastPassed tests passed.");
      }

      iterator.seekToFirst();
      iterator.seek(iterator.key());
      assert(iterator.key() != null);
      assert(iterator.value() != null);

      System.out.println("iterator seek test passed.");

      iterator.close();
      System.out.println("iterator tests passed.");
    } catch (RocksDBException e) {
      System.err.println(e);
    }
    if (db != null) {
      db.close();
    }
    // be sure to dispose c++ pointers
    options.dispose();
    readOptions.dispose();
  }
}
