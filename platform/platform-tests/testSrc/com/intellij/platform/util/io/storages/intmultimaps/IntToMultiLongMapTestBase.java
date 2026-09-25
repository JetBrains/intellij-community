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
  public void lookupAndModifySupportsAllValueTransitions() throws Exception {
    assertLookupAndModifyContract(map);
  }

  @Test
  public void lookupAndModifyAppliesAllChangesBeforeProcessorStops() throws Exception {
    assertLookupAndModifyAppliesAllChangesBeforeProcessorStops(map);
  }

  public static void assertLookupAndModifyAppliesAllChangesBeforeProcessorStops(@NotNull IntToMultiLongMap map) throws Exception {
    map.put(1, 10);
    map.put(1, 20);
    map.put(1, 30);
    var modifiedValues = new HashSet<Long>();

    boolean processedAll = map.lookupAndModify(1, (oldValue, newValueRef) -> {
      modifiedValues.add(oldValue);
      newValueRef.set(oldValue + 100);
      return modifiedValues.size() < 2;
    });

    assertFalse(processedAll);
    assertEquals(2, modifiedValues.size());
    for (long oldValue : modifiedValues) {
      assertEquals(NO_VALUE, map.lookup(1, value -> value == oldValue));
      assertEquals(oldValue + 100, map.lookup(1, value -> value == oldValue + 100));
    }
    assertEquals(3, map.size());
  }

  public static void assertLookupAndModifyContract(@NotNull IntToMultiLongMap map) throws Exception {
    map.put(1, 10);
    map.put(1, 20);
    map.put(1, 30);

    boolean processedAll = map.lookupAndModify(1, (oldValue, newValueRef) -> {
      if (oldValue == 10) {
        newValueRef.set(11);
      }
      else if (oldValue == 20) {
        newValueRef.set(NO_VALUE);
      }
      else if (oldValue == NO_VALUE) {
        newValueRef.set(40);
      }
      return true;
    });
    assertTrue(processedAll);
    assertEquals(NO_VALUE, map.lookup(1, value -> value == 10));
    assertEquals(11, map.lookup(1, value -> value == 11));
    assertEquals(NO_VALUE, map.lookup(1, value -> value == 20));
    assertEquals(30, map.lookup(1, value -> value == 30));
    assertEquals(40, map.lookup(1, value -> value == 40));

    boolean stopped = map.lookupAndModify(1, (oldValue, newValueRef) -> {
      if (oldValue != 30) {
        return true;
      }
      newValueRef.set(31);
      return false;
    });
    assertFalse(stopped);
    assertEquals(NO_VALUE, map.lookup(1, value -> value == 30));
    assertEquals(31, map.lookup(1, value -> value == 31));

    boolean insertedAndStopped = map.lookupAndModify(2, (oldValue, newValueRef) -> {
      assertEquals(NO_VALUE, oldValue, "The processor must receive NO_VALUE for a missing key");
      newValueRef.set(50);
      return false;
    });
    assertFalse(insertedAndStopped, "The result only reports whether the processor requested more values");
    assertEquals(50, map.lookup(2, value -> value == 50));
    assertEquals(4, map.size());
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
