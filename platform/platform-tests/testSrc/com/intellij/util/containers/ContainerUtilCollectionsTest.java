/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.RunFirst;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ref.GCUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

// tests various ContainerUtil.create* and ContainerUtil.new* collections for being really weak/soft/concurrent
@RunFirst
public class ContainerUtilCollectionsTest extends Assert {
  @Rule
  public TestRule watcher = TestLoggerFactory.createTestWatcher();

  private static final long TIMEOUT = 5 * 60 * 1000;  // 5 minutes

  private static final TObjectHashingStrategy<String> IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY = new TObjectHashingStrategy<String>() {
    @Override
    public int computeHashCode(String object) {
      return Character.toLowerCase(object.charAt(object.length() - 1));
    }

    @Override
    public boolean equals(String o1, String o2) {
      return StringUtil.equalsIgnoreCase(o1, o2);
    }
  };

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakMapTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakMap(ContainerUtil.identityStrategy());
    checkKeyTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftMapTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentSoftMap(10, 0.5f, 8, ContainerUtil.identityStrategy());
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
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakKeyWeakValueMap(ContainerUtil.identityStrategy());
    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftKeySoftValueTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentSoftKeySoftValueMap(10, 0.5f, 8, ContainerUtil.identityStrategy());
    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakKeySoftValueTossedEvenWithIdentityStrategy() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakKeySoftValueMap(10, 0.5f, 8, ContainerUtil.identityStrategy());
    checkKeyTossedEventually(map);
    checkValueTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testWeakMapTossedEvenWithIdentityStrategy() {
    Map<Object, Object> map = ContainerUtil.createWeakMap(10,0.5f,ContainerUtil.identityStrategy());
    checkKeyTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testSoftMapTossedEvenWithIdentityStrategy() {
    Map<Object, Object> map = ContainerUtil.createSoftMap(ContainerUtil.identityStrategy());
    checkKeyTossedEventually(map);
  }

  @Test(timeout = TIMEOUT)
  public void testRemoveFromSoftEntrySet() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentSoftMap();
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
    checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(ContainerUtil.createConcurrentSoftMap());
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
    checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(ContainerUtil.createConcurrentSoftKeySoftValueMap(1,1,1,ContainerUtil.canonicalStrategy()));
  }

