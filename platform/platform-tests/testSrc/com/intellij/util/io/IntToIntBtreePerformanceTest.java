// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SkipSlowTestLocally
public class IntToIntBtreePerformanceTest {
  private static final Logger LOG = Logger.getInstance(IntToIntBtreePerformanceTest.class);
  
  private static final int PAGE_SIZE = 32768;
  private static final StorageLockContext LOCK_CONTEXT = new StorageLockContext(true, true);
  
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();
  
  private File btreeFile;
  private IntToIntBtree btree;
  private PersistentIntToIntBtreeTestHelper helper;
  
  /**
   * Test helper that manages metadata persistence for IntToIntBtree.
   * Mimics the approach used by PersistentBTreeEnumerator to properly save/load
   * BTree metadata (height, counts, root address, etc.) to a separate storage.
   */
  private static class PersistentIntToIntBtreeTestHelper implements Closeable {
    private final IntToIntBtree btree;
    private final PagedFileStorage metadataStorage;
    private static final int METADATA_START = 0;
    
    PersistentIntToIntBtreeTestHelper(IntToIntBtree btree, Path metadataFile, boolean isNewBTree) throws IOException {
      this.btree = btree;
      this.metadataStorage = new PagedFileStorage(metadataFile, LOCK_CONTEXT, 
                                                   PAGE_SIZE, true, true);
      if (!isNewBTree) {
        loadMetadata();
      }
    }
    
    private void loadMetadata() throws IOException {
      btree.persistVars(new IntToIntBtree.BtreeDataStorage() {
        @Override
        public int persistInt(int offset, int value, boolean toDisk) throws IOException {
          if (toDisk) {
            metadataStorage.putInt(METADATA_START + offset, value);
          } else {
            value = metadataStorage.getInt(METADATA_START + offset);
          }
          return value;
        }
      }, false);
    }
    
    void saveMetadata() throws IOException {
      btree.persistVars(new IntToIntBtree.BtreeDataStorage() {
        @Override
        public int persistInt(int offset, int value, boolean toDisk) throws IOException {
          if (toDisk) {
            metadataStorage.putInt(METADATA_START + offset, value);
          } else {
            value = metadataStorage.getInt(METADATA_START + offset);
          }
          return value;
        }
      }, true);
      metadataStorage.force();
    }
    
    @Override
    public void close() throws IOException {
      saveMetadata();
      btree.doClose();
      metadataStorage.close();
    }
  }
  
  @Before
  public void setUp() throws Exception {
    btreeFile = tempDir.newFile("btree");
    LOCK_CONTEXT.writeLock().lock();
    btree = new IntToIntBtree(PAGE_SIZE, btreeFile.toPath(), LOCK_CONTEXT, true);
    helper = new PersistentIntToIntBtreeTestHelper(btree, 
                btreeFile.toPath().resolveSibling(btreeFile.getName() + ".meta"), 
                true);
  }
  
  @After
  public void tearDown() throws Exception {
    try {
      if (helper != null) {
        helper.close();
      } else if (btree != null) {
        btree.doClose();
      }
    }
    finally {
      LOCK_CONTEXT.writeLock().unlock();
    }
  }
  
  private static Int2IntMap generateKeyValues(int count) {
    Int2IntMap result = new Int2IntOpenHashMap(count);
    Random random = new Random(count);
    for (int i = 0; i < count; i++) {
      result.put(random.nextInt(), random.nextInt());
    }
    return result;
  }
  
  @Test
  public void testSequentialPutPerformance() {
    final int count = 1_000_000;
    final Int2IntMap keyValues = generateKeyValues(count);
    
    Benchmark.newBenchmark("IntToIntBtree sequential put (1M entries)", () -> {
      for (Int2IntMap.Entry entry : keyValues.int2IntEntrySet()) {
        btree.put(entry.getIntKey(), entry.getIntValue());
      }
    }).warmupIterations(0).attempts(1).runAsStressTest().start();
    
    LOG.debug(String.format("File size = %d bytes", btreeFile.length()));
  }
  
