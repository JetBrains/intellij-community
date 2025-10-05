// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.LongStream;

import static com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap.NO_VALUE;
import static org.junit.jupiter.api.Assertions.*;

public abstract class DurableIntToMultiIntMapTestBase<M extends DurableIntToMultiIntMap> {

  protected final int entriesCountToTest;

  protected M multimap;

  protected DurableIntToMultiIntMapTestBase(int entriesCountToTest) { this.entriesCountToTest = entriesCountToTest; }

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
  public void mapIsEmpty_AfterInsertedValue_AndClear() throws IOException {
    multimap.put(1, 2);
    multimap.clear();
    assertTrue(multimap.isEmpty(),
                "Map should be empty after .clear()");
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
  public void manyKeyValues_PutRemovedAndPutAgain_AreAllExistInTheMap() throws IOException {
    long[] packedKeysValues = generateUniqueKeyValues(entriesCountToTest);

    //test for entries removing-overwriting: add/remove/add again the entries from the map,
    // and check map behave as-if only the last added entries are there

    putAllEntries(multimap, packedKeysValues);
    removeAllEntries(multimap, packedKeysValues);

    assertEquals(0, multimap.size(),
                 "Map must be empty since all entries were removed"
    );

    for (long packedKeyValue : packedKeysValues) {
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      boolean reallyPut = multimap.put(key, value);
      assertTrue(reallyPut, "(" + key + ", " + value + ") must be a new entry for the map");
    }

    assertEquals(packedKeysValues.length,
                 multimap.size(),
                 "Map.size must be equal to the number of entries added"
    );

    for (long packedKeyValue : packedKeysValues) {
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      assertTrue(multimap.has(key, value),
                 "(" + key + ", " + value + ") must exist in the map");
    }
  }

  @Test
  public void withManyKeyValuesPut_MultimapSizeIsEqualToNumberOfTruthReturned() throws IOException {
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
  public void withManyKeyValuesPut_AndCleared_NoneOfPutEntriesAreInMapAnymore() throws IOException {
    long[] packedKeysValues = generateUniqueKeyValues(entriesCountToTest);

    for (long packedKeyValue : packedKeysValues) {
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      multimap.put(key, value);
    }

    multimap.clear();

    for (long packedKeyValue : packedKeysValues) {
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);


      assertFalse(
        multimap.has(key, value),
        "multimap.has("+key+", "+value+") must return false after .clear()"
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
  public void remove_DeletesKeyValueFromTheMultimap_AndReturnsTrueIfKeyValuePreviouslyExists() throws IOException {
    long[] packedKeysValues = generateUniqueKeyValues(entriesCountToTest);
    putAllEntries(multimap, packedKeysValues);

    for (long packedKeyValue : packedKeysValues) {
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      assertTrue(multimap.has(key, value),
                 "[key,value] was put before => must exist in the map");

      boolean existentRemoved = multimap.remove(key, value);

      assertTrue(existentRemoved,
                 "[key,value] exist in the map => remove must return true");
      assertFalse(multimap.has(key, value),
                  "[key,value] was just removed => must NOT exist in the map anymore");

      boolean notExistentRemoved = multimap.remove(key, value);
      assertFalse(notExistentRemoved,
                  "[key,value] NOT exist in the map => remove must return false");
    }

    assertEquals(
      0,
      multimap.size(),
      "All values added -- were removed, size must be 0"
    );
    assertTrue(
      multimap.isEmpty(),
      "All values added -- were removed, size must be 0"
    );
  }

  @Test
  public void replace_ChangedOldValue_WithNewOne() throws IOException {
    multimap.put(1, 2);
    multimap.put(1, 3);

    assertEquals(2, multimap.size(), "there are 2 entries in the multimap");

    multimap.replace(1, 3, /* -> */ 4);

    assertTrue(multimap.has(1, 2), "1->2 mapping should remain untouched");
    assertTrue(multimap.has(1, 4), "1->3 mapping should be replaced with 1->4");
    assertFalse(multimap.has(1, 3), "1->3 mapping should not exist anymore");

    assertEquals(2, multimap.size(), "there are still 2 entries in the multimap");
  }

  @Test
  public void replace_RemovesOldValue_IfNewValueMappingAlreadyExistsInMultimap() throws IOException {
    multimap.put(1, 2);
    multimap.put(1, 3);

    assertEquals(2, multimap.size(), "there are 2 entries in the multimap");

    //replace 2 with 3 -- and 3 is already exist in the map:
    multimap.replace(1, 2, /* -> */ 3);

    assertFalse(multimap.has(1, 2), "1->2 mapping should remain untouched");
    assertTrue(multimap.has(1, 3), "1->3 mapping should exist (replaced from 1->2)");

    assertEquals(1, multimap.size(), "there is only 1 entry remain in the multimap (2 entries have collapsed)");
    IntOpenHashSet values = lookupAllValues(multimap, 1);
    assertEquals(1, values.size());
  }


  @Test
  public void closeIsSafeToCallTwice() throws IOException {
    multimap.close();
    multimap.close();
  }



  /* ======================== infrastructure: ================================================================ */


  protected static void putAllEntries(@NotNull DurableIntToMultiIntMap multimap,
                                      long[] packedKeysValues) throws IOException {
    for (long packedKeyValue : packedKeysValues) {
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      multimap.put(key, value);
    }
  }

  private static void removeAllEntries(@NotNull DurableIntToMultiIntMap multimap,
                                       long[] packedKeysValues) throws IOException {
    for (long packedKeyValue : packedKeysValues) {
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      multimap.remove(key, value);
    }
  }

  protected int lookupSingleValue(int key,
                                  int value) throws IOException {
    return multimap.lookup(key, v -> (v == value));
  }

  protected static @NotNull IntOpenHashSet lookupAllValues(@NotNull DurableIntToMultiIntMap multimap,
                                                           int key) throws IOException {
    IntOpenHashSet values = new IntOpenHashSet();
    multimap.lookup(key, value -> {
      values.add(value);
      return false;//don't stop, keep looking
    });
    return values;
  }

  protected static int key(long packedKeyValue) {
    return (int)(packedKeyValue >> 32);
  }

  protected static int value(long packedKeyValue) {
    return (int)packedKeyValue;
  }

  protected static long[] generateUniqueKeyValues(int size) {
    return ThreadLocalRandom.current().longs()
      //generate more multi-value-keys, to better check appropriate branches: +1,+2,... is almost always
      // have same upper 32 bits (=key), but different lower 32 bits (=value) => this switch creates
      // approximately 3/14 of keys with 2 values, another 1/14 of keys with 5 values, and 10/14 of
      // keys with a single value
      .flatMap(v -> switch ((int)(v % 14)) {
        case 13 -> LongStream.of(v, v + 1, v - 1, v + 42, v - 42);
        case 10, 11, 12 -> LongStream.of(v, v + 1);
        default -> LongStream.of(v);
      })
      .filter(v -> key(v) != NO_VALUE
                   && value(v) != NO_VALUE)
      .distinct()
      .limit(size)
      .toArray();
  }
}