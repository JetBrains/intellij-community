// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.intmultimaps;

import static com.intellij.util.io.dev.intmultimaps.DurableIntToMultiIntMap.NO_VALUE;
import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.LongStream;

public abstract class IntToMultiIntMapTestBase<M extends DurableIntToMultiIntMap> {

  protected final int entriesCountToTest;

  protected M multimap;

  protected IntToMultiIntMapTestBase(int entriesCountToTest) { this.entriesCountToTest = entriesCountToTest; }

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws IOException {
    multimap = openInDir(tempDir);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (multimap != null) {
      multimap.closeAndClean();
    }
  }

  protected abstract M openInDir(@NotNull Path tempDir) throws IOException;

  @Test
  public void ZERO_IS_PROHIBITED_KEY() throws IOException {
    assertThrows(IllegalArgumentException.class,
                 () -> multimap.put(NO_VALUE, 1),
                 "Can't use key=0: 0 is reserved value (NO_VALUE)"
    );
  }

  @Test
  public void ZERO_IS_PROHIBITED_VALUE() throws IOException {
    assertThrows(IllegalArgumentException.class,
                 () -> multimap.put(1, 0),
                 "Can't use value=0: 0 is reserved value (NO_VALUE)"
    );
  }

  @Test
  public void mapIsEmpty_Initially() throws IOException {
    assertTrue(multimap.isEmpty(),
               "Map is just created, must be empty");
  }

  @Test
  public void mapIsNotEmpty_AfterInsertedValue() throws IOException {
    multimap.put(1, 2);
    assertFalse(multimap.isEmpty(),
                "(1,2) record was put, map must NOT be empty anymore");
  }

  @Test
  public void manyKeyValuesPut_AreAllExistInTheMap() throws IOException {
    long[] packedKeysValues = generateUniqueKeyValues(entriesCountToTest);

    for (int i = 0; i < packedKeysValues.length; i++) {
      long packedKeyValue = packedKeysValues[i];
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      multimap.put(key, value);

      assertTrue(multimap.has(key, value),
                 "[" + i + "].has(" + key + "," + value + ") must be true");
      assertEquals(value,
                   lookupSingleValue(key, value),
                   "[" + i + "].lookup(" + key + "," + value + ") must be " + value);
    }
    assertEquals(
      packedKeysValues.length,
      multimap.size(),
      packedKeysValues.length + " values were added to multimap"
    );
  }

  @Test
  public void manyKeyValuesInserted_AreAllExistInTheMap() throws IOException {
    long[] packedKeysValues = generateUniqueKeyValues(entriesCountToTest);

    for (int i = 0; i < packedKeysValues.length; i++) {
      long packedKeyValue = packedKeysValues[i];
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      multimap.lookupOrInsert(key,
                              v -> (v == value),
                              k -> value);

      assertTrue(multimap.has(key, value),
                 "[" + i + "].has(" + key + "," + value + ") must be true");
      assertEquals(value,
                   lookupSingleValue(key, value),
                   "[" + i + "].lookup(" + key + "," + value + ") must be " + value);
    }
  }

  @Test
  public void singleKeyTo2ValuesMapping_CouldBeReadBack() throws IOException {
    multimap.put(1, 2);
    multimap.put(1, 3);

    assertTrue(multimap.has(1, 2));
    assertTrue(multimap.has(1, 3));

    IntOpenHashSet values = lookupAllValues(multimap, 1);

    assertEquals(2, values.size(), "2 values were mapped with key '1': " + values);
    assertTrue(values.contains(2), "Values for key=1 must contain 2");
    assertTrue(values.contains(3), "Values for key=1 must contain 3");
  }


  @Test
  public void withManyKeyValuesPut_SizeIsEqualToNumberOfTruthReturned() throws IOException {
    long[] packedKeysValues = generateUniqueKeyValues(entriesCountToTest);

    int truthsReturnedFromPut = 0;
    for (long packedKeyValue : packedKeysValues) {
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      if (multimap.put(key, value)) {
        truthsReturnedFromPut++;
      }

      assertEquals(
        truthsReturnedFromPut,
        multimap.size(),
        truthsReturnedFromPut + " entries were really put to multimap"
      );
    }
  }

  @Test
  public void putReturnTrue_IfKeyValueNotInTheMap() throws IOException {
    long[] packedKeysValues = generateUniqueKeyValues(entriesCountToTest);

    for (int i = 0; i < packedKeysValues.length; i++) {
      long packedKeyValue = packedKeysValues[i];
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      if (lookupSingleValue(key, value) == NO_VALUE) {
        assertTrue(multimap.put(key, value),
                   "[" + i + "].put(" + key + "," + value + ") must be true since .lookup() can't find value");
        assertEquals(value,
                     lookupSingleValue(key, value),
                     "[" + i + "].lookup(" + key + "," + value + ") must be " + value);
      }
    }
    assertEquals(
      packedKeysValues.length,
      multimap.size(),
      packedKeysValues.length + " values were added to multimap"
    );
  }


  @Test
  public void closeIsSafeToCallTwice() throws IOException {
    multimap.close();
    multimap.close();
  }

  //TODO RC: test modification of records
  //TODO RC: test many multi-mapping (>1 value for the same key)


  /* ======================== infrastructure: ================================================================ */


  private int lookupSingleValue(int key,
                                int value) throws IOException {
    return multimap.lookup(key, v -> (v == value));
  }

  private static @NotNull IntOpenHashSet lookupAllValues(@NotNull DurableIntToMultiIntMap multimap,
                                                         int key) throws IOException {
    IntOpenHashSet values = new IntOpenHashSet();
    multimap.lookup(key, value -> {
      values.add(value);
      return false;//don't stop, keep looking
    });
    return values;
  }

  private static int key(long packedKeyValue) {
    return (int)(packedKeyValue >> 32);
  }

  private static int value(long packedKeyValue) {
    return (int)packedKeyValue;
  }

  private static long[] generateUniqueKeyValues(int size) {
    return ThreadLocalRandom.current().longs()
      .filter(v -> key(v) != NO_VALUE
                   && value(v) != NO_VALUE)
      //generate more multi-keys, to better check apt branches
      .flatMap(v -> switch ((int)(v % 14)) {
        case 13 -> LongStream.of(v, v + 1, v - 1, v + 42, v - 42);
        case 10, 11, 12 -> LongStream.of(v, v + 1);
        default -> LongStream.of(v);
      })
      .distinct()
      .limit(size)
      .toArray();
  }
}