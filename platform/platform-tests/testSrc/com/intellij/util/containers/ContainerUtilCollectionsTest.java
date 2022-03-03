// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.RunFirst;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.GCWatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

// tests various ContainerUtil.create*, ContainerUtil.new*, CollectionFactory.create*, ConcurrentCollectionFactory.create* collections for being really weak/soft/concurrent
@RunFirst
public class ContainerUtilCollectionsTest extends Assert {
  @Rule
  public TestRule watcher = TestLoggerFactory.createTestWatcher();

  private static final long TIMEOUT = 5 * 60 * 1000;  // 5 minutes

  private static final HashingStrategy<String> IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY = new HashingStrategy<>() {
    @Override
    public int hashCode(String object) {
      return Character.toLowerCase(object.charAt(object.length() - 1));
    }

    @Override
    public boolean equals(String o1, String o2) {
      return StringUtil.equalsIgnoreCase(o1, o2);
    }
  };

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakMapTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = CollectionFactory.createConcurrentWeakIdentityMap();
    checkKeyTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftMapTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = CollectionFactory.createConcurrentSoftMap(10, 0.5f, 8, HashingStrategy.identity());
    checkKeyTossedEventually(map);
  }

  private void checkKeyTossedEventually(Map<Object, Object> map) {
    checkClearsEventuallyAfterGCPressure(map, ()->map.put(new Object(), new Object()));
    checkClearsEventuallyAfterGCPressure(map, ()->map.put(new Object(), this));
  }
  private void checkKeyTossedEventually(ObjectIntMap<Object> map) {
    checkClearsEventuallyAfterGCPressure(map, ()->map.put(new Object(), 0));
  }
  private void checkValueTossedEventually(IntObjectMap<Object> map) {
    checkClearsEventuallyAfterGCPressure(map, ()->map.put(0, new Object()));
  }
  private void checkValueTossedEventually(Map<Object, Object> map) {
    checkClearsEventuallyAfterGCPressure(map, ()->map.put(new Object(), new Object()));
    checkClearsEventuallyAfterGCPressure(map, ()->map.put(this, new Object()));
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakKeyWeakValueTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = CollectionFactory.createConcurrentWeakKeyWeakValueIdentityMap();
    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftKeySoftValueTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = CollectionFactory.createConcurrentSoftKeySoftValueIdentityMap(10, 0.5f, 8);
    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakKeySoftValueTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = CollectionFactory.createConcurrentWeakKeySoftValueIdentityMap(10, 0.5f, 8);
    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testWeakMapTossedEvenWithIdentityStrategy() {
    Map<Object, Object> map = CollectionFactory.createWeakIdentityMap(10, 0.5f);
    checkKeyTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testSoftMapTossedEvenWithIdentityStrategy() {
    Map<Object, Object> map = CollectionFactory.createSoftMap(HashingStrategy.identity());
    checkKeyTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testRemoveFromSoftEntrySet() {
    ConcurrentMap<Object, Object> map = CollectionFactory.createConcurrentSoftMap();
    map.put(this, this);
    Set<Map.Entry<Object, Object>> entries = map.entrySet();
    assertEquals(1, entries.size());
    Map.Entry<Object, Object> entry = entries.iterator().next();
    entries.remove(entry);

    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testRemoveFromWeakEntrySet() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakMap();
    map.put(this, this);
    Set<Map.Entry<Object, Object>> entries = map.entrySet();
    assertEquals(1, entries.size());
    Map.Entry<Object, Object> entry = entries.iterator().next();
    entries.remove(entry);

    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakMapTossed() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakMap();
    checkKeyTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakMapDoesntRetainOldValueKeyAfterPutWithTheSameKeyButDifferentValue() {
    checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(ContainerUtil.createConcurrentWeakMap());
  }
  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftMapDoesntRetainOldValueKeyAfterPutWithTheSameKeyButDifferentValue() {
    checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(CollectionFactory.createConcurrentSoftMap());
  }
  @Test(timeout = TIMEOUT)
  public void testConcurrentWKWVMapDoesntRetainOldValueKeyAfterPutWithTheSameKeyButDifferentValue() {
    checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(ContainerUtil.createConcurrentWeakKeyWeakValueMap());
  }
  @Test(timeout = TIMEOUT)
  public void testConcurrentWKSVMapDoesntRetainOldValueKeyAfterPutWithTheSameKeyButDifferentValue() {
    checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(ContainerUtil.createConcurrentWeakKeySoftValueMap());
  }
  @Test(timeout = TIMEOUT)
  public void testConcurrentSKSVMapDoesntRetainOldValueKeyAfterPutWithTheSameKeyButDifferentValue() {
    checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(CollectionFactory.createConcurrentSoftKeySoftValueMap(1, 1, 1));
  }

  @SuppressWarnings("OverwrittenKey")
  private void checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(Map<Object, Object> map) {
    Object key = new Object();
    class MyValue_ {}
    map.put(key, strong = new MyValue_());
    map.put(key, this);
    strong = null;
    LeakHunter.checkLeak(map, MyValue_.class);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftMapTossed() {
    ConcurrentMap<Object, Object> map = CollectionFactory.createConcurrentSoftMap();
    checkKeyTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakValueMapTossed() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakValueMap();
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftValueMapTossed() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentSoftValueMap();
    checkValueTossedEventually(map);
  }

  private void checkClearsEventuallyAfterGCPressure(Map<Object, Object> map, @NotNull Runnable putKey) {
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());
    putKey.run();

    Object strong = new Object();
    //noinspection SizeReplaceableByIsEmpty
    do {
      map.put(strong, strong);  // to run processQueues();
      assertFalse(map.isEmpty());
      map.remove(strong);
      assertNull(map.get(strong));

      GCUtil.tryGcSoftlyReachableObjects(() -> map.isEmpty());
    }
    while (map.size() != 0);
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());
    map.put(this, this);
    assertEquals(1, map.size());
    map.clear();
    assertEquals(0, map.size());
    assertNull(map.get(strong));
  }

  private static final int RANDOM_INT = 987654321;
  private void checkClearsEventuallyAfterGCPressure(ObjectIntMap<Object> map, @NotNull Runnable put) {
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());
    put.run();
    strong = new Object();
    //noinspection SizeReplaceableByIsEmpty
    do {
      map.put(strong, RANDOM_INT);  // to run processQueues();
      assertFalse(map.isEmpty());
      map.remove(strong);
      assertEquals(0, map.get(strong));

      GCUtil.tryGcSoftlyReachableObjects(() -> map.isEmpty());
    }
    while (map.size() != 0);
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());
    map.put(this, RANDOM_INT);
    assertEquals(1, map.size());
    map.clear();
    assertEquals(0, map.size());
    assertEquals(0, map.get(strong));
  }

  private void checkClearsEventuallyAfterGCPressure(IntObjectMap<Object> map, @NotNull Runnable put) {
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());
    put.run();

    strong = new Object();
    //noinspection SizeReplaceableByIsEmpty
    do {
      map.put(RANDOM_INT, strong);  // to run processQueues();
      assertFalse(map.isEmpty());
      map.remove(RANDOM_INT);
      assertNull(map.get(RANDOM_INT));

      GCUtil.tryGcSoftlyReachableObjects(() -> map.isEmpty());
    }
    while (map.size() != 0);
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());
    map.put(RANDOM_INT, this);
    assertEquals(1, map.size());
    map.clear();
    assertEquals(0, map.size());
    assertNull(map.get(RANDOM_INT));
  }

  @Test(timeout = TIMEOUT)
  public void testSoftMapCustomStrategy() {
    Map<String, String> map = CollectionFactory.createSoftMap(IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    map.put("ab", "ab");
    assertEquals("ab", map.get("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testWeakMapCustomStrategy() {
    Map<String, String> map = CollectionFactory.createWeakMap(10, 0.5f, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    String keyL = "ab";
    String keyU = StringUtil.toUpperCase(keyL);
    String value = "asdfab";
    map.put(keyL, value);
    assertSame(value, map.get(keyU));
    assertSame(value, map.get(keyL));
    String removed = map.remove("aB");
    assertSame(value, removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testWeakNativeHashCodeDoesNotGetCalledWhenCustomStrategyIsSpecified() {
    Map<Object, Object> map = CollectionFactory.createWeakIdentityMap(10, 0.5f);

    checkHashCodeDoesntCalledFor(map);
  }

  @Test(timeout = TIMEOUT)
  public void testSoftNativeHashCodeDoesNotGetCalledWhenCustomStrategyIsSpecified() {
    Map<Object, Object> map = CollectionFactory.createSoftMap(HashingStrategy.identity());

    checkHashCodeDoesntCalledFor(map);
  }

  private void checkHashCodeDoesntCalledFor(Map<Object, Object> map) {
    Object key = new Object(){
      @Override
      public int hashCode() {
        fail("must not be called");
        return super.hashCode();
      }
    };
    map.put(key, "ab");
    assertSame("ab", map.get(key));
    map.remove(key);
    assertTrue(map.isEmpty());
  }


  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftCustomStrategy() {
    ConcurrentMap<String, String> map = CollectionFactory.createConcurrentSoftMap(10, 0.7f, 16, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    map.put("ab", "ab");
    assertEquals(1, map.size());
    assertSame("ab",map.get("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test
  public void testConcurrentSoftMapMustNotAcceptNullKeyOrValue() {
    Map<String, String> map = CollectionFactory.createConcurrentSoftMap();

    assertNullKeysMustThrow(map);
    assertNullValuesMustThrow(map);
  }
  @Test
  public void testConcurrentWeakMapMustNotAcceptNullKeyOrValue() {
    Map<String, String> map = ContainerUtil.createConcurrentWeakMap();

    assertNullKeysMustThrow(map);
    assertNullValuesMustThrow(map);
  }

  @Test
  public void testConcurrentWeakKeySoftValueMapMustNotAcceptNullKeyOrValue() {
    Map<String, String> map = CollectionFactory.createConcurrentWeakKeySoftValueMap(1, 1, 1, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    assertNullKeysMustThrow(map);
    assertNullValuesMustThrow(map);
  }

  @Test
  public void testConcurrentWeakKeyWeakValueMustNotAcceptNullKeyOrValue() {
    Map<String, String> map = ContainerUtil.createConcurrentWeakKeyWeakValueMap(IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    assertNullKeysMustThrow(map);
    assertNullValuesMustThrow(map);
  }

  @Test
  public void testWeakMapMustNotAcceptNullKey() {
    assertNullKeysMustThrow(ContainerUtil.createWeakMap());
  }
  @Test
  public void testSoftMapMustNotAcceptNullKey() {
    assertNullKeysMustThrow(ContainerUtil.createSoftMap());
  }
  @Test
  public void testWeakKeySoftValueMapMustNotAcceptNullKey() {
    assertNullKeysMustThrow(ContainerUtil.createWeakKeySoftValueMap());
  }
  @Test
  public void testWeakKeyWeakValueMapMustNotAcceptNullKey() {
    assertNullKeysMustThrow(ContainerUtil.createWeakKeyWeakValueMap());
  }
  @Test
  public void testSoftKeySoftValueMapMustNotAcceptNullKey() {
    assertNullKeysMustThrow(ContainerUtil.createSoftKeySoftValueMap());
  }
  @Test
  public void testSoftValueMapMustNotAcceptNullKey() {
    assertNullKeysMustThrow(ContainerUtil.createSoftValueMap());
  }
  @Test
  public void testWeakValueMapMustNotAcceptNullKey() {
    assertNullKeysMustThrow(ContainerUtil.createWeakValueMap());
  }

  private static void assertNullKeysMustThrow(Map<String, ? super String> map) {
    UsefulTestCase.assertThrows(IllegalArgumentException.class, () -> map.put(null, "ab"));
    assertEquals(0, map.size());
    UsefulTestCase.assertThrows(IllegalArgumentException.class, () -> map.get(null));
    UsefulTestCase.assertThrows(IllegalArgumentException.class, () -> map.remove(null));
    assertTrue(map.isEmpty());
  }
  private static void assertNullValuesMustThrow(Map<? super String, String> map) {
    UsefulTestCase.assertThrows(IllegalArgumentException.class, () -> map.put("ab", null));
    assertEquals(0, map.size());
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakSoftCustomStrategy() {
    ConcurrentMap<String, String> map = CollectionFactory.createConcurrentWeakKeySoftValueMap(1, 1, 1, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    map.put("ab", "ab");
    assertEquals(1, map.size());
    assertSame("ab", map.get("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentLongObjectHashMap() {
    ConcurrentLongObjectMap<Object> map = ConcurrentCollectionFactory.createConcurrentLongObjectMap();
    for (long i = Long.MAX_VALUE-1000; i != Long.MIN_VALUE+1000; i++) {
      Object prev = map.put(i, i);
      assertNull(prev);
      Object ret = map.get(i);
      assertTrue(ret instanceof Long);
      assertEquals(i, ret);

      if (map.size() > 1) {
        Object remove = map.remove(i - 1);
        assertTrue(remove instanceof Long);
        assertEquals(i - 1, remove);
      }
      assertEquals(1, map.size());
    }
    map.clear();
    assertEquals(0, map.size());
    assertTrue(map.isEmpty());
  }


  @Test(timeout = TIMEOUT)
  public void testConcurrentIntObjectHashMap() {
    IntObjectMap<Object> map = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
    for (int i = 0; i < 1000; i++) {
      Object prev = map.put(i, i);
      assertNull(prev);
      Object ret = map.get(i);
      assertTrue(ret instanceof Integer);
      assertEquals(i, ret);

      if (i != 0) {
        Object remove = map.remove(i - 1);
        assertTrue(remove instanceof Integer);
        assertEquals(i - 1, remove);
      }
      assertEquals(1, map.size());
    }
    map.clear();
    assertEquals(0, map.size());
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakKeyWeakValueMapTossed() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakKeyWeakValueMap();

    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testSoftKeySoftValueMapTossed() {
    Map<Object, Object> map = ContainerUtil.createSoftKeySoftValueMap();
    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testWeakKeySoftValueMapTossed() {
    Map<Object, Object> map = ContainerUtil.createWeakKeySoftValueMap();
    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  private volatile Object strong;

  @Test
  public void testConcurrentWeakValueSize() {
    Map<String, Object> map = ContainerUtil.createConcurrentWeakValueMap();
    Ref<Object> ref1 = Ref.create(new Object());
    Ref<Object> ref2 = Ref.create(new Object());

    map.put("a", ref1.get());
    map.put("b", ref2.get());

    GCWatcher.fromClearedRef(ref2).ensureCollected();
    assertEquals(1, map.size());

    GCWatcher.fromClearedRef(ref1).ensureCollected();
    assertTrue(map.toString(), map.isEmpty());
  }

  @Test
  public void testConcurrentWeakValuePutIfAbsentMustActuallyPutNewValueIfTheOldWasGced() {
    Map<String, Object> map = ContainerUtil.createConcurrentWeakValueMap();
    checkPutIfAbsent(map);
  }
  @Test
  public void testConcurrentSoftValuePutIfAbsentMustActuallyPutNewValueIfTheOldWasGced() {
    Map<String, Object> map = ContainerUtil.createConcurrentSoftValueMap();
    checkPutIfAbsent(map);
  }

  @Test
  public void testConcurrentIntKeySoftValuePutIfAbsentMustActuallyPutNewValueIfTheOldWasGced() {
    ConcurrentIntObjectMap<Object> map = ConcurrentCollectionFactory.createConcurrentIntObjectSoftValueMap();
    checkPutIfAbsent(map);
  }

  @Test
  public void testConcurrentIntKeyWeakValuePutIfAbsentMustActuallyPutNewValueIfTheOldWasGced() {
    ConcurrentIntObjectMap<Object> map = ConcurrentCollectionFactory.createConcurrentIntObjectWeakValueMap();
    checkPutIfAbsent(map);
  }

  private static void checkPutIfAbsent(Map<? super String, Object> map) {
    String key = "a";
    map.put(key, new Object());
    String newVal = "xxx";
    int i;
    int N = 1_000_000;
    for (i = 0; i < N; i++) {
      Object prev = map.putIfAbsent(key, newVal);
      if (prev == null) {
        assertSame(newVal, map.get(key));
        break;
      }
      assertEquals(Object.class, prev.getClass());
      Object actual = map.get(key);
      assertNotNull(actual);
      if (actual == newVal) {
        break; // gced, replaced
      }
      assertEquals(Object.class, actual.getClass()); // still not gced, put failed. repeat
    }
    if (i == N) {
      GCUtil.tryGcSoftlyReachableObjects(() -> map.get(key)==null);
      Object prev = map.putIfAbsent(key, newVal);
      assertNull(prev);
      assertSame(newVal, map.get(key));
    }
  }
  private static void checkPutIfAbsent(ConcurrentIntObjectMap<Object> map) {
    int key = 4;
    map.put(key, new Object());
    String newVal = "xxx";
    int i;
    int N = 1_000_000;
    for (i = 0; i < N; i++) {
      Object prev = map.putIfAbsent(key, newVal);
      if (prev == null) {
        assertSame(newVal, map.get(key));
        break;
      }
      assertEquals(Object.class, prev.getClass());
      Object actual = map.get(key);
      assertNotNull(actual);
      if (actual == newVal) {
        break; // gced, replaced
      }
      assertEquals(Object.class, actual.getClass()); // still not gced, put failed. repeat
    }
    if (i == N) {
      GCUtil.tryGcSoftlyReachableObjects(() -> map.get(key)==null);
      Object prev = map.putIfAbsent(key, newVal);
      assertNull(prev);
      assertSame(newVal, map.get(key));
    }
  }

  @Test
  public void testConcurrentHashMapTreeBinifiesItself() {
    class AwfulHashCode {
      @Override
      public int hashCode() {
        return 0;
      }
    }

    ConcurrentMap<Object, Object> map = ConcurrentCollectionFactory.createConcurrentMap(new HashingStrategy<>() {
      @Override
      public int hashCode(Object object) {
        return 0;
      }

      @Override
      public boolean equals(Object o1, Object o2) {
        return o1 == o2;
      }
    });
    int N = 1000;
    for (int i = 0; i < N; i++) {
      map.put(new AwfulHashCode(), 0);
    }
    assertEquals(N, map.size());
  }


  @Test
  public void weakSetTossed() {
    Set<Object> set = ContainerUtil.createWeakSet();
    checkClearsEventuallyAfterGCPressure(set);
  }

  private void checkClearsEventuallyAfterGCPressure(Set<Object> set) {
    assertTrue(set.isEmpty());
    Ref<Object> ref = Ref.create(new Object());
    set.add(ref.get());

    GCWatcher.fromClearedRef(ref).ensureCollected();

    set.add(this);  // to run processQueues();
    assertFalse(set.isEmpty());
    set.remove(this);

    assertTrue(set.isEmpty());
    //noinspection ConstantConditions
    assertEquals(0, set.size());
    set.add(this);
    assertEquals(1, set.size());
  }

  @Test(timeout = TIMEOUT)
  public void testWeakKeyMapsKeySetIsIterable() {
    checkKeySetIterable(ContainerUtil.createWeakMap());
    checkKeySetIterable(ContainerUtil.createWeakKeySoftValueMap());
    checkKeySetIterable(ContainerUtil.createWeakKeyWeakValueMap());
    checkKeySetIterable(ContainerUtil.createConcurrentWeakMap());
  }

  private static void checkKeySetIterable(@NotNull Map<Object, Object> map) {
    for (int i=0; i<10; i++) {
      for (int k=0;k<i;k++) {
        map.put(new Object(), new Object());
      }
      checkKeySetIterable(map.keySet());
    }
  }

  private static void checkKeySetIterable(@NotNull Set<Object> set) {
    boolean found;
    do {
      found = false;
      for (Object o : set) {
        found = true;
        assertNotNull(o);
      }
      GCUtil.tryGcSoftlyReachableObjects(()->set.isEmpty());
    } while (found);
  }

  @Test(timeout = TIMEOUT)
  public void testWeakKeyIntValueMapTossed() {
    ObjectIntMap<Object> map = ContainerUtil.createWeakKeyIntValueMap();
    checkKeyTossedEventually(map);
  }
  @Test(timeout = TIMEOUT)
  public void testIntKeyWeakValueMapTossed() {
    IntObjectMap<Object> map = ContainerUtil.createIntKeyWeakValueMap();
    checkValueTossedEventually(map);
  }

  @Test
  public void testEntrySet() {
    checkEntrySetIterator(ConcurrentCollectionFactory.createConcurrentIntObjectMap());
    checkEntrySetIterator(ConcurrentCollectionFactory.createConcurrentIntObjectSoftValueMap());
    checkEntrySetIterator(ConcurrentCollectionFactory.createConcurrentIntObjectWeakValueMap());
    checkEntrySetIterator(ContainerUtil.createIntKeyWeakValueMap());
  }

  @Test
  public void testEntrySetTossesValue() {
    checkEntrySetIteratorTossesValue(ConcurrentCollectionFactory.createConcurrentIntObjectSoftValueMap());
    checkEntrySetIteratorTossesValue(ConcurrentCollectionFactory.createConcurrentIntObjectWeakValueMap());
    checkEntrySetIteratorTossesValue(ContainerUtil.createIntKeyWeakValueMap());
  }

  private void checkEntrySetIteratorTossesValue(@NotNull IntObjectMap<Object> map) {
    map.put(1, this);
    map.put(2, this);
    map.put(3, strong = new Object());
    map.put(4, this);

    Iterator<IntObjectMap.Entry<Object>> iterator = map.entrySet().iterator();
    assertTrue(iterator.hasNext());
    strong = null;
    for (int i=0; i<10; i++) {
      if (map.get(3)==null) break;
      GCUtil.tryGcSoftlyReachableObjects(() -> map.get(3)==null);
    }
    if (map.get(3) == null) {
      List<Integer> keys = ContainerUtil.map(ContainerUtil.collect(iterator), e -> e.getKey());
      assertFalse(keys.contains(3));
    }
    else {
      // bad luck - iterator has started with 3
      assertEquals(3, iterator.next().getKey());
    }
  }

  private void checkEntrySetIterator(IntObjectMap<Object> map) {
    map.clear();
    int K1 = 1;
    map.put(K1, this);
    int K2 = 2;
    map.put(K2, map);

    assertEquals(2, ContainerUtil.collect(map.entrySet().iterator()).size());
    assertEquals(2, ContainerUtil.collect(map.entrySet().iterator()).size());

    Iterator<IntObjectMap.Entry<Object>> iterator = map.entrySet().iterator();
    assertTrue(iterator.hasNext());
    IntObjectMap.Entry<Object> next = iterator.next();
    int key = next.getKey();
    assertTrue(key==K1 || key==K2);
    iterator.remove();

    assertEquals(1, ContainerUtil.collect(map.entrySet().iterator()).size());
    Iterator<IntObjectMap.Entry<Object>> it2 = map.entrySet().iterator();
    int otherKey = K1 + K2 - key;
    assertEquals(otherKey, it2.next().getKey());
    assertFalse(it2.hasNext());
    try {
      it2.next();
      fail("must throw");
    }
    catch (NoSuchElementException ignored) {
    }

    assertTrue(iterator.hasNext());
    assertEquals(otherKey, iterator.next().getKey());
    iterator.remove();
    UsefulTestCase.assertEmpty(ContainerUtil.collect(map.entrySet().iterator()));
    assertTrue(map.isEmpty());
  }
}
