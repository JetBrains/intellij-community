/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import gnu.trove.TObjectHashingStrategy;
import junit.framework.TestCase;

import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConcurrentMapsTest extends TestCase {
  public void testKeysRemovedWhenIdentityStrategyIsUsed() {
    ConcurrentWeakHashMap<Object, Object> map = new ConcurrentWeakHashMap<Object, Object>(TObjectHashingStrategy.IDENTITY);
    map.put(new Object(), new Object());

    tryGcSoftlyReachableObjects(); // sometimes weak references are not collected under linux, try to stress gc to force them
    do {
      System.gc();
    }
    while (!map.processQueue());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  public void testRemoveFromSoftEntrySet() {
    ConcurrentSoftHashMap<Object, Object> map = new ConcurrentSoftHashMap<Object, Object>();
    map.put(this, this);
    Set<Map.Entry<Object,Object>> entries = map.entrySet();
    assertEquals(1, entries.size());
    Map.Entry<Object, Object> entry = entries.iterator().next();
    entries.remove(entry);

    assertTrue(map.isEmpty());
  }

  public void testRemoveFromWeakEntrySet() {
    ConcurrentWeakHashMap<Object, Object> map = new ConcurrentWeakHashMap<Object, Object>();
    map.put(this, this);
    Set<Map.Entry<Object,Object>> entries = map.entrySet();
    assertEquals(1, entries.size());
    Map.Entry<Object, Object> entry = entries.iterator().next();
    entries.remove(entry);

    assertTrue(map.isEmpty());
  }

  public void testTossedWeakKeysAreRemoved() {
    ConcurrentWeakHashMap<Object, Object> map = new ConcurrentWeakHashMap<Object, Object>();
    map.put(new Object(), new Object());

    tryGcSoftlyReachableObjects(); // sometimes weak references are not collected under linux, try to stress gc to force them
    do {
      System.gc();
    }
    while (!map.processQueue());
    assertEquals(0, map.underlyingMapSize());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  public static void tryGcSoftlyReachableObjects() {
    SoftReference reference = new SoftReference(new Object());
    List<Object> list = ContainerUtil.newArrayList();
    while (reference.get() != null) {
      list.add(new SoftReference<byte[]>(new byte[(int)Runtime.getRuntime().freeMemory() / 2]));
    }
  }

  public void testTossedSoftKeysAreRemoved() {
    ConcurrentSoftHashMap<Object, Object> map = new ConcurrentSoftHashMap<Object, Object>();
    map.put(new Object(), new Object());

    tryGcSoftlyReachableObjects();
    do {
      System.gc();
    }
    while (!map.processQueue());
    assertEquals(0, map.underlyingMapSize());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  public void testTossedWeakValueIsRemoved() {
    ConcurrentWeakValueHashMap<Object, Object> map = new ConcurrentWeakValueHashMap<Object, Object>();
    map.put(new Object(), new Object());

    tryGcSoftlyReachableObjects(); // sometimes weak references are not collected under linux, try to stress gc to force them
    do {
      System.gc();
    }
    while (!map.processQueue());
    assertEquals(0, map.underlyingMapSize());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }
  public void testTossedSoftValueIsRemoved() {
    ConcurrentSoftValueHashMap<Object, Object> map = new ConcurrentSoftValueHashMap<Object, Object>();
    map.put(new Object(), new Object());

    tryGcSoftlyReachableObjects();
    do {
      System.gc();
    }
    while (!map.processQueue());
    assertEquals(0, map.underlyingMapSize());
    map.put(this, this);
    assertEquals(1, map.underlyingMapSize());
  }

  public void testStrategy() {
    SoftHashMap<String, String> map = new SoftHashMap<String, String>(new TObjectHashingStrategy<String>() {
      @Override
      public int computeHashCode(String object) {
        return Character.toLowerCase(object.charAt(object.length() - 1));
      }

      @Override
      public boolean equals(String o1, String o2) {
        return StringUtil.equalsIgnoreCase(o1, o2);
      }
    });

    map.put("ab", "ab");
    assertTrue(map.containsKey("AB"));
    String removed = map.remove("aB");
    assertEquals("ab", removed);
    assertTrue(map.isEmpty());
  }

  public void testTossedSoftKeyAndValue() {
    SoftKeySoftValueHashMap<Object, Object> map = new SoftKeySoftValueHashMap<Object, Object>();
    map.put(new Object(), new Object());

    tryGcSoftlyReachableObjects();
    do {
      System.gc();
    }
    while (!map.processQueue());
    assertTrue(map.isEmpty());
  }

  public void testTossedWeakKeyAndValue() {
    WeakKeyWeakValueHashMap<Object, Object> map = new WeakKeyWeakValueHashMap<Object, Object>();
    map.put(new Object(), new Object());

    tryGcSoftlyReachableObjects();
    do {
      System.gc();
    }
    while (!map.processQueue());
    assertTrue(map.isEmpty());
  }
}
