// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.indexing.impl.IndexDebugProperties;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

public class IntToIntBtreeTest {

  private static final int ENOUGH_KEYS = 1 << 22;

  private static final int PAGE_SIZE = 32_768;

  private static final StorageLockContext LOCK_CONTEXT = new StorageLockContext(true, true);

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private IntToIntBtree bTree;
  private Int2IntMap generatedKeyValues;
  private File btreeFile;
  private PersistentIntToIntBtreeTestHelper btreeHelper;

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
      // Create a small storage file for metadata (1 page is enough)
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
      }, false); // Load from disk
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
      }, true); // Save to disk
      metadataStorage.force();
    }
    
    @Override
    public void close() throws IOException {
      saveMetadata();
      btree.doClose();
      metadataStorage.close();
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    IndexDebugProperties.DEBUG = true;
  }

  @Before
  public void setUp() throws Exception {
    btreeFile = temporaryFolder.newFile("btree");
    LOCK_CONTEXT.writeLock().lock();

    bTree = new IntToIntBtree(PAGE_SIZE, btreeFile.toPath(), LOCK_CONTEXT, /*createAnew: */ true);
    btreeHelper = new PersistentIntToIntBtreeTestHelper(bTree, 
                    btreeFile.toPath().resolveSibling(btreeFile.getName() + ".meta"), 
                    /*isNewBTree: */ true);
    
    generatedKeyValues = generateKeyValues(ENOUGH_KEYS);

    for (Map.Entry<Integer, Integer> e : generatedKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }
  }

  @After
  public void tearDown() throws Exception {
    try {
      if (btreeHelper != null) {
        btreeHelper.close();
      } else if (bTree != null) {
        bTree.doClose();
      }
    }
    finally {
      LOCK_CONTEXT.writeLock().unlock();
    }
  }

  /**
   * Helper method to close and reopen the BTree, simulating a persistence cycle.
   * Uses PersistentIntToIntBtreeTestHelper to properly save/load metadata.
   */
  private void reopenBTree() throws IOException {
    // Close and save metadata
    btreeHelper.close();
    
    // Reopen and load metadata
    bTree = new IntToIntBtree(PAGE_SIZE, btreeFile.toPath(), LOCK_CONTEXT, /*createAnew: */ false);
    btreeHelper = new PersistentIntToIntBtreeTestHelper(bTree, 
                    btreeFile.toPath().resolveSibling(btreeFile.getName() + ".meta"),
                    /*isNewBTree: */ false);
  }


  @Test
  public void allKeysPutIntoBTreeCouldBeFoundBackWithAssociatedValues() throws IOException {

    final int[] valueHolder = new int[1];
    final ObjectSet<Int2IntMap.Entry> entries = generatedKeyValues.int2IntEntrySet();
    for (final Int2IntMap.Entry entry : entries) {
      final int key = entry.getIntKey();
      final int expectedValue = entry.getIntValue();

      final boolean found = bTree.get(key, valueHolder);

      assertTrue("key[" + key + "] should be found in btree", found);
      assertEquals("key[" + key + "] should be mapped to value[" + expectedValue + "] by btree",
                   expectedValue,
                   valueHolder[0]);
    }
  }

  @Test
  public void allKeysValuesPutIntoBTreeCouldBeReadBackWithProcessMappings() throws IOException {

    final Int2IntMap bTreeContent = new Int2IntOpenHashMap(generatedKeyValues.size());
    bTree.processMappings(new IntToIntBtree.KeyValueProcessor() {
      @Override
      public boolean process(final int key,
                             final int value) throws IOException {
        bTreeContent.put(key, value);
        return true;
      }
    });
    assertEquals(
      "Keys-values read from bTree should be the same as were put into bTree before",
      generatedKeyValues,
      bTreeContent
    );
  }


  @Test
  public void keys_Not_PutIntoBTreeCould_Not_BeFoundBack() throws IOException {

    final int[] valueHolder = new int[1];
    final ThreadLocalRandom current = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_KEYS; i++) {
      final int key = nextKeyNotContainedInTree(current);
      final boolean found = bTree.get(key, valueHolder);

      assertFalse("key[" + key + "] should NOT be in BTree, but it is found, and mapped to value[" + valueHolder[0] + "]",
                  found);
    }
  }

  @Test
  public void overwrittenValuesAreReadBackAsWritten() throws IOException {

    final int[] allKeys = generatedKeyValues.keySet().intStream().toArray();
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final int[] valueHolder = new int[1];

    //overwrite random keys with new values: do enough ENOUGH_KEYS * 4 turns so a lot of keys
    // will be overwritten more than once
    for (int i = 0; i < ENOUGH_KEYS * 4; i++) {
      final int keyIndex = rnd.nextInt(allKeys.length);
      final int keyToOverwrite = allKeys[keyIndex];
      final int newValue = rnd.nextInt();
      generatedKeyValues.put(keyToOverwrite, newValue);
      bTree.put(keyToOverwrite, newValue);

      bTree.get(keyToOverwrite, valueHolder);
      assertEquals(
        "Value just written must be read back as-is",
        newValue,
        valueHolder[0]
      );
    }

    //check final state: bTree content is same as generatedKeyValues content:
    final ObjectSet<Int2IntMap.Entry> entries = generatedKeyValues.int2IntEntrySet();
    for (final Int2IntMap.Entry entry : entries) {
      final int key = entry.getIntKey();
      final int expectedValue = entry.getIntValue();

      final boolean found = bTree.get(key, valueHolder);

      assertTrue("key[" + key + "] should be found in btree", found);
      assertEquals("key[" + key + "] should be mapped to value[" + expectedValue + "] by btree",
                   expectedValue,
                   valueHolder[0]);
    }
  }

  //TODO RC: check all properties are kept after store/load

  @Test
  public void dataPersistedAfterCloseAndReopen() throws IOException {
    // Create a smaller dataset for faster test execution
    final int testKeysCount = 10_000;
    final Int2IntMap testKeyValues = generateKeyValues(testKeysCount);
    
    // Put all test data into BTree
    for (Map.Entry<Integer, Integer> e : testKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }
    
    // Close and reopen BTree
    reopenBTree();
    
    // Verify all keys are still present with correct values
    final int[] valueHolder = new int[1];
    for (Int2IntMap.Entry entry : testKeyValues.int2IntEntrySet()) {
      final int key = entry.getIntKey();
      final int expectedValue = entry.getIntValue();
      
      final boolean found = bTree.get(key, valueHolder);
      
      assertTrue("key[" + key + "] should be found after reopen", found);
      assertEquals("key[" + key + "] should have correct value after reopen",
                   expectedValue,
                   valueHolder[0]);
    }
  }

  @Test
  public void largeDatasetPersistedAfterReopen() throws IOException {
    // Use the existing large dataset (4.2M keys) from setUp()
    // Close and reopen BTree
    reopenBTree();
    
    // Verify data integrity by randomly sampling keys (checking all 4.2M would be slow)
    final int[] allKeys = generatedKeyValues.keySet().intStream().toArray();
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final int sampleSize = 100_000; // Sample 100K keys out of 4.2M
    final int[] valueHolder = new int[1];
    
    for (int i = 0; i < sampleSize; i++) {
      final int keyIndex = rnd.nextInt(allKeys.length);
      final int key = allKeys[keyIndex];
      final int expectedValue = generatedKeyValues.get(key);
      
      final boolean found = bTree.get(key, valueHolder);
      
      assertTrue("key[" + key + "] should be found after reopen (sample " + i + ")", found);
      assertEquals("key[" + key + "] should have correct value after reopen",
                   expectedValue,
                   valueHolder[0]);
    }
  }

  @Test
  public void incrementalWritesAcrossMultipleReopens() throws IOException {
    final int batchSize = 1_000_000;
    final Int2IntMap batch1 = generateKeyValues(batchSize);
    final Int2IntMap batch2 = generateKeyValues(batchSize);
    final Int2IntMap batch3 = generateKeyValues(batchSize);
    
    // Track all expected values (later batches overwrite earlier ones)
    final Int2IntMap allExpectedValues = new Int2IntOpenHashMap();
    
    // Write batch 1
    for (Map.Entry<Integer, Integer> e : batch1.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
      allExpectedValues.put(e.getKey(), e.getValue());
    }
    reopenBTree();
    
    // Write batch 2
    for (Map.Entry<Integer, Integer> e : batch2.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
      allExpectedValues.put(e.getKey(), e.getValue()); // Overwrites batch1 if key exists
    }
    reopenBTree();
    
    // Write batch 3
    for (Map.Entry<Integer, Integer> e : batch3.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
      allExpectedValues.put(e.getKey(), e.getValue()); // Overwrites earlier batches if key exists
    }
    reopenBTree();
    
    // Verify all keys have their final (most recent) values
    final int[] valueHolder = new int[1];
    
    for (Int2IntMap.Entry entry : allExpectedValues.int2IntEntrySet()) {
      final int key = entry.getIntKey();
      final int expectedValue = entry.getIntValue();
      
      assertTrue("key[" + key + "] should be found after multiple reopens", bTree.get(key, valueHolder));
      assertEquals("key[" + key + "] should have correct final value",
                   expectedValue, valueHolder[0]);
    }
  }

  @Test
  public void overwrittenValuesPersistedCorrectly() throws IOException {
    final int testKeysCount = 10_000;
    final Int2IntMap testKeyValues = generateKeyValues(testKeysCount);
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    
    // Put initial values
    for (Map.Entry<Integer, Integer> e : testKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }
    
    // Overwrite half of the keys with new values
    final int[] allKeys = testKeyValues.keySet().intStream().toArray();
    for (int i = 0; i < testKeysCount / 2; i++) {
      final int keyIndex = rnd.nextInt(allKeys.length);
      final int key = allKeys[keyIndex];
      final int newValue = rnd.nextInt();
      testKeyValues.put(key, newValue); // Update expected values
      bTree.put(key, newValue);
    }
    
    // Close and reopen
    reopenBTree();
    
    // Verify all keys have their latest (possibly overwritten) values
    final int[] valueHolder = new int[1];
    for (Int2IntMap.Entry entry : testKeyValues.int2IntEntrySet()) {
      final int key = entry.getIntKey();
      final int expectedValue = entry.getIntValue();
      
      final boolean found = bTree.get(key, valueHolder);
      
      assertTrue("key[" + key + "] should be found after reopen", found);
      assertEquals("key[" + key + "] should have latest overwritten value",
                   expectedValue,
                   valueHolder[0]);
    }
  }

  @Test
  public void flushEnsuresDataPersisted() throws IOException {
    final int testKeysCount = 10_000;
    final Int2IntMap testKeyValues = generateKeyValues(testKeysCount);
    
    // Put data and explicitly flush
    for (Map.Entry<Integer, Integer> e : testKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }
    bTree.doFlush();
    
    // Reopen (simulates crash recovery - data should survive due to flush)
    reopenBTree();
    
    // Verify all data is present
    final int[] valueHolder = new int[1];
    for (Int2IntMap.Entry entry : testKeyValues.int2IntEntrySet()) {
      final int key = entry.getIntKey();
      final int expectedValue = entry.getIntValue();
      
      final boolean found = bTree.get(key, valueHolder);
      
      assertTrue("key[" + key + "] should survive flush and reopen", found);
      assertEquals("key[" + key + "] should have correct value after flush and reopen",
                   expectedValue,
                   valueHolder[0]);
    }
  }

  @Test
  public void zeroKeyCanBeStoredAndRetrieved() throws IOException {
    final int zeroKeyValue = 42;
    final int[] valueHolder = new int[1];
    
    // Put key=0
    bTree.put(0, zeroKeyValue);
    
    // Verify key=0 can be retrieved
    final boolean found = bTree.get(0, valueHolder);
    
    assertTrue("key=0 should be found in BTree", found);
    assertEquals("key=0 should have correct value", zeroKeyValue, valueHolder[0]);
  }

  @Test
  public void zeroKeyPersistedAfterReopen() throws IOException {
    final int zeroKeyValue = 12345;
    
    // Put key=0
    bTree.put(0, zeroKeyValue);
    
    // Close and reopen
    reopenBTree();
    
    // Verify key=0 persisted
    final int[] valueHolder = new int[1];
    final boolean found = bTree.get(0, valueHolder);
    
    assertTrue("key=0 should be found after reopen", found);
    assertEquals("key=0 should have correct value after reopen", zeroKeyValue, valueHolder[0]);
  }

  @Test
  public void zeroKeyCanBeOverwritten() throws IOException {
    final int initialValue = 100;
    final int updatedValue = 200;
    final int[] valueHolder = new int[1];
    
    // Put key=0 with initial value
    bTree.put(0, initialValue);
    assertTrue("key=0 should be found", bTree.get(0, valueHolder));
    assertEquals("key=0 should have initial value", initialValue, valueHolder[0]);
    
    // Overwrite key=0 with new value
    bTree.put(0, updatedValue);
    assertTrue("key=0 should still be found", bTree.get(0, valueHolder));
    assertEquals("key=0 should have updated value", updatedValue, valueHolder[0]);
    
    // Verify persistence of overwritten value
    reopenBTree();
    assertTrue("key=0 should be found after reopen", bTree.get(0, valueHolder));
    assertEquals("key=0 should have updated value after reopen", updatedValue, valueHolder[0]);
  }

  @Test
  public void zeroKeyIndependentFromRegularKeys() throws IOException {
    final int zeroKeyValue = 999;
    final int[] valueHolder = new int[1];
    
    // Put key=0 and some regular keys
    bTree.put(0, zeroKeyValue);
    bTree.put(1, 111);
    bTree.put(2, 222);
    bTree.put(3, 333);
    
    // Verify all keys work correctly
    assertTrue("key=0 should be found", bTree.get(0, valueHolder));
    assertEquals("key=0 should have correct value", zeroKeyValue, valueHolder[0]);
    
    assertTrue("key=1 should be found", bTree.get(1, valueHolder));
    assertEquals("key=1 should have correct value", 111, valueHolder[0]);
    
    assertTrue("key=2 should be found", bTree.get(2, valueHolder));
    assertEquals("key=2 should have correct value", 222, valueHolder[0]);
    
    assertTrue("key=3 should be found", bTree.get(3, valueHolder));
    assertEquals("key=3 should have correct value", 333, valueHolder[0]);
    
    // Verify both zero and regular keys persist independently
    reopenBTree();
    
    assertTrue("key=0 should be found after reopen", bTree.get(0, valueHolder));
    assertEquals("key=0 should have correct value after reopen", zeroKeyValue, valueHolder[0]);
    
    assertTrue("key=1 should be found after reopen", bTree.get(1, valueHolder));
    assertEquals("key=1 should have correct value after reopen", 111, valueHolder[0]);
    
    assertTrue("key=2 should be found after reopen", bTree.get(2, valueHolder));
    assertEquals("key=2 should have correct value after reopen", 222, valueHolder[0]);
    
    assertTrue("key=3 should be found after reopen", bTree.get(3, valueHolder));
    assertEquals("key=3 should have correct value after reopen", 333, valueHolder[0]);
  }

  @Test
  public void emptyTreeReturnsNotFoundForAllKeys() throws IOException {
    // Create a new empty BTree (no pre-population from setUp)
    final File emptyFile = temporaryFolder.newFile("empty-btree");
    final IntToIntBtree emptyBTree = new IntToIntBtree(PAGE_SIZE, emptyFile.toPath(), LOCK_CONTEXT, true);
    final PersistentIntToIntBtreeTestHelper emptyHelper = 
      new PersistentIntToIntBtreeTestHelper(emptyBTree, 
                                             emptyFile.toPath().resolveSibling(emptyFile.getName() + ".meta"),
                                             /*isNewBTree: */ true);
    
    try {
      final int[] valueHolder = new int[1];
      final ThreadLocalRandom rnd = ThreadLocalRandom.current();
      
      // Verify random keys are not found
      for (int i = 0; i < 1000; i++) {
        final int key = rnd.nextInt();
        final boolean found = emptyBTree.get(key, valueHolder);
        assertFalse("Empty BTree should not contain key[" + key + "]", found);
      }
      
      // Verify processMappings completes with no entries
      final int[] entryCount = {0};
      emptyBTree.processMappings(new IntToIntBtree.KeyValueProcessor() {
        @Override
        public boolean process(int key, int value) {
          entryCount[0]++;
          return true;
        }
      });
      
      assertEquals("Empty BTree should have 0 entries via processMappings", 0, entryCount[0]);
    }
    finally {
      emptyHelper.close();
    }
  }

  @Test
  public void singleKeyTreeWorksCorrectly() throws IOException {
    // Create a new BTree with only a single key
    final File singleKeyFile = temporaryFolder.newFile("single-key-btree");
    IntToIntBtree singleKeyBTree = new IntToIntBtree(PAGE_SIZE, singleKeyFile.toPath(), LOCK_CONTEXT, true);
    PersistentIntToIntBtreeTestHelper singleKeyHelper = 
      new PersistentIntToIntBtreeTestHelper(singleKeyBTree, 
                                             singleKeyFile.toPath().resolveSibling(singleKeyFile.getName() + ".meta"),
                                             /*isNewBTree: */ true);
    
    try {
      final int singleKey = 42;
      final int singleValue = 84;
      final int[] valueHolder = new int[1];
      
      // Put single key
      singleKeyBTree.put(singleKey, singleValue);
      
      // Verify single key can be retrieved
      assertTrue("Single key should be found", singleKeyBTree.get(singleKey, valueHolder));
      assertEquals("Single key should have correct value", singleValue, valueHolder[0]);
      
      // Close and reopen with helper managing metadata
      singleKeyHelper.close();
      singleKeyBTree = new IntToIntBtree(PAGE_SIZE, singleKeyFile.toPath(), LOCK_CONTEXT, false);
      singleKeyHelper = new PersistentIntToIntBtreeTestHelper(singleKeyBTree, 
                                                               singleKeyFile.toPath().resolveSibling(singleKeyFile.getName() + ".meta"),
                                                               /*isNewBTree: */ false);
      
      // Verify single key persists
      assertTrue("Single key should be found after reopen", singleKeyBTree.get(singleKey, valueHolder));
      assertEquals("Single key should have correct value after reopen", singleValue, valueHolder[0]);
      
      // Verify other keys are not found
      assertFalse("Other keys should not be found", singleKeyBTree.get(singleKey + 1, valueHolder));
      assertFalse("Other keys should not be found", singleKeyBTree.get(singleKey - 1, valueHolder));
    }
    finally {
      singleKeyHelper.close();
    }
  }

  @Test
  public void boundaryValuesHandledCorrectly() throws IOException {
    final int[] valueHolder = new int[1];
    
    // Test Integer.MIN_VALUE and MAX_VALUE as both keys and values
    bTree.put(Integer.MIN_VALUE, 100);
    bTree.put(Integer.MAX_VALUE, 200);
    bTree.put(100, Integer.MIN_VALUE);
    bTree.put(200, Integer.MAX_VALUE);
    
    // Verify before reopen
    assertTrue("MIN_VALUE key should be found", bTree.get(Integer.MIN_VALUE, valueHolder));
    assertEquals("MIN_VALUE key should have correct value", 100, valueHolder[0]);
    
    assertTrue("MAX_VALUE key should be found", bTree.get(Integer.MAX_VALUE, valueHolder));
    assertEquals("MAX_VALUE key should have correct value", 200, valueHolder[0]);
    
    assertTrue("Key 100 should be found", bTree.get(100, valueHolder));
    assertEquals("Key 100 should have MIN_VALUE as value", Integer.MIN_VALUE, valueHolder[0]);
    
    assertTrue("Key 200 should be found", bTree.get(200, valueHolder));
    assertEquals("Key 200 should have MAX_VALUE as value", Integer.MAX_VALUE, valueHolder[0]);
    
    // Verify after reopen
    reopenBTree();
    
    assertTrue("MIN_VALUE key should be found after reopen", bTree.get(Integer.MIN_VALUE, valueHolder));
    assertEquals("MIN_VALUE key should have correct value after reopen", 100, valueHolder[0]);
    
    assertTrue("MAX_VALUE key should be found after reopen", bTree.get(Integer.MAX_VALUE, valueHolder));
    assertEquals("MAX_VALUE key should have correct value after reopen", 200, valueHolder[0]);
    
    assertTrue("Key 100 should be found after reopen", bTree.get(100, valueHolder));
    assertEquals("Key 100 should have MIN_VALUE as value after reopen", Integer.MIN_VALUE, valueHolder[0]);
    
    assertTrue("Key 200 should be found after reopen", bTree.get(200, valueHolder));
    assertEquals("Key 200 should have MAX_VALUE as value after reopen", Integer.MAX_VALUE, valueHolder[0]);
  }

  @Test
  public void sequentialKeysInsertionMaintainsCorrectness() throws IOException {
    final File seqFile = temporaryFolder.newFile("sequential-btree");
    final IntToIntBtree seqBTree = new IntToIntBtree(PAGE_SIZE, seqFile.toPath(), LOCK_CONTEXT, true);
    PersistentIntToIntBtreeTestHelper seqHelper = 
      new PersistentIntToIntBtreeTestHelper(seqBTree, 
                                             seqFile.toPath().resolveSibling(seqFile.getName() + ".meta"),
                                             /*isNewBTree: */ true);
    
    try {
      final int sequentialKeysCount = 100_000;
      final int[] valueHolder = new int[1];
      
      // Insert sequential keys (0, 1, 2, ..., 99999)
      for (int i = 0; i < sequentialKeysCount; i++) {
        seqBTree.put(i, i * 2); // value = key * 2
      }
      
      // Verify all keys are retrievable
      for (int i = 0; i < sequentialKeysCount; i++) {
        assertTrue("Sequential key[" + i + "] should be found", seqBTree.get(i, valueHolder));
        assertEquals("Sequential key[" + i + "] should have correct value", i * 2, valueHolder[0]);
      }
      
      // Close and reopen with helper managing metadata
      seqHelper.close();
      final IntToIntBtree reopenedSeqBTree = new IntToIntBtree(PAGE_SIZE, seqFile.toPath(), LOCK_CONTEXT, false);
      final PersistentIntToIntBtreeTestHelper reopenedHelper = 
        new PersistentIntToIntBtreeTestHelper(reopenedSeqBTree, 
                                               seqFile.toPath().resolveSibling(seqFile.getName() + ".meta"),
                                               /*isNewBTree: */ false);
      
      try {
        // Verify all keys persist correctly
        for (int i = 0; i < sequentialKeysCount; i++) {
          assertTrue("Sequential key[" + i + "] should be found after reopen", 
                     reopenedSeqBTree.get(i, valueHolder));
          assertEquals("Sequential key[" + i + "] should have correct value after reopen", 
                       i * 2, valueHolder[0]);
        }
        
        // Verify processMappings returns all entries
        final int[] entryCount = {0};
        reopenedSeqBTree.processMappings(new IntToIntBtree.KeyValueProcessor() {
          @Override
          public boolean process(int key, int value) {
            entryCount[0]++;
            return true;
          }
        });
        
        assertEquals("processMappings should return all sequential entries", 
                     sequentialKeysCount, entryCount[0]);
      }
      finally {
        reopenedHelper.close();
      }
    }
    catch (IOException e) {
      // Ensure cleanup on exception
      try {
        seqHelper.close();
      }
      catch (IOException ignored) {
      }
      throw e;
    }
  }

  @Test
  public void flushWithoutChangesSucceeds() throws IOException {
    final int testKeysCount = 1_000;
    final Int2IntMap testKeyValues = generateKeyValues(testKeysCount);
    
    // Put data and flush
    for (Map.Entry<Integer, Integer> e : testKeyValues.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }
    bTree.doFlush();
    
    // Call flush again without any changes - should succeed without errors
    bTree.doFlush();
    
    // Verify data is still correct after multiple flushes
    final int[] valueHolder = new int[1];
    for (Int2IntMap.Entry entry : testKeyValues.int2IntEntrySet()) {
      final int key = entry.getIntKey();
      final int expectedValue = entry.getIntValue();
      
      assertTrue("key[" + key + "] should be found after multiple flushes", bTree.get(key, valueHolder));
      assertEquals("key[" + key + "] should have correct value after multiple flushes",
                   expectedValue, valueHolder[0]);
    }
  }

  @Test
  public void multipleFlushesPreserveData() throws IOException {
    final int batchSize = 10_000;
    final Int2IntMap batch1 = generateKeyValues(batchSize);
    final Int2IntMap batch2 = generateKeyValues(batchSize);
    final Int2IntMap batch3 = generateKeyValues(batchSize);
    
    // Batch 1: put and flush
    for (Map.Entry<Integer, Integer> e : batch1.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }
    bTree.doFlush();
    
    // Batch 2: put and flush
    for (Map.Entry<Integer, Integer> e : batch2.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }
    bTree.doFlush();
    
    // Batch 3: put and flush
    for (Map.Entry<Integer, Integer> e : batch3.int2IntEntrySet()) {
      bTree.put(e.getKey(), e.getValue());
    }
    bTree.doFlush();
    
    // Reopen to verify all batches persisted
    reopenBTree();
    
    final int[] valueHolder = new int[1];
    
    // Verify batch 1
    for (Int2IntMap.Entry entry : batch1.int2IntEntrySet()) {
      final int key = entry.getIntKey();
      assertTrue("Batch 1 key[" + key + "] should be found after multiple flushes and reopen",
                 bTree.get(key, valueHolder));
      assertEquals("Batch 1 key[" + key + "] should have correct value",
                   entry.getIntValue(), valueHolder[0]);
    }
    
    // Verify batch 2
    for (Int2IntMap.Entry entry : batch2.int2IntEntrySet()) {
      final int key = entry.getIntKey();
      assertTrue("Batch 2 key[" + key + "] should be found after multiple flushes and reopen",
                 bTree.get(key, valueHolder));
      assertEquals("Batch 2 key[" + key + "] should have correct value",
                   entry.getIntValue(), valueHolder[0]);
    }
    
    // Verify batch 3
    for (Int2IntMap.Entry entry : batch3.int2IntEntrySet()) {
      final int key = entry.getIntKey();
      assertTrue("Batch 3 key[" + key + "] should be found after multiple flushes and reopen",
                 bTree.get(key, valueHolder));
      assertEquals("Batch 3 key[" + key + "] should have correct value",
                   entry.getIntValue(), valueHolder[0]);
    }
  }


  /* ================================= INFRASTRUCTURE: ================================================================== */

  private int nextKeyNotContainedInTree(final ThreadLocalRandom current) {
    for (int i = 0; i < 1024; i++) {
      final int key = current.nextInt();
      if (!generatedKeyValues.containsKey(key)) {
        return key;
      }
    }
    //avoid test hangs in pathological cases:
    throw new IllegalStateException("Something is wrong with 1024 random ints all contained in generatedKeyValues");
  }


  private static Int2IntMap generateKeyValues(final int keysCount) {
    final Int2IntMap keyValues = new Int2IntOpenHashMap(keysCount);
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < keysCount; i++) {
      final int key = rnd.nextInt();
      final int value = rnd.nextInt();
      keyValues.put(key, value);
    }
    return keyValues;
  }
}