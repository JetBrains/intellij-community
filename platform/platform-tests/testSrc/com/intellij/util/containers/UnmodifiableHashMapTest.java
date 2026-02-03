// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import one.util.streamex.IntStreamEx;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnmodifiableHashMapTest {
  @Test
  public void testEmpty() {
    UnmodifiableHashMap<Object, Object> empty = UnmodifiableHashMap.empty();
    assertThat(empty).hasSize(0);
    assertThat(empty).isEmpty();
    assertThat(empty).doesNotContainKey("foo");
    //noinspection RedundantOperationOnEmptyContainer
    assertThat(empty.get("foo")).isNull();

    Map<String, String> map = Map.of("k", "v");
    assertThat(empty.withAll(map)).isEqualTo(map);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testPut() {
    assertThatThrownBy(() -> UnmodifiableHashMap.empty().put("foo", "bar")).isInstanceOf(UnsupportedOperationException.class);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testRemove() {
    assertThatThrownBy(() -> UnmodifiableHashMap.empty().remove("foo")).isInstanceOf(UnsupportedOperationException.class);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testPutAll() {
    //noinspection RedundantCollectionOperation
    assertThatThrownBy(() -> UnmodifiableHashMap.empty().putAll(Collections.singletonMap("foo", "bar")))
      .isInstanceOf(UnsupportedOperationException.class);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testClear() {
    assertThatThrownBy(() -> UnmodifiableHashMap.empty().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void testWith() {
    UnmodifiableHashMap<Integer, String> map = UnmodifiableHashMap.empty();
    for (int i = 0; i < 50; i++) {
      String value = String.valueOf(i);
      map = map.with(i, value);
      assertThat(map.size()).isEqualTo(i + 1);
      assertThat(map.get(i)).isEqualTo(value);
      assertThat(map).containsKey(i);
      assertThat(map).containsValue(value);
      assertThat(map).doesNotContainValue(String.valueOf(i + 1));
      assertThat(map.get(i + 1)).isNull();

      UnmodifiableHashMap<Integer, String> map1 = map.with(i, value);
      assertThat(map).isSameAs(map1);
      UnmodifiableHashMap<Integer, String> map2 = map.with(i, String.valueOf(i + 1));
      assertThat(map).isNotSameAs(map2);
      assertThat(map2).hasSameSizeAs(map);
      assertThat(map2.keySet()).isEqualTo(map.keySet());
      assertThat(map2).containsValue(String.valueOf(i + 1));
      assertThat(map2).doesNotContainValue(value);
    }
  }

  @Test
  public void testWithout() {
    int size = 51;
    UnmodifiableHashMap<Integer, String> map = create(size);
    for (int i = 0; i < size; i++) {
      map = map.without(i);
      assertThat(map).hasSize(size - 1 - i);
      assertThat(map).doesNotContainKey(i);
      assertThat(i == size - 1 || map.containsKey(i + 1)).isTrue();
    }
    map = create(size);
    for (int i = size; i >= 0; i--) {
      map = map.without(i);
      assertThat(map).hasSize(i);
      assertThat(map).doesNotContainKey(i);
      assertThat(i == 0 || map.containsKey(i - 1)).isTrue();
    }
  }

  @Test
  public void testAddCollisions() {
    UnmodifiableHashMap<Long, String> map = UnmodifiableHashMap.empty();
    for (int i = 0; i < 50; i++) {
      long key = ((long)i << 32) | i ^ 135;
      map = map.with(key, String.valueOf(key));
      assertThat(map).hasSize(i + 1);
      assertThat(map.get(key)).isEqualTo(String.valueOf(key));
      assertThat(map).containsKey(key);
      assertThat(map).containsValue(String.valueOf(key));
      assertThat(map).doesNotContainValue(String.valueOf(key + 1));
      assertThat(map.get(key + 1)).isNull();
    }
  }

  @Test
  public void testGet() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      assertThat(map.get(null)).isNull();
    }
  }

  @Test
  public void testIterate() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      Set<Integer> keys = new java.util.HashSet<>(map.keySet());
      assertThat(keys).isEqualTo(IntStreamEx.range(size).boxed().toSet());
      Set<String> values = new java.util.HashSet<>(map.values());
      assertThat(values).isEqualTo(IntStreamEx.range(size).mapToObj(String::valueOf).toSet());
      Set<Map.Entry<Integer, String>> entries = new java.util.HashSet<>(map.entrySet());
      assertThat(entries).isEqualTo(IntStreamEx.range(size).mapToEntry(i -> i, String::valueOf).toSet());
    }
  }

  @Test
  public void testValues() {
    UnmodifiableHashMap<Integer, String> map = create(10);
    assertThat(map.values()).contains("9");
    assertThat(map.values()).doesNotContain("11");
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
      assertThat(keys).isEqualTo(IntStreamEx.range(size).boxed().toSet());
      assertThat(values).isEqualTo(IntStreamEx.range(size).mapToObj(String::valueOf).toSet());
    }
  }

  @Test
  public void testToString() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      String actual = map.toString();
      assertThat(actual).startsWith("{");
      assertThat(actual).endsWith("}");
      String content = actual.substring(1, actual.length() - 1);
      if (size == 0) {
        assertThat(content).isEmpty();
        continue;
      }

      Set<String> parts = Set.of(content.split(", ", -1));
      assertThat(parts).hasSize(size);
      assertThat(parts).isEqualTo(IntStreamEx.range(size).mapToObj(i -> i + "=" + i).toSet());
    }
  }

  @Test
  public void testEquals() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      assertThat(map).isEqualTo(map);
      HashMap<Integer, String> hashMap = new HashMap<>(map);
      assertThat(hashMap).isEqualTo(map);
      assertThat(map).isEqualTo(hashMap);
      UnmodifiableHashMap<Integer, String> map1 = map.with(0, "1");
      assertThat(map1.equals(map)).isFalse();
      assertThat(map.equals(map1)).isFalse();
      assertThat(map1.equals(hashMap)).isFalse();
      assertThat(hashMap.equals(map1)).isFalse();
    }
  }

  @Test
  public void testHashCode() {
    for (int size : new int[]{0, 1, 2, 3, 4, 10, 11, 12}) {
      UnmodifiableHashMap<Integer, String> map = create(size);
      HashMap<Integer, String> hashMap = new HashMap<>(map);
      assertThat(hashMap.hashCode()).isEqualTo(map.hashCode());
    }
  }

  @Test
  public void fromMap() {
    UnmodifiableHashMap<Integer, String> map = create(10);
    assertThat(map).isSameAs(UnmodifiableHashMap.fromMap(map));

    assertThat(UnmodifiableHashMap.fromMap(Map.of())).isSameAs(UnmodifiableHashMap.empty());
  }

  @Test
  public void testWithAll() {
    UnmodifiableHashMap<Integer, String> map = UnmodifiableHashMap.<Integer, String>empty()
      .with(1, "One").with(2, "Two").with(3, "Three");
    UnmodifiableHashMap<Integer, String> map2 = map.withAll(Collections.emptyMap());
    assertThat(map2).isEqualTo(map);
    map2 = map.withAll(Collections.singletonMap(4, "Four"));
    assertThat(map2).hasSize(4);
    assertThat(map2.get(4)).isEqualTo("Four");
    assertThat(map2.entrySet().containsAll(map.entrySet())).isTrue();

    map2 = map.withAll(UnmodifiableHashMap.<Integer, String>empty().with(0, "Zero").with(4, "Four"));
    assertThat(map2).hasSize(5);
    assertThat(map2.get(4)).isEqualTo("Four");
    assertThat(map2.get(0)).isEqualTo("Zero");
    assertThat(map2.entrySet().containsAll(map.entrySet())).isTrue();
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
