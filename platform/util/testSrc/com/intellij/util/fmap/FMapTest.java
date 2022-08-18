// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.fmap;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.testFramework.UsefulTestCase.assertSameElements;
import static com.intellij.util.fmap.ArrayBackedFMap.ARRAY_THRESHOLD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class FMapTest {

  @Test
  public void equality() {
    for (int i : new int[]{0, 1, 2, 3, ARRAY_THRESHOLD, ARRAY_THRESHOLD + 1, ARRAY_THRESHOLD + 5}) {
      var expected = createMap(i);
      var map = createMap(i);
      var reversedMap = createMapReversed(i);
      assertEquals(expected.hashCode(), map.hashCode());
      assertEquals(expected, map);
      assertEquals(expected.hashCode(), reversedMap.hashCode());
      assertEquals(expected, reversedMap);
    }
  }

  @Test
  public void factoryOf() {
    assertEquals(FMap.<String, String>empty().plus("k", "v"), FMap.of("k", "v"));
    assertEquals(FMap.<String, String>empty().plus("k1", "v1").plus("k2", "v2"), FMap.of("k1", "v1", "k2", "v2"));
  }

  @Test
  public void emptyMap() {
    testMap(0);
  }

  @Test
  public void oneKey() {
    testMap(1);
  }

  @Test
  public void twoKeys() {
    testMap(2);
  }

  @Test
  public void threeKeys() {
    testMap(3);
  }

  @Test
  public void arrayThresholdKeys() {
    testMap(ARRAY_THRESHOLD);
    testMap(ARRAY_THRESHOLD + 1);
  }

  @Test
  public void manyKeys() {
    testMap(ARRAY_THRESHOLD + 5);
  }

  private static void testMap(int size) {
    FMap<String, String> map = createMap(size);
    assertSize(size, map);
    assertKeys(map);
    assertValues(map);
    testPlus(map);
    testMinus(map);
  }

  private static FMap<String, String> createMap(int size) {
    FMap<String, String> map = FMap.empty();
    for (int i = 0; i < size; i++) {
      map = map.plus(key(i), value(i));
    }
    return map;
  }

  private static FMap<String, String> createMapReversed(int size) {
    FMap<String, String> map = FMap.empty();
    for (int i = size - 1; i >= 0; i--) {
      map = map.plus(key(i), value(i));
    }
    return map;
  }

  private static @NotNull String key(int i) {
    return "k" + i;
  }

  private static @NotNull String value(int i) {
    return "v" + i;
  }

  private static void assertSize(int expectedSize, @NotNull FMap<String, String> map) {
    assertEquals(expectedSize == 0, map.isEmpty());
    assertEquals(expectedSize, map.size());
  }

  private static void assertKeys(@NotNull FMap<String, String> map) {
    assertSameElements(map.keys(), IntStream.range(0, map.size()).mapToObj(FMapTest::key).collect(Collectors.toList()));
  }

  private static void assertValues(@NotNull FMap<String, String> map) {
    for (int i = 0; i < map.size(); i++) {
      assertEquals(value(i), map.get(key(i)));
    }
  }

  private static void testPlus(@NotNull FMap<String, String> map) {
    int size = map.size();
    for (int i = 0; i < size; i++) {
      String key = key(i);
      String value = value(i);
      assertSame(map, map.plus(key, value)); // equal key, equal value
      var updatedMap = map.plus(key, "x" + value); // equal key, new value
      assertSize(size, updatedMap);
      assertKeys(updatedMap);
      assertValues(updatedMap, i);
    }
  }

  private static void assertValues(@NotNull FMap<String, String> map, int changedKey) {
    for (int i = 0; i < map.size(); i++) {
      String key = key(i);
      String expectedValue = i == changedKey ? "xv" + i : value(i);
      assertEquals(expectedValue, map.get(key));
    }
  }

  private static void testMinus(@NotNull FMap<String, String> map) {
    assertSame(map, map.minus("nonexistentKey"));
    int size = map.size();
    for (int i = 0; i < size; i++) {
      var updatedMap = map.minus(key(i));
      assertSize(size - 1, updatedMap);
      assertKeys(size, updatedMap, i);
      assertValues(size, updatedMap, i);
    }
  }

  private static void assertKeys(int sizeBefore, @NotNull FMap<String, String> updatedMap, int removedKey) {
    List<String> expectedKeys = IntStream.range(0, sizeBefore)
      .filter(i -> i != removedKey)
      .mapToObj(FMapTest::key)
      .collect(Collectors.toList());
    assertSameElements(updatedMap.keys(), expectedKeys);
  }

  private static void assertValues(int sizeBefore, @NotNull FMap<String, String> updatedMap, int removedKey) {
    for (int i = 0; i < sizeBefore; i++) {
      String key = key(i);
      String expectedValue = i == removedKey ? null : value(i);
      assertEquals(expectedValue, updatedMap.get(key));
    }
  }
}
