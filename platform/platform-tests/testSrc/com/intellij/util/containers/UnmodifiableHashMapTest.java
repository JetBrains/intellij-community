// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import one.util.streamex.IntStreamEx;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class UnmodifiableHashMapTest {
  @Test
  public void testEmpty() {
    UnmodifiableHashMap<Object, Object> empty = UnmodifiableHashMap.empty();
    assertEquals(0, empty.size());
    assertTrue(empty.isEmpty());
    //noinspection ConstantConditions
    assertFalse(empty.containsKey("foo"));
    //noinspection RedundantOperationOnEmptyContainer
    assertNull(empty.get("foo"));
  }

  @SuppressWarnings("deprecation")
  @Test(expected = UnsupportedOperationException.class)
  public void testPut() {
    UnmodifiableHashMap.empty().put("foo", "bar");
  }

  @SuppressWarnings("deprecation")
  @Test(expected = UnsupportedOperationException.class)
  public void testRemove() {
    UnmodifiableHashMap.empty().remove("foo");
  }

  @SuppressWarnings("deprecation")
  @Test(expected = UnsupportedOperationException.class)
  public void testPutAll() {
    //noinspection RedundantCollectionOperation
    UnmodifiableHashMap.empty().putAll(Collections.singletonMap("foo", "bar"));
  }

  @SuppressWarnings("deprecation")
  @Test(expected = UnsupportedOperationException.class)
  public void testClear() {
    UnmodifiableHashMap.empty().clear();
  }

  @Test
  public void testWith() {
    UnmodifiableHashMap<Integer, String> map = UnmodifiableHashMap.empty();
    for (int i = 0; i < 50; i++) {
      String value = String.valueOf(i);
      map = map.with(i, value);
      assertEquals(i + 1, map.size());
      assertEquals(value, map.get(i));
      assertTrue(map.containsKey(i));
      assertTrue(map.containsValue(value));
      assertFalse(map.containsValue(String.valueOf(i + 1)));
      assertNull(map.get(i + 1));

      UnmodifiableHashMap<Integer, String> map1 = map.with(i, value);
      assertSame(map1, map);
      UnmodifiableHashMap<Integer, String> map2 = map.with(i, String.valueOf(i + 1));
      assertNotSame(map2, map);
      assertEquals(map.size(), map2.size());
      assertEquals(map.keySet(), map2.keySet());
      assertTrue(map2.containsValue(String.valueOf(i + 1)));
      assertFalse(map2.containsValue(value));
    }
  }

  @Test
  public void testWithout() {
    int size = 51;
    UnmodifiableHashMap<Integer, String> map = create(size);
    for (int i = 0; i < size; i++) {
      map = map.without(i);
      assertEquals(size - 1 - i, map.size());
      assertFalse(map.containsKey(i));
      assertTrue(i == size - 1 || map.containsKey(i + 1));
    }
    map = create(size);
    for (int i = size; i >= 0; i--) {
      map = map.without(i);
      assertEquals(i, map.size());
      assertFalse(map.containsKey(i));
      assertTrue(i == 0 || map.containsKey(i - 1));
    }
  }

  @Test
  public void testAddCollisions() {
    UnmodifiableHashMap<Long, String> map = UnmodifiableHashMap.empty();
    for (int i = 0; i < 50; i++) {
      long key = ((long)i << 32) | i ^ 135;
      map = map.with(key, String.valueOf(key));
      assertEquals(i + 1, map.size());
      assertEquals(String.valueOf(key), map.get(key));
      assertTrue(map.containsKey(key));
      assertTrue(map.containsValue(String.valueOf(key)));
      assertFalse(map.containsValue(String.valueOf(key + 1)));
      assertNull(map.get(key + 1));
    }
  }

  @Test
  public void testGet() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      assertNull(map.get(null));
    }
  }

  @Test
  public void testIterate() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      Set<Integer> keys = new java.util.HashSet<>(map.keySet());
      assertEquals(IntStreamEx.range(size).boxed().toSet(), keys);
      Set<String> values = new java.util.HashSet<>(map.values());
      assertEquals(IntStreamEx.range(size).mapToObj(String::valueOf).toSet(), values);
      Set<Map.Entry<Integer, String>> entries = new java.util.HashSet<>(map.entrySet());
      assertEquals(IntStreamEx.range(size).mapToEntry(i -> i, String::valueOf).toSet(), entries);
    }
  }

  @SuppressWarnings("RedundantCollectionOperation")
  @Test
  public void testValues() {
    UnmodifiableHashMap<Integer, String> map = create(10);
    assertTrue(map.values().contains("9"));
    assertFalse(map.values().contains("11"));
  }

  @Test
  public void testForEach() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      Set<Integer> keys = new java.util.HashSet<>();
      Set<String> values = new java.util.HashSet<>();
      map.forEach((k, v) -> {
        keys.add(k);
        values.add(v);
      });
      assertEquals(IntStreamEx.range(size).boxed().toSet(), keys);
      assertEquals(IntStreamEx.range(size).mapToObj(String::valueOf).toSet(), values);
    }
  }

  @Test
  public void testToString() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      String actual = map.toString();
      assertTrue(actual.startsWith("{"));
      assertTrue(actual.endsWith("}"));
      String content = actual.substring(1, actual.length() - 1);
      if (size == 0) {
        assertTrue(content.isEmpty());
        continue;
      }
      Set<String> parts = Set.of(content.split(", ", -1));
      assertEquals(size, parts.size());
      assertEquals(IntStreamEx.range(size).mapToObj(i -> i + "=" + i).toSet(), parts);
    }
  }

  @Test
  public void testEquals() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      assertEquals(map, map);
      HashMap<Integer, String> hashMap = new HashMap<>(map);
      assertEquals(hashMap, map);
      assertEquals(map, hashMap);
      UnmodifiableHashMap<Integer, String> map1 = map.with(0, "1");
      assertNotEquals(map1, map);
      assertNotEquals(map, map1);
      assertNotEquals(map1, hashMap);
      assertNotEquals(hashMap, map1);
    }
  }

  @Test
  public void testHashCode() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      HashMap<Integer, String> hashMap = new HashMap<>(map);
      assertEquals(hashMap.hashCode(), map.hashCode());
    }
  }

  @Test
  public void testFromMap() {
    UnmodifiableHashMap<Integer, String> map = create(10);
    assertSame(map, UnmodifiableHashMap.fromMap(map));
  }

  @Test
  public void testWithAll() {
    UnmodifiableHashMap<Integer, String> map = UnmodifiableHashMap.<Integer, String>empty()
      .with(1, "One").with(2, "Two").with(3, "Three");
    UnmodifiableHashMap<Integer, String> map2 = map.withAll(Collections.emptyMap());
    assertSame(map, map2);
    map2 = map.withAll(Collections.singletonMap(4, "Four"));
    assertEquals(4, map2.size());
    assertEquals("Four", map2.get(4));
    assertTrue(map2.entrySet().containsAll(map.entrySet()));

    map2 = map.withAll(UnmodifiableHashMap.<Integer, String>empty().with(0, "Zero").with(4, "Four"));
    assertEquals(5, map2.size());
    assertEquals("Four", map2.get(4));
    assertEquals("Zero", map2.get(0));
    assertTrue(map2.entrySet().containsAll(map.entrySet()));
  }

  private static UnmodifiableHashMap<Integer, String> create(int size) {
    UnmodifiableHashMap<Integer, String> map =
      IntStreamEx.range(size < 4 ? size :size / 4 * 4).mapToEntry(i -> i, String::valueOf).toMapAndThen(UnmodifiableHashMap::fromMap);
    while (map.size() < size) {
      map = map.with(map.size(), String.valueOf(map.size()));
    }
    return map;
  }
}