  private void checkMapDoesntLeakOldValueAfterPutWithTheSameKeyButDifferentValue(Map<Object, Object> map) {
    Object key = new Object();
    class MyValue {}
    map.put(key, strong = new MyValue());
    map.put(key, this);
    strong = null;
    LeakHunter.checkLeak(map, MyValue.class);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftMapTossed() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentSoftMap();
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

      GCUtil.tryGcSoftlyReachableObjects();
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

      GCUtil.tryGcSoftlyReachableObjects();
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

      GCUtil.tryGcSoftlyReachableObjects();
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
    Map<String, String> map = ContainerUtil.createSoftMap(IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    map.put("ab", "ab");
    assertEquals("ab", map.get("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testWeakMapCustomStrategy() {
    Map<String, String> map = ContainerUtil.createWeakMap(10, 0.5f, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

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
    Map<Object, Object> map = ContainerUtil.createWeakMap(10,0.5f,ContainerUtil.identityStrategy());

    checkHashCodeDoesntCalledFor(map);
  }

  @Test(timeout = TIMEOUT)
  public void testSoftNativeHashCodeDoesNotGetCalledWhenCustomStrategyIsSpecified() {
    Map<Object, Object> map = ContainerUtil.createSoftMap(ContainerUtil.identityStrategy());

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
    ConcurrentMap<String, String> map = ContainerUtil.createConcurrentSoftMap(10, 0.7f, 16, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    map.put("ab", "ab");
    assertEquals(1, map.size());
    assertSame("ab",map.get("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test
  public void testConcurrentSoftNullKey() {
    Map<String, String> map = ContainerUtil.createConcurrentSoftMap();

    tryToInsertNullKeys(map);
  }
  @Test
  public void testConcurrentWeakNullKey() {
    Map<String, String> map = ContainerUtil.createConcurrentWeakMap();

    tryToInsertNullKeys(map);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConcurrentWeakSoftNullKey() {
    Map<String, String> map = ContainerUtil.createConcurrentWeakKeySoftValueMap(1, 1, 1, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    tryToInsertNullKeys(map);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConcurrentWeakWeakNullKey() {
    Map<String, String> map = ContainerUtil.createConcurrentWeakKeyWeakValueMap(IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    tryToInsertNullKeys(map);
  }

  private static void tryToInsertNullKeys(Map<String, String> map) {
    map.put(null, "ab");
    assertEquals(1, map.size());
    assertEquals("ab", map.get(null));
    String removed = map.remove(null);
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakSoftCustomStrategy() {
    ConcurrentMap<String, String> map = ContainerUtil.createConcurrentWeakKeySoftValueMap(1, 1, 1, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    map.put("ab", "ab");
    assertEquals(1, map.size());
    assertSame("ab", map.get("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentLongObjectHashMap() {
    ConcurrentLongObjectMap<Object> map = ContainerUtil.createConcurrentLongObjectMap();
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
      assertEquals(map.size(), 1);
    }
    map.clear();
    assertEquals(map.size(), 0);
    assertTrue(map.isEmpty());
  }


  @Test(timeout = TIMEOUT)
  public void testConcurrentIntObjectHashMap() {
    IntObjectMap<Object> map = ContainerUtil.createConcurrentIntObjectMap();
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
      assertEquals(map.size(), 1);
    }
    map.clear();
    assertEquals(map.size(), 0);
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
    strong = new Object();
    map.put("a", strong);
    map.put("b", new Object());

    GCUtil.tryGcSoftlyReachableObjects();
    assertEquals(1, map.size());

    strong = null;
    GCUtil.tryGcSoftlyReachableObjects();
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
  public void testConcurrentIntKeyWeakValuePutIfAbsentMustActuallyPutNewValueIfTheOldWasGced() {
    ConcurrentIntObjectMap<Object> map = ContainerUtil.createConcurrentIntObjectWeakValueMap();
    checkPutIfAbsent(map);
  }
  @Test
  public void testConcurrentIntKeySoftValuePutIfAbsentMustActuallyPutNewValueIfTheOldWasGced() {
    ConcurrentIntObjectMap<Object> map = ContainerUtil.createConcurrentIntObjectSoftValueMap();
    checkPutIfAbsent(map);
  }

  private static void checkPutIfAbsent(Map<String, Object> map) {
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
      GCUtil.tryGcSoftlyReachableObjects();
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
      GCUtil.tryGcSoftlyReachableObjects();
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

    ConcurrentMap<Object, Object> map = ConcurrentCollectionFactory.createMap(new TObjectHashingStrategy<Object>() {
      @Override
      public int computeHashCode(Object object) {
        return 0;
      }

      @Override
      public boolean equals(Object o1, Object o2) {
        return o1==o2;
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
    set.add(new Object());

    //noinspection SizeReplaceableByIsEmpty
    do {
      set.add(this);  // to run processQueues();
      assertFalse(set.isEmpty());
      set.remove(this);

      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (set.size() != 0);
    assertTrue(set.isEmpty());
    assertEquals(0, set.size());
    set.add(this);
    assertEquals(1, set.size());
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
    checkEntrySetIterator(ContainerUtil.createConcurrentIntObjectMap());
    checkEntrySetIterator(ContainerUtil.createConcurrentIntObjectSoftValueMap());
    checkEntrySetIterator(ContainerUtil.createConcurrentIntObjectWeakValueMap());
    checkEntrySetIterator(ContainerUtil.createIntKeyWeakValueMap());
  }

  @Test
  public void testEntrySetTossesValue() {
    checkEntrySetIteratorTossesValue(ContainerUtil.createConcurrentIntObjectSoftValueMap());
    checkEntrySetIteratorTossesValue(ContainerUtil.createConcurrentIntObjectWeakValueMap());
    checkEntrySetIteratorTossesValue(ContainerUtil.createIntKeyWeakValueMap());
  }

  private void checkEntrySetIteratorTossesValue(IntObjectMap<Object> map) {
    map.put(1, this);
    map.put(2, this);
    map.put(3, strong = new Object());
    map.put(4, this);

    Iterator<IntObjectMap.Entry<Object>> iterator = map.entrySet().iterator();
    assertTrue(iterator.hasNext());
    strong = null;
    for (int i=0; i<10; i++) {
      if (map.get(3)==null) break;
      GCUtil.tryGcSoftlyReachableObjects();
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
