// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.containers.IntObjectCache;
import com.intellij.util.io.stats.FilePageCacheStatistics;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class PersistentBTreeEnumeratorTest {
  private static final Logger LOG = Logger.getInstance(PersistentBTreeEnumeratorTest.class);

  private static final String COLLISION_1 = "";
  private static final String COLLISION_2 = "\u0000";
  private static final String UTF_1 = "\ue534";
  private static final String UTF_2 = StringUtil.repeatSymbol('a', 624);

  static class TestStringEnumerator extends PersistentBTreeEnumerator<String> {
    TestStringEnumerator(File file) throws IOException {
      super(file.toPath(), new EnumeratorStringDescriptor(), 4096);
    }
  }

  @Rule public TempDirectory tempDir = new TempDirectory();

  private TestStringEnumerator myEnumerator;
  private File myFile;

  @Before
  public void setUp() throws IOException {
    myFile = tempDir.newFile("persistent-trie");
    myEnumerator = new TestStringEnumerator(myFile);
  }

  @After
  public void tearDown() throws IOException {
    myEnumerator.close();
    IOUtil.deleteAllFilesStartingWith(myFile);
    assertFalse(myFile.exists());
  }

  @Test
  public void testAddEqualStrings() throws IOException {
    int index = myEnumerator.enumerate("IntelliJ IDEA");
    myEnumerator.enumerate("Just another string");
    assertEquals(index, myEnumerator.enumerate("IntelliJ IDEA"));
  }

  @Test
  public void testAddEqualStringsAndMuchGarbage() throws IOException {
    Map<Integer, String> strings = new HashMap<>(10001);
    String s = "IntelliJ IDEA";
    int index = myEnumerator.enumerate(s);
    strings.put(index, s);

    // clear strings and nodes cache
    for (int i = 0; i < 10000; ++i) {
      String v = i + "Just another string";
      int idx = myEnumerator.enumerate(v);
      assertEquals(v, myEnumerator.valueOf(idx));
      strings.put(idx, v);
    }

    for (Map.Entry<Integer, String> e : strings.entrySet()) {
      assertEquals((int)e.getKey(), myEnumerator.enumerate(e.getValue()));
    }

    Set<String> enumerated = new HashSet<>(myEnumerator.getAllDataObjects(null));
    assertEquals(new HashSet<>(strings.values()), enumerated);
  }

  @Test
  public void testCollision() throws IOException {
    int id1 = myEnumerator.enumerate(COLLISION_1);
    int id2 = myEnumerator.enumerate(COLLISION_2);
    assertNotEquals(id1, id2);

    assertEquals(COLLISION_1, myEnumerator.valueOf(id1));
    assertEquals(COLLISION_2, myEnumerator.valueOf(id2));
    assertEquals(Set.of(COLLISION_1, COLLISION_2),
                 new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  @Test
  public void testCollision1() throws IOException {
    int id1 = myEnumerator.enumerate(COLLISION_1);

    assertEquals(id1, myEnumerator.tryEnumerate(COLLISION_1));
    assertEquals(PersistentEnumeratorBase.NULL_ID, myEnumerator.tryEnumerate(COLLISION_2));

    int id2 = myEnumerator.enumerate(COLLISION_2);
    assertNotEquals(id1, id2);

    assertEquals(id1, myEnumerator.tryEnumerate(COLLISION_1));
    assertEquals(id2, myEnumerator.tryEnumerate(COLLISION_2));
    assertEquals(PersistentEnumeratorBase.NULL_ID, myEnumerator.tryEnumerate("some string"));

    assertEquals(COLLISION_1, myEnumerator.valueOf(id1));
    assertEquals(COLLISION_2, myEnumerator.valueOf(id2));
    assertEquals(Set.of(COLLISION_1, COLLISION_2), new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  @Test
  public void testUTFString() throws IOException {
    int id1 = myEnumerator.enumerate(UTF_1);
    int id2 = myEnumerator.enumerate(UTF_2);
    assertNotEquals(id1, id2);

    assertEquals(UTF_1, myEnumerator.valueOf(id1));
    assertEquals(UTF_2, myEnumerator.valueOf(id2));
    assertEquals(Set.of(UTF_1, UTF_2), new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  @Test
  public void testOpeningClosing() throws IOException {
    ArrayList<String> strings = new ArrayList<>(2000);
    for (int i = 0; i < 2000; ++i) {
      strings.add(createRandomString());
    }
    for (int i = 0; i < 2000; ++i) {
      myEnumerator.enumerate(strings.get(i));
      myEnumerator.close();
      myEnumerator = new TestStringEnumerator(myFile);
    }
    for (int i = 0; i < 2000; ++i) {
      myEnumerator.enumerate(strings.get(i));
      assertFalse(myEnumerator.isDirty());
      myEnumerator.close();
      myEnumerator = new TestStringEnumerator(myFile);
    }
    for (int i = 0; i < 2000; ++i) {
      assertFalse(myEnumerator.isDirty());
      myEnumerator.close();
      myEnumerator = new TestStringEnumerator(myFile);
    }
    HashSet<String> allStringsSet = new HashSet<>(strings);
    assertEquals(allStringsSet, new HashSet<>(myEnumerator.getAllDataObjects(null)));

    String additionalString = createRandomString();
    allStringsSet.add(additionalString);
    myEnumerator.enumerate(additionalString);
    assertTrue(myEnumerator.isDirty());
    assertEquals(allStringsSet, new HashSet<>(myEnumerator.getAllDataObjects(null)));
  }

  @Test
  public void testValueOfForUnExistedData() throws IOException {
    assertNull(myEnumerator.valueOf(-10));
    assertNull(myEnumerator.valueOf(0));

    assertNull(myEnumerator.valueOf(1));
    assertNull(myEnumerator.valueOf(1000));

    String string = createRandomString();
    int value = myEnumerator.enumerate(string);
    assertNotEquals(1000, value);

    assertNull(myEnumerator.valueOf(1000));
    assertEquals(string, myEnumerator.valueOf(value));

    myEnumerator.force();

    assertNull(myEnumerator.valueOf(1000));
    assertEquals(string, myEnumerator.valueOf(value));
  }

  @Test
  public void testEmptyEnumeratorTryEnumerateDoesntAccessDisk() throws IOException {
    myEnumerator.force();
    boolean isEmpty = myEnumerator.processAllDataObject(s -> false, null);
    assertTrue(isEmpty);

    StorageLockContext.forceDirectMemoryCache();
    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked();

    FilePageCacheStatistics statsBefore = StorageLockContext.getStatistics();

    myEnumerator.tryEnumerate("qwerty");

    FilePageCacheStatistics statsAfter = StorageLockContext.getStatistics();

    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked();

    // ensure enumerator didn't request any page

    int pageLoadDiff = statsAfter.getRegularPageLoads() - statsBefore.getRegularPageLoads();
    int pageMissDiff = statsAfter.getPageLoadsAboveSizeThreshold() - statsBefore.getPageLoadsAboveSizeThreshold();
    int pageHitDiff = statsAfter.getPageHits() - statsBefore.getPageHits();
    int pageFastHitDiff = statsAfter.getPageFastCacheHits() - statsBefore.getPageFastCacheHits();

    assertEquals(0, pageLoadDiff);
    assertEquals(0, pageMissDiff);
    assertEquals(0, pageHitDiff);
    assertEquals(0, pageFastHitDiff);
  }

  @PerformanceUnitTest
  @Test
  public void testSmallEnumeratorTryEnumeratePerformance() throws IOException {
    List<String> data = Arrays.asList("qwe", "asd", "zxc", "123");
    for (String item : data) {
      myEnumerator.enumerate(item);
    }

    List<String> absentData = Arrays.asList("456", "789", "jjj", "kkk");

    StorageLockContext.forceDirectMemoryCache();
    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked();

    FilePageCacheStatistics statsBefore = StorageLockContext.getStatistics();
    Benchmark.newBenchmark("PersistentStringEnumerator", () -> {
      for (int i = 0; i < 10000; i++) {
        for (String item : data) {
          assertNotEquals(0, myEnumerator.tryEnumerate(item));
        }

        for (String item : absentData) {
          assertEquals(0, myEnumerator.tryEnumerate(item));
        }
      }
    }).warmupIterations(0).attempts(1).start();
    FilePageCacheStatistics statsAfter = StorageLockContext.getStatistics();

    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked();

    // ensure enumerator didn't request any page

    int pageLoadDiff = statsAfter.getRegularPageLoads() - statsBefore.getRegularPageLoads();
    int pageMissDiff = statsAfter.getPageLoadsAboveSizeThreshold() - statsBefore.getPageLoadsAboveSizeThreshold();
    int pageHitDiff = statsAfter.getPageHits() - statsBefore.getPageHits();
    int pageFastHitDiff = statsAfter.getPageFastCacheHits() - statsBefore.getPageFastCacheHits();

    assertEquals(1, pageLoadDiff);
    assertEquals(0, pageMissDiff);
    assertEquals(0, pageHitDiff);
    assertEquals(0, pageFastHitDiff);
  }

  @Test
  public void testEnumeratorDiskAccessCount() throws IOException {
    StorageLockContext.forceDirectMemoryCache();
    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked();
    FilePageCacheStatistics statsBefore = StorageLockContext.getStatistics();

    for (int i = 0; i < 1000; i++) {
      myEnumerator.enumerate("value" + i);
    }
    for (int i = 0; i < 1000; i++) {
      myEnumerator.tryEnumerate("value" + i);
    }

    FilePageCacheStatistics statsAfter = StorageLockContext.getStatistics();
    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked();

    int pageLoadDiff = statsAfter.getRegularPageLoads() - statsBefore.getRegularPageLoads();
    int pageMissDiff = statsAfter.getPageLoadsAboveSizeThreshold() - statsBefore.getPageLoadsAboveSizeThreshold();
    int pageHitDiff = statsAfter.getPageHits() - statsBefore.getPageHits();
    int pageFastHitDiff = statsAfter.getPageFastCacheHits() - statsBefore.getPageFastCacheHits();

    assertEquals(3, pageLoadDiff);
    assertEquals(0, pageMissDiff);
    assertEquals(0, pageHitDiff);
    assertEquals(1929, pageFastHitDiff);
  }

  @Test
  public void testEnumeratorRootRecaching() throws IOException {
    List<String> data = Arrays.asList("qwe", "asd", "zxc", "123");
    for (String item : data) {
      myEnumerator.enumerate(item);
    }

    StorageLockContext.forceDirectMemoryCache();
    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked();

    FilePageCacheStatistics statsBefore = StorageLockContext.getStatistics();
    for (String item : data) {
      assertNotEquals(0, myEnumerator.tryEnumerate(item));
      StorageLockContext.forceDirectMemoryCache();
    }
    FilePageCacheStatistics statsAfter = StorageLockContext.getStatistics();

    // ensure we don't cache anything
    StorageLockContext.assertNoBuffersLocked();

    // ensure enumerator didn't request any page

    int pageLoadDiff = statsAfter.getRegularPageLoads() - statsBefore.getRegularPageLoads();
    int pageMissDiff = statsAfter.getPageLoadsAboveSizeThreshold() - statsBefore.getPageLoadsAboveSizeThreshold();
    int pageHitDiff = statsAfter.getPageHits() - statsBefore.getPageHits();

    assertEquals(4, pageLoadDiff);
    assertEquals(0, pageMissDiff);
    assertEquals(0, pageHitDiff);
  }

  @PerformanceUnitTest
  @Test
  public void testPerformance() throws IOException {
    IntObjectCache<String> stringCache = new IntObjectCache<>(2000);
    IntObjectCache.DeletedPairsListener<String> listener = (key, value) -> {
      try {
        assertEquals(myEnumerator.enumerate(value), key);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    Benchmark.newBenchmark("PersistentStringEnumerator", () -> {
      stringCache.addDeletedPairsListener(listener);
      for (int i = 0; i < 100000; ++i) {
        String string = createRandomString();
        stringCache.cacheObject(myEnumerator.enumerate(string), string);
      }
      stringCache.removeDeletedPairsListener(listener);
      stringCache.removeAll();
    }).start();
    myEnumerator.close();
    LOG.debug(String.format("File size = %d bytes\n", myFile.length()));
  }

  @Test
  public void testCorruptionRecovery() throws IOException {
    System.setProperty(PersistentBTreeEnumerator.DO_SELF_HEAL_PROP, Boolean.toString(true));
    try {
      String[] values = new String[] {"AAA", "BBB", "CCC", "DDD", "EEE", "HHH", "JJJ", "ZZZ"};
      int[] ids = new int[values.length];
      for (int i = 0, length = values.length; i < length; i++) {
        String value = values[i];
        ids[i] = myEnumerator.enumerate(value);
      }

      for (int i = 0; i < values.length; i++) {
        String value = values[i];
        assertEquals(ids[i], myEnumerator.catchCorruption(new CorruptAndEnumerateAfter(value)).intValue());
      }
    }
    finally {
      System.setProperty(PersistentBTreeEnumerator.DO_SELF_HEAL_PROP, Boolean.toString(false));
    }
  }

  @Test
  public void testCorruptionRecoveryForLargeEnumerator() throws IOException {
    System.setProperty(PersistentBTreeEnumerator.DO_SELF_HEAL_PROP, Boolean.toString(true));
    try {
      List<String> values = new ArrayList<>();
      IntList ids = new IntArrayList();
      for (int i = 0; i < 1_000_000; i++) {
        String value = String.valueOf(i);
        values.add(value);
        ids.add(myEnumerator.enumerate(value));
      }

      for (int i = 0; i < values.size(); i += 50_000) {
        String value = values.get(i);
        System.out.println("checked " + i);
        assertEquals(ids.getInt(i), myEnumerator.catchCorruption(new CorruptAndEnumerateAfter(value)).intValue());
      }
    }
    finally {
      System.setProperty(PersistentBTreeEnumerator.DO_SELF_HEAL_PROP, Boolean.toString(false));
    }
  }

  // ==================== Flush Operation Tests ====================
  
  @Test
  public void testFlushWithoutChangesSucceeds() throws IOException {
    String value = "testValue";
    myEnumerator.enumerate(value);
    myEnumerator.force();
    
    // Flush again without changes should succeed
    myEnumerator.force();
    assertFalse(myEnumerator.isDirty());
  }
  
  @Test
  public void testMultipleConsecutiveFlushes() throws IOException {
    String value1 = "value1";
    String value2 = "value2";
    
    int id1 = myEnumerator.enumerate(value1);
    myEnumerator.force();
    assertFalse(myEnumerator.isDirty());
    
    int id2 = myEnumerator.enumerate(value2);
    myEnumerator.force();
    assertFalse(myEnumerator.isDirty());
    
    myEnumerator.force(); // Third flush without changes
    assertFalse(myEnumerator.isDirty());
    
    assertEquals(value1, myEnumerator.valueOf(id1));
    assertEquals(value2, myEnumerator.valueOf(id2));
  }
  
  @Test
  public void testFlushEnsuresPersistence() throws IOException {
    String value = "persistentValue";
    int id = myEnumerator.enumerate(value);
    myEnumerator.force();
    
    // Reopen and verify data persisted
    myEnumerator.close();
    myEnumerator = new TestStringEnumerator(myFile);
    
    assertEquals(value, myEnumerator.valueOf(id));
    assertEquals(id, myEnumerator.tryEnumerate(value));
  }
  
  // ==================== Empty Enumerator Tests ====================
  
  @Test
  public void testEmptyEnumeratorValueOfReturnsNull() throws IOException {
    // Fresh enumerator should return null for any ID
    assertNull(myEnumerator.valueOf(1));
    assertNull(myEnumerator.valueOf(100));
    assertNull(myEnumerator.valueOf(Integer.MAX_VALUE));
  }
  
  @Test
  public void testEmptyEnumeratorGetAllReturnsEmpty() throws IOException {
    Collection<String> allObjects = myEnumerator.getAllDataObjects(null);
    assertNotNull(allObjects);
    assertTrue(allObjects.isEmpty());
  }
  
  @Test
  public void testEmptyEnumeratorProcessAllReturnsTrue() throws IOException {
    // processAllDataObject should return true (meaning "continue") when no data
    boolean result = myEnumerator.processAllDataObject(value -> {
      fail("Should not be called for empty enumerator");
      return true;
    }, null);
    assertTrue("Empty enumerator should return true from processAllDataObject", result);
  }
  
  @Test
  public void testEmptyEnumeratorTryEnumerateReturnsNull() throws IOException {
    assertEquals(DataEnumerator.NULL_ID, myEnumerator.tryEnumerate("nonExistent"));
    assertEquals(DataEnumerator.NULL_ID, myEnumerator.tryEnumerate(""));
    assertEquals(DataEnumerator.NULL_ID, myEnumerator.tryEnumerate("anything"));
  }
  
  @Test
  public void testEmptyEnumeratorPersistedCorrectly() throws IOException {
    myEnumerator.force();
    myEnumerator.close();
    myEnumerator = new TestStringEnumerator(myFile);
    
    // Should still be empty after reopen
    assertTrue(myEnumerator.getAllDataObjects(null).isEmpty());
    assertFalse(myEnumerator.isDirty());
  }
  
  // ==================== Single Element Tests ====================
  
  @Test
  public void testSingleElementEnumerator() throws IOException {
    String value = "singleValue";
    int id = myEnumerator.enumerate(value);
    
    assertEquals(value, myEnumerator.valueOf(id));
    assertEquals(id, myEnumerator.tryEnumerate(value));
    assertEquals(DataEnumerator.NULL_ID, myEnumerator.tryEnumerate("other"));
    
    Collection<String> all = myEnumerator.getAllDataObjects(null);
    assertEquals(1, all.size());
    assertTrue(all.contains(value));
  }
  
  @Test
  public void testSingleElementPersistedAfterReopen() throws IOException {
    String value = "persistentSingle";
    int id = myEnumerator.enumerate(value);
    myEnumerator.force();
    
    myEnumerator.close();
    myEnumerator = new TestStringEnumerator(myFile);
    
    assertEquals(value, myEnumerator.valueOf(id));
    assertEquals(id, myEnumerator.tryEnumerate(value));
    
    Collection<String> all = myEnumerator.getAllDataObjects(null);
    assertEquals(1, all.size());
    assertTrue(all.contains(value));
  }
  
  @Test
  public void testSingleElementReenumerationReturnsSameId() throws IOException {
    String value = "reenumedValue";
    int id1 = myEnumerator.enumerate(value);
    int id2 = myEnumerator.enumerate(value);
    int id3 = myEnumerator.enumerate(value);
    
    assertEquals(id1, id2);
    assertEquals(id1, id3);
    
    Collection<String> all = myEnumerator.getAllDataObjects(null);
    assertEquals(1, all.size());
  }
  
  // ==================== Sequential Operations Tests ====================
  
  @Test
  public void testSequentialEnumerationMaintainsConsistency() throws IOException {
    List<String> values = new ArrayList<>();
    List<Integer> ids = new ArrayList<>();
    
    // Enumerate 1000 sequential values
    for (int i = 0; i < 1000; i++) {
      String value = "seq_" + i;
      values.add(value);
      ids.add(myEnumerator.enumerate(value));
    }
    
    // Verify all can be retrieved
    for (int i = 0; i < 1000; i++) {
      assertEquals(values.get(i), myEnumerator.valueOf(ids.get(i)));
      assertEquals((int)ids.get(i), myEnumerator.tryEnumerate(values.get(i)));
    }
    
    // Verify all are in getAllDataObjects
    Collection<String> all = myEnumerator.getAllDataObjects(null);
    assertEquals(1000, all.size());
    assertTrue(all.containsAll(values));
  }
  
  @Test
  public void testInterleavedEnumerateAndRetrieve() throws IOException {
    Map<String, Integer> valueToId = new HashMap<>();
    
    for (int i = 0; i < 100; i++) {
      String value = "interleaved_" + i;
      int id = myEnumerator.enumerate(value);
      valueToId.put(value, id);
      
      // Immediately verify
      assertEquals(value, myEnumerator.valueOf(id));
      assertEquals(id, myEnumerator.tryEnumerate(value));
      
      // Verify all previous values still work
      for (Map.Entry<String, Integer> entry : valueToId.entrySet()) {
        assertEquals(entry.getKey(), myEnumerator.valueOf(entry.getValue()));
      }
    }
  }
  
  @Test
  public void testBoundaryValueStrings() throws IOException {
    String empty = "";
    String nullChar = "\u0000";
    String longString = StringUtil.repeat("x", 10000);
    String unicodeString = "\u0001\u0002\uffff\ud800\udc00"; // Including surrogate pair
    
    int id1 = myEnumerator.enumerate(empty);
    int id2 = myEnumerator.enumerate(nullChar);
    int id3 = myEnumerator.enumerate(longString);
    int id4 = myEnumerator.enumerate(unicodeString);
    
    // All IDs should be unique
    assertNotEquals(id1, id2);
    assertNotEquals(id1, id3);
    assertNotEquals(id1, id4);
    assertNotEquals(id2, id3);
    assertNotEquals(id2, id4);
    assertNotEquals(id3, id4);
    
    // All should be retrievable
    assertEquals(empty, myEnumerator.valueOf(id1));
    assertEquals(nullChar, myEnumerator.valueOf(id2));
    assertEquals(longString, myEnumerator.valueOf(id3));
    assertEquals(unicodeString, myEnumerator.valueOf(id4));
    
    // Persist and verify
    myEnumerator.force();
    myEnumerator.close();
    myEnumerator = new TestStringEnumerator(myFile);
    
    assertEquals(empty, myEnumerator.valueOf(id1));
    assertEquals(nullChar, myEnumerator.valueOf(id2));
    assertEquals(longString, myEnumerator.valueOf(id3));
    assertEquals(unicodeString, myEnumerator.valueOf(id4));
  }
  
  @Test
  public void testProcessAllDataObjectStopsOnFalse() throws IOException {
    // Add several values
    for (int i = 0; i < 10; i++) {
      myEnumerator.enumerate("value_" + i);
    }
    
    final int[] count = {0};
    boolean result = myEnumerator.processAllDataObject(value -> {
      count[0]++;
      return count[0] < 5; // Stop after processing 5 values
    }, null);
    
    assertFalse("Should return false when processor returns false", result);
    assertEquals(5, count[0]);
  }
  
  @Test
  public void testProcessAllDataObjectContinuesOnTrue() throws IOException {
    // Add several values
    int totalValues = 10;
    for (int i = 0; i < totalValues; i++) {
      myEnumerator.enumerate("value_" + i);
    }
    
    final int[] count = {0};
    boolean result = myEnumerator.processAllDataObject(value -> {
      count[0]++;
      return true; // Continue for all
    }, null);
    
    assertTrue("Should return true when processor always returns true", result);
    assertEquals(totalValues, count[0]);
  }

  private static final StringBuilder builder = new StringBuilder(100);
  private static final Random random = new Random(13101977);

  static String createRandomString() {
    builder.setLength(0);
    int len = random.nextInt(40) + 10;
    for (int i = 0; i < len; ++i) {
      builder.append((char)(32 + random.nextInt(2 + i >> 1)));
    }
    return builder.toString();
  }

  private class CorruptAndEnumerateAfter implements ThrowableComputable<Integer, IOException> {
    private final AtomicBoolean myIoExceptionThrown = new AtomicBoolean(false);
    private final String myValue;

    private CorruptAndEnumerateAfter(String value) { myValue = value; }

    @Override
    public Integer compute() throws IOException {
      if (!myIoExceptionThrown.get()) {
        myIoExceptionThrown.set(true);
        throw new IOException("Corrupted!!!");
      }

      return myEnumerator.tryEnumerate(myValue);
    }
  }
}