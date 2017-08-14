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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ref.GCUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.*;

public class ConcurrentMapsTest {
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
  public void testConcurrentWeakKeysRemovedWhenIdentityStrategyIsUsed() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakMap(ContainerUtil.identityStrategy());
    checkKeyIsTossedAfterGCPressure(map);
  }

  @Test(timeout = TIMEOUT)
  public void testWeakHashMapWithIdentityStrategy() {
    WeakHashMap<Object, Object> map = new WeakHashMap<>(10,0.5f,ContainerUtil.identityStrategy());
    checkKeyIsTossedAfterGCPressure(map);
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
  public void testConcurrentWeakTossedWeakKeysAreRemoved() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakMap();
    checkKeyIsTossedAfterGCPressure(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftTossedSoftKeysAreRemoved() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentSoftMap();
    checkKeyIsTossedAfterGCPressure(map);
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentWeakTossedWeakValueIsRemoved() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakValueMap();
    checkKeyIsTossedAfterGCPressure(map);
  }

  private void checkKeyIsTossedAfterGCPressure(Map<Object, Object> map) {
    map.put(new Object(), new Object());

    //noinspection SizeReplaceableByIsEmpty
    do {
      map.put(this, this);  // to run processQueues();
      map.remove(this);

      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (map.size() != 0);
    assertEquals(0, map.size());
    map.put(this, this);
    assertEquals(1, map.size());
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftTossedSoftValueIsRemoved() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentSoftValueMap();
    checkKeyIsTossedAfterGCPressure(map);
  }

  @Test(timeout = TIMEOUT)
  public void testSoftMapCustomStrategy() {
    SoftHashMap<String, String> map = new SoftHashMap<>(IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    map.put("ab", "ab");
    assertTrue(map.containsKey("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testWeakMapCustomStrategy() {
    WeakHashMap<String, String> map = new WeakHashMap<>(10, 0.5f, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    String keyL = "ab";
    String keyU = StringUtil.toUpperCase(keyL);
    String value = "asdfab";
    map.put(keyL, value);
    assertTrue(map.containsKey(keyU));
    assertTrue(map.containsKey(keyL));
    String removed = map.remove("aB");
    assertSame(value, removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testWeakNativeHashCodeDoesNotGetCalledWhenCustomStrategyIsSpecified() {
    Map<Object, Object> map = new WeakHashMap<>(ContainerUtil.identityStrategy());

    checkHashCodeDoesntCalledFor(map);
  }

  @Test(timeout = TIMEOUT)
  public void testSoftNativeHashCodeDoesNotGetCalledWhenCustomStrategyIsSpecified() {
    Map<Object, Object> map = new SoftHashMap<>(ContainerUtil.identityStrategy());

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
    assertTrue(map.containsKey(key));
    map.remove(key);
    assertTrue(map.isEmpty());
  }


  @Test(timeout = TIMEOUT)
  public void testConcurrentSoftCustomStrategy() {
    ConcurrentMap<String, String> map = ContainerUtil.createConcurrentSoftMap(10, 0.7f, 16, IGNORE_CASE_WITH_CRAZY_HASH_STRATEGY);

    map.put("ab", "ab");
    assertEquals(1, map.size());
    assertTrue(map.containsKey("AB"));
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
    assertTrue(map.containsKey("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testSoftKeySoftValueTossedSoftKeyAndValue() {
    Map<Object, Object> map = ContainerUtil.createSoftKeySoftValueMap();
    map.put(new Object(), new Object());

    do {
      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
      map.remove(this); // to call processQueue()
    }
    while (!map.isEmpty());
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testWeakKeyWeakValueTossedWeakKeyAndValue() {
    RefKeyRefValueHashMap<Object, Object> map = (RefKeyRefValueHashMap<Object, Object>)ContainerUtil.createWeakKeyWeakValueMap();
    checkTossedKeyAndValue(map);
  }

  private static void checkTossedKeyAndValue(@NotNull RefKeyRefValueHashMap<Object, Object> map) {
    map.put(new Object(), new Object());

    do {
      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueue());
    assertTrue(map.isEmpty());

    Object hardKey = map;
    map.put(hardKey, new Object());

    do {
      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueue());
    assertTrue(map.isEmpty());
    assertSame(map, hardKey);

    Object hardValue = map;
    map.put(new Object(), hardValue);

    do {
      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueue());
    assertTrue(map.isEmpty());
    assertSame(map, hardValue);
  }

  @Test(timeout = TIMEOUT)
  public void testWeakKeySoftValueTossedWeakKeyAndValue() {
    RefKeyRefValueHashMap<Object, Object> map = (RefKeyRefValueHashMap<Object, Object>)ContainerUtil.createWeakKeySoftValueMap();
    checkTossedKeyAndValue(map);
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
    ConcurrentIntObjectMap<Object> map = ContainerUtil.createConcurrentIntObjectMap();
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
  public void testConcurrentWeakKeyAndValueTossed() {
    ConcurrentMap<Object, Object> map = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
    map.put(new Object(), new Object());

    do {
      map.put(this, this);  // to run processQueues();
      map.remove(this);

      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.isEmpty());

    map.put(this, new Object());
    do {
      map.put(this, this);  // to run processQueues();
      map.remove(this);

      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.isEmpty());

    map.put(new Object(), this);
    do {
      map.put(this, this);  // to run processQueues();
      map.remove(this);

      GCUtil.tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.isEmpty());
  }

  @SuppressWarnings("FieldCanBeLocal")
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
}
