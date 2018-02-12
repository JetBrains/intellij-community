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
package com.intellij.util.keyFMap;

import com.intellij.openapi.util.Key;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KeyFMapTest extends TestCase {
  private static final List<Key<Object>> KEYS =
    IntStream.range(0, 20).mapToObj(i -> Key.create("Key#"+i)).collect(Collectors.toList());

  private static KeyFMap createKeyFMap(List<Key<Object>> keys, List<Object> values) {
    KeyFMap map = KeyFMap.EMPTY_MAP;

    for (int i = 0; i < keys.size(); i++) {
      map = map.plus(keys.get(i), values.get(i));
    }

    return map;
  }

  private static void doTestGetKeys(int size) {
    List<Object> values = ContainerUtil.newArrayList();
    for (int i = 0; i < size; i++) {
      values.add("Value#" + i);
    }

    KeyFMap map = createKeyFMap(KEYS.subList(0, size), values);

    Key[] actualKeys = map.getKeys();

    assertEquals(size, actualKeys.length);

    for (Key key : KEYS.subList(0, size)) {
      assertTrue("Key not found: " + key, ArrayUtil.contains(key, actualKeys));
    }
  }

  public void testHashCodeEquals() {
    Random r = new Random(1);
    for(int n=0; n<15; n++) {
      Map<Key<Object>, Object> hashMap = new HashMap<>();
      KeyFMap fMap = KeyFMap.EMPTY_MAP;

      for(int i=0; i<n; i++) {
        Object value = "Value#" + i;
        Key<Object> key;
        while (true) {
          key = KEYS.get(r.nextInt(KEYS.size()));
          if (hashMap.putIfAbsent(key, value) == null) break;
        }
        KeyFMap newFMap = fMap.plus(key, value);
        assertNotSame(fMap, newFMap); // new key is added: must be not same
        fMap = newFMap;
      }
      assertEquals(hashMap + ":" + fMap, hashMap.hashCode(), fMap.hashCode());
      KeyFMap fMap2 = KeyFMap.EMPTY_MAP;
      for (Map.Entry<Key<Object>, Object> entry : hashMap.entrySet()) {
        fMap2 = fMap2.plus(entry.getKey(), entry.getValue());
      }
      assertEquals(fMap, fMap2);
      Iterator<Key<Object>> iterator = hashMap.keySet().iterator();
      while(iterator.hasNext()) {
        Key<Object> key = iterator.next();
        iterator.remove();
        assertEquals(fMap, fMap2);
        fMap = fMap.minus(key);
        assertFalse(fMap.equals(fMap2));
        fMap2 = fMap2.minus(key);
        assertEquals(fMap, fMap2);
        assertEquals(fMap.hashCode(), fMap2.hashCode());
      }
      assertTrue(fMap.isEmpty());
    }
  }

  public void testIdentityHashCode() {
    KeyFMap map = KeyFMap.EMPTY_MAP;
    for(int i=0; i<15; i++) {
      String val1 = "Value#"+i;
      String val2 = "Value#"+i;
      KeyFMap map1 = map.plus(KEYS.get(i), val1);
      KeyFMap map2 = map.plus(KEYS.get(i), val2);
      assertEquals(map1.hashCode(), map2.hashCode());
      assertEquals(map1, map2);
      assertFalse(map1.getValueIdentityHashCode() == map2.getValueIdentityHashCode());
      assertFalse(map1.equalsByReference(map2));
      map2 = map.plus(KEYS.get(i), val1);
      assertTrue(map1.getValueIdentityHashCode() == map2.getValueIdentityHashCode());
      assertTrue(map1.equalsByReference(map2));
      map = map1;
    }
  }

  public void testGetKeysOnEmptyFMap() {
    doTestGetKeys(0);
  }

  public void testGetKeysOnOneElementFMap() {
    doTestGetKeys(1);
  }

  public void testGetKeysOnTwoElementFMap() {
    doTestGetKeys(2);
  }

  public void testGetKeysOnArrayBackedFMap() {
    doTestGetKeys(5);
  }

  public void testGetKeysOnMapBackedFMap() {
    doTestGetKeys(15);
  }
}