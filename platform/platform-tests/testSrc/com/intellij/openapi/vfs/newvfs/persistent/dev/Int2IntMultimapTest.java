// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.newvfs.persistent.dev.InvertedFilenameHashBasedIndex.Int2IntMultimap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

/**
 *
 */
public class Int2IntMultimapTest {

  public static final int ENOUGH_ENTRIES = 100_000;


  private Int2IntMultimap multimap;

  @Before
  public void setUp() throws Exception {
    multimap = new Int2IntMultimap();
  }

  @After
  public void tearDown() throws Exception {
    assertInvariant_ValuesForEachKeysAreUnique(multimap);
  }

  @Test
  public void emptyMultimapHasSizeZero() {
    assertEquals(
      multimap.size(),
      0
    );
  }

  @Test
  public void manyKeyValuesInserted_AreAllExistInTheMap() {
    final long[] packedKeysValues = generateKeyValues(ENOUGH_ENTRIES);

    final Int2IntMultimap multimap = new Int2IntMultimap();
    for (final long packedKeyValue : packedKeysValues) {
      final int key = key(packedKeyValue);
      final int value = value(packedKeyValue);

      multimap.put(key, value);

      assertTrue(
        "Multimap must have (key,value) just added",
        multimap.has(key, value)
      );
    }

    assertEquals(
      "Multimap size must be == number of unique (key,value) pairs",
      multimap.size(),
      new LongOpenHashSet(packedKeysValues).size()
    );
  }

  @Test
  public void manyKeyValuesInserted_AndRemoved_NoneExistInTheMap() {
    final long[] packedKeysValues = generateKeyValues(ENOUGH_ENTRIES);

    final Int2IntMultimap multimap = new Int2IntMultimap();
    for (final long packedKeyValue : packedKeysValues) {
      final int key = key(packedKeyValue);
      final int value = value(packedKeyValue);

      multimap.put(key, value);

      assertTrue(
        "Multimap must have (key,value) just added",
        multimap.has(key, value)
      );
    }

    for (final long packedKeyValue : packedKeysValues) {
      final int key = key(packedKeyValue);
      final int value = value(packedKeyValue);

      multimap.remove(key, value);

      assertFalse(
        "Multimap must NOT have (key, value) just removed",
        multimap.has(key, value)
      );
    }

    assertEquals(
      "Multimap size must be 0 after all entries are removed",
      multimap.size(),
      0
    );
  }

  @Test
  public void afterManyKeyValues_InsertedAndRemovedRandomly_MultimapContentIsSameAsEtalon() {
    final long[] packedKeysValues = generateKeyValues(ENOUGH_ENTRIES);

    final Int2IntMultimap multimap = new Int2IntMultimap();
    //Use Map[int -> Set[int]] as 'etalon' multimap implementation to compare against:
    final Int2ObjectMap<IntSet> etalon = new Int2ObjectOpenHashMap<>();

    for (final long packedKeyValue : packedKeysValues) {
      final int key = key(packedKeyValue);
      final int value = value(packedKeyValue);

      multimap.put(key, value);
      etalon.computeIfAbsent(key, _k -> new IntOpenHashSet()).add(value);
    }

    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < ENOUGH_ENTRIES; i++) {
      final int randomIndex = rnd.nextInt(packedKeysValues.length);
      final long packedKeyValue = packedKeysValues[randomIndex];
      final int key = key(packedKeyValue);
      final int value = value(packedKeyValue);

      if (rnd.nextBoolean()) {
        multimap.put(key, value);
        etalon.computeIfAbsent(key, _k -> new IntOpenHashSet()).add(value);
      }
      else {
        multimap.remove(key, value);
        etalon.computeIfAbsent(key, _k -> new IntOpenHashSet()).remove(value);
      }
    }

    //now check content against etalon:
    for (final Int2ObjectMap.Entry<IntSet> e : etalon.int2ObjectEntrySet()) {
      final int key = e.getIntKey();
      final IntSet etalonValues = e.getValue();
      final IntOpenHashSet multimapValues = getValues(multimap, key);

      assertEquals(
        "key[" + key + "] values must be the same ",
        etalonValues,
        multimapValues
      );
    }

    assertEquals(
      "Multimap size must be == number of unique (key,value) pairs",
      multimap.size(),
      etalon.values().stream().mapToInt(values -> values.size()).sum()
    );
  }


  /* ======================== infrastructure: ================================================================ */

  private static void assertInvariant_ValuesForEachKeysAreUnique(final Int2IntMultimap multimap) {
    final IntOpenHashSet keys = new IntOpenHashSet();
    multimap.forEach((key, value) -> keys.add(key));
    for (final int key : keys) {
      final IntOpenHashSet values = new IntOpenHashSet();
      multimap.get(key, value -> {
        if (!values.add(value)) {
          fail("get(" + key + ") values are non-unique: value[" + value + "] was already reported " + values);
        }
        return true;
      });
    }
  }

  @NotNull
  private static IntOpenHashSet getValues(final Int2IntMultimap multimap,
                                          final int key) {
    final IntOpenHashSet values = new IntOpenHashSet();
    multimap.get(key, value -> {
      values.add(value);
      return true;
    });
    return values;
  }

  private static long pack(final int key,
                           final int value) {
    return Integer.toUnsignedLong(key) << Integer.SIZE | Integer.toUnsignedLong(value);
  }

  private static int key(final long packedKeyValue) {
    return (int)(packedKeyValue >> 32);
  }

  private static int value(final long packedKeyValue) {
    return (int)packedKeyValue;
  }

  private static long[] generateKeyValues(final int size) {
    return ThreadLocalRandom.current().longs()
      .filter(v -> key(v) != Int2IntMultimap.NO_VALUE
                   && value(v) != Int2IntMultimap.NO_VALUE)
      .limit(size)
      .toArray();
  }
}