// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Defines the shared contract for `int -> long*` map implementations
public abstract class IntToMultiLongMapTestBase {
  private IntToMultiLongMap map;

  @BeforeEach
  void setUp() throws Exception {
    map = createMap();
  }

  protected abstract @NotNull IntToMultiLongMap createMap() throws Exception;

  @Test
  public void supportsMultipleValuesForTheSameKey() throws Exception {
    assertTrue(map.put(1, 10));
    assertTrue(map.put(1, 20));
    assertFalse(map.put(1, 10), "The map must not add the same pair twice");

    assertEquals(10, map.lookup(1, value -> value == 10));
    assertEquals(20, map.lookup(1, value -> value == 20));
    assertEquals(NO_VALUE, map.lookup(1, _ -> false));
    assertEquals(2, map.size());
  }

  @Test
  public void replacesAndRemovesOnlyTheSpecifiedPair() throws Exception {
    map.put(1, 10);
    map.put(1, 20);

    assertTrue(map.replace(1, 10, 30));
    assertFalse(map.replace(1, 10, 40));
    assertEquals(30, map.lookup(1, value -> value == 30));

    assertTrue(map.remove(1, 20));
    assertFalse(map.remove(1, 20));
    assertEquals(1, map.size());
  }

  @Test
  public void replacingWithAnExistingValueKeepsTheValuesUnique() throws Exception {
    map.put(1, 10);
    map.put(1, 20);

    assertTrue(map.replace(1, 10, 20));
    assertEquals(NO_VALUE, map.lookup(1, value -> value == 10));
    assertEquals(20, map.lookup(1, value -> value == 20));
    assertEquals(1, map.size(), "Replacing with an existing value must remove the old mapping");
  }

  @Test
  public void iteratesAndClearsAllPairs() throws Exception {
    map.put(1, 10);
    map.put(1, 20);
    map.put(2, 30);
    var pairs = new HashSet<String>();

    assertTrue(map.forEach((key, value) -> pairs.add(key + ":" + value)));
    assertEquals(Set.of("1:10", "1:20", "2:30"), pairs);

    map.clear();
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());
  }

  @Test
  public void reservesZeroAsTheMissingValue() {
    assertThrows(IllegalArgumentException.class, () -> map.put(1, NO_VALUE),
                 "The map must reject NO_VALUE as a new value");
    assertThrows(IllegalArgumentException.class, () -> map.replace(1, NO_VALUE, 1),
                 "The map must reject NO_VALUE as an old value");
    assertThrows(IllegalArgumentException.class, () -> map.replace(1, 1, NO_VALUE),
                 "The map must reject NO_VALUE as a replacement value");
    assertThrows(IllegalArgumentException.class, () -> map.remove(1, NO_VALUE),
                 "The map must reject NO_VALUE as a removed value");
  }
}
