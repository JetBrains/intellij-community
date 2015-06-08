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
import com.intellij.util.GCUtil;
import gnu.trove.TObjectHashingStrategy;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class ConcurrentMapsTest {
  private static final long TIMEOUT = 5 * 60 * 1000;  // 5 minutes

  private static final TObjectHashingStrategy<String> CUSTOM_STRATEGY = new TObjectHashingStrategy<String>() {
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
  public void testKeysRemovedWhenIdentityStrategyIsUsed() {
    @SuppressWarnings("unchecked") ConcurrentWeakHashMap<Object, Object> map = new ConcurrentWeakHashMap<Object, Object>(TObjectHashingStrategy.IDENTITY);
    map.put(new Object(), new Object());

    do {
      tryGcSoftlyReachableObjects(); // sometimes weak references are not collected under linux, try to stress gc to force them
      System.gc();
    }
    while (!map.processQueue());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  @Test(timeout = TIMEOUT)
  public void testRemoveFromSoftEntrySet() {
    ConcurrentSoftHashMap<Object, Object> map = new ConcurrentSoftHashMap<Object, Object>();
    map.put(this, this);
    Set<Map.Entry<Object, Object>> entries = map.entrySet();
    assertEquals(1, entries.size());
    Map.Entry<Object, Object> entry = entries.iterator().next();
    entries.remove(entry);

    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testRemoveFromWeakEntrySet() {
    ConcurrentWeakHashMap<Object, Object> map = new ConcurrentWeakHashMap<Object, Object>();
    map.put(this, this);
    Set<Map.Entry<Object, Object>> entries = map.entrySet();
    assertEquals(1, entries.size());
    Map.Entry<Object, Object> entry = entries.iterator().next();
    entries.remove(entry);

    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testTossedWeakKeysAreRemoved() {
    ConcurrentWeakHashMap<Object, Object> map = new ConcurrentWeakHashMap<Object, Object>();
    map.put(new Object(), new Object());

    do {
      tryGcSoftlyReachableObjects(); // sometimes weak references are not collected under linux, try to stress gc to force them
      System.gc();
    }
    while (!map.processQueue());
    assertEquals(0, map.underlyingMapSize());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  public static void tryGcSoftlyReachableObjects() {
    GCUtil.tryGcSoftlyReachableObjects();
  }

  @Test(timeout = TIMEOUT)
  public void testTossedSoftKeysAreRemoved() {
    ConcurrentSoftHashMap<Object, Object> map = new ConcurrentSoftHashMap<Object, Object>();
    map.put(new Object(), new Object());

    do {
      tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueue());
    assertEquals(0, map.underlyingMapSize());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  @Test(timeout = TIMEOUT)
  public void testTossedWeakValueIsRemoved() {
    ConcurrentWeakValueHashMap<Object, Object> map =
      (ConcurrentWeakValueHashMap<Object, Object>)ContainerUtil.createConcurrentWeakValueMap();
    map.put(new Object(), new Object());

    do {
      tryGcSoftlyReachableObjects(); // sometimes weak references are not collected under linux, try to stress gc to force them
      System.gc();
    }
    while (!map.processQueue());
    assertEquals(0, map.underlyingMapSize());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  @Test(timeout = TIMEOUT)
  public void testTossedSoftValueIsRemoved() {
    ConcurrentSoftValueHashMap<Object, Object> map = new ConcurrentSoftValueHashMap<Object, Object>();
    map.put(new Object(), new Object());

    do {
      tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueue());
    assertEquals(0, map.underlyingMapSize());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  @Test(timeout = TIMEOUT)
  public void testCustomStrategy() {
    SoftHashMap<String, String> map = new SoftHashMap<String, String>(CUSTOM_STRATEGY);

    map.put("ab", "ab");
    assertTrue(map.containsKey("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testCustomStrategyForConcurrentSoft() {
    ConcurrentSoftHashMap<String, String> map = new ConcurrentSoftHashMap<String, String>(CUSTOM_STRATEGY);

    map.put("ab", "ab");
    assertEquals(1, map.size());
    assertTrue(map.containsKey("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test
  public void testNullKeyForConcurrentSoft() {
    Map<String, String> map = new ConcurrentSoftHashMap<String, String>();

    checkNullKeys(map);
  }
  @Test
  public void testNullKeyForConcurrentWeak() {
    Map<String, String> map = new ConcurrentWeakHashMap<String, String>();

    checkNullKeys(map);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullKeyForConcurrentWeakSoft() {
    Map<String, String> map = new ConcurrentWeakKeySoftValueHashMap<String, String>(1, 1, 1, CUSTOM_STRATEGY);

    checkNullKeys(map);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNullKeyForConcurrentWeakWeak() {
    Map<String, String> map = new ConcurrentWeakKeyWeakValueHashMap<String, String>(1, 1, 1, CUSTOM_STRATEGY);

    checkNullKeys(map);
  }

  private static void checkNullKeys(Map<String, String> map) {
    map.put(null, "ab");
    assertEquals(1, map.size());
    assertEquals("ab", map.get(null));
    String removed = map.remove(null);
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testCustomStrategyForConcurrentWeakSoft() {
    ConcurrentWeakKeySoftValueHashMap<String, String> map = new ConcurrentWeakKeySoftValueHashMap<String, String>(1, 1, 1, CUSTOM_STRATEGY);

    map.put("ab", "ab");
    assertEquals(1, map.size());
    assertTrue(map.containsKey("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testTossedSoftKeyAndValue() {
    SoftKeySoftValueHashMap<Object, Object> map = new SoftKeySoftValueHashMap<Object, Object>();
    map.put(new Object(), new Object());

    do {
      tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueue());
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testTossedWeakKeyAndValue() {
    WeakKeyWeakValueHashMap<Object, Object> map = new WeakKeyWeakValueHashMap<Object, Object>();
    map.put(new Object(), new Object());

    do {
      tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueue());
    assertTrue(map.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void testConcurrentLongObjectHashMap() {
    ConcurrentLongObjectMap<Object> map = new ConcurrentLongObjectHashMap<Object>();
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
  public void testStripedLockIntObjectConcurrentHashMap() {
    ConcurrentIntObjectMap<Object> map = new StripedLockIntObjectConcurrentHashMap<Object>();
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
  public void testConcurrentIntObjectHashMap() {
    ConcurrentIntObjectMap<Object> map = new ConcurrentIntObjectHashMap<Object>();
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
  public void testTossedConcurrentWeakKeyAndValue() {
    ConcurrentWeakKeyWeakValueHashMap<Object, Object> map =
      (ConcurrentWeakKeyWeakValueHashMap<Object, Object>)ContainerUtil.createConcurrentWeakKeyWeakValueMap();
    map.put(new Object(), new Object());

    do {
      tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueues());
    assertTrue(map.isEmpty());

    map.put(this, new Object());
    do {
      tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueues());
    assertTrue(map.isEmpty());

    map.put(new Object(), this);
    do {
      tryGcSoftlyReachableObjects();
      System.gc();
    }
    while (!map.processQueues());
    assertTrue(map.isEmpty());
  }
}