  @Test
  public void testRandomGetPerformance() throws IOException {
    final int count = 1_000_000;
    final Int2IntMap keyValues = generateKeyValues(count);
    
    // Setup: populate btree
    for (Int2IntMap.Entry entry : keyValues.int2IntEntrySet()) {
      btree.put(entry.getIntKey(), entry.getIntValue());
    }
    
    final int[] valueHolder = new int[1];
    final int[] keys = keyValues.keySet().toIntArray();
    
    Benchmark.newBenchmark("IntToIntBtree random get (1M entries)", () -> {
      for (int key : keys) {
        assertTrue(btree.get(key, valueHolder));
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
  }
  
  @Test
  public void testMixedWorkload() throws IOException {
    final int count = 1_000_000;
    final Int2IntMap keyValues = generateKeyValues(count);
    
    // Setup: populate with half the data
    int i = 0;
    for (Int2IntMap.Entry entry : keyValues.int2IntEntrySet()) {
      if (i++ >= count / 2) break;
      btree.put(entry.getIntKey(), entry.getIntValue());
    }
    
    final int[] valueHolder = new int[1];
    final int[] keys = keyValues.keySet().toIntArray();
    
    Benchmark.newBenchmark("IntToIntBtree mixed 50/50 read/write (1M ops)", () -> {
      for (int idx = 0; idx < count; idx++) {
        if (idx % 2 == 0) {
          // Read
          btree.get(keys[idx % keys.length], valueHolder);
        } else {
          // Write
          Int2IntMap.Entry entry = keyValues.int2IntEntrySet().iterator().next();
          btree.put(entry.getIntKey(), entry.getIntValue());
        }
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
  }
  
  @Test
  public void testProcessMappingsPerformance() throws IOException {
    final int[] counts = {100_000, 500_000, 1_000_000};
    
    for (int count : counts) {
      // Setup fresh btree for each size
      helper.close();
      btree = new IntToIntBtree(PAGE_SIZE, btreeFile.toPath(), LOCK_CONTEXT, true);
      helper = new PersistentIntToIntBtreeTestHelper(btree, 
                  btreeFile.toPath().resolveSibling(btreeFile.getName() + ".meta"), 
                  true);
      
      Int2IntMap keyValues = generateKeyValues(count);
      for (Int2IntMap.Entry entry : keyValues.int2IntEntrySet()) {
        btree.put(entry.getIntKey(), entry.getIntValue());
      }
      
      final int[] processedCount = {0};
      Benchmark.newBenchmark("IntToIntBtree processMappings (" + count + " entries)", () -> {
        processedCount[0] = 0;
        btree.processMappings(new IntToIntBtree.KeyValueProcessor() {
          @Override
          public boolean process(int key, int value) {
            processedCount[0]++;
            return true;
          }
        });
      }).warmupIterations(1).attempts(2).runAsStressTest().start();
      
      assertEquals(keyValues.size(), processedCount[0]);
    }
  }
  
  @Test
  public void testPersistenceOverhead() throws IOException {
    final int count = 100_000;
    final Int2IntMap keyValues = generateKeyValues(count);
    
    // Populate
    for (Int2IntMap.Entry entry : keyValues.int2IntEntrySet()) {
      btree.put(entry.getIntKey(), entry.getIntValue());
    }
    
    // Measure flush time
    Benchmark.newBenchmark("IntToIntBtree flush (100k entries)", () -> {
      btree.doFlush();
    }).warmupIterations(1).attempts(3).runAsStressTest().start();
    
    // Measure close time
    Benchmark.newBenchmark("IntToIntBtree close (100k entries)", () -> {
      helper.saveMetadata();
    }).warmupIterations(0).attempts(1).runAsStressTest().start();
    
    helper.close();
    
    // Measure reopen time
    Benchmark.newBenchmark("IntToIntBtree reopen (100k entries)", () -> {
      btree = new IntToIntBtree(PAGE_SIZE, btreeFile.toPath(), LOCK_CONTEXT, false);
      helper = new PersistentIntToIntBtreeTestHelper(btree, 
                  btreeFile.toPath().resolveSibling(btreeFile.getName() + ".meta"),
                  false);
    }).warmupIterations(0).attempts(1).runAsStressTest().start();
    
    // Verify data
    final int[] valueHolder = new int[1];
    for (Int2IntMap.Entry entry : keyValues.int2IntEntrySet()) {
      assertTrue(btree.get(entry.getIntKey(), valueHolder));
      assertEquals(entry.getIntValue(), valueHolder[0]);
    }
  }
  
  @Test
  public void testZeroKeyHandling() {
    final int count = 1_000_000;
    
    // Test zero key performance
    Benchmark.newBenchmark("IntToIntBtree put key=0 (1M times)", () -> {
      for (int i = 0; i < count; i++) {
        btree.put(0, i);
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
    
    final int[] valueHolder = new int[1];
    Benchmark.newBenchmark("IntToIntBtree get key=0 (1M times)", () -> {
      for (int i = 0; i < count; i++) {
        assertTrue(btree.get(0, valueHolder));
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
    
    // Compare with regular key
    Benchmark.newBenchmark("IntToIntBtree put regular key (1M times)", () -> {
      for (int i = 0; i < count; i++) {
        btree.put(12345, i);
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
    
    Benchmark.newBenchmark("IntToIntBtree get regular key (1M times)", () -> {
      for (int i = 0; i < count; i++) {
        assertTrue(btree.get(12345, valueHolder));
      }
    }).warmupIterations(2).attempts(3).runAsStressTest().start();
  }
  
  @Test
  public void testScalability() throws IOException {
    final int[] sizes = {10_000, 100_000, 1_000_000, 4_000_000};
    
    for (int size : sizes) {
      // Fresh btree for each size
      helper.close();
      btree = new IntToIntBtree(PAGE_SIZE, btreeFile.toPath(), LOCK_CONTEXT, true);
      helper = new PersistentIntToIntBtreeTestHelper(btree, 
                  btreeFile.toPath().resolveSibling(btreeFile.getName() + ".meta"), 
                  true);
      
      final Int2IntMap keyValues = generateKeyValues(size);
      
      Benchmark.newBenchmark("IntToIntBtree put (" + size + " entries)", () -> {
        for (Int2IntMap.Entry entry : keyValues.int2IntEntrySet()) {
          btree.put(entry.getIntKey(), entry.getIntValue());
        }
      }).warmupIterations(0).attempts(1).runAsStressTest().start();
      
      LOG.debug(String.format("Size %d: file size = %d bytes", size, btreeFile.length()));
    }
  }
}
