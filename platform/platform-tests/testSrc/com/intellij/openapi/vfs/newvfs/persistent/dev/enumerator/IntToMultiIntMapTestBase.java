// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import static org.junit.jupiter.api.Assertions.*;

import com.intellij.openapi.vfs.newvfs.persistent.dev.intmultimaps.IntToMultiIntMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public abstract class IntToMultiIntMapTestBase<M extends IntToMultiIntMap> {

  protected final int entriesCountToTest;

  protected M multimap;

  public IntToMultiIntMapTestBase() {
    this(100_000);
  }

  public IntToMultiIntMapTestBase(int entriesCountToTest) { this.entriesCountToTest = entriesCountToTest; }

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws IOException {
    multimap = create(tempDir);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (multimap != null) {
      multimap.close();
    }
  }

  protected abstract M create(@NotNull Path tempDir) throws IOException;

  @Test
  public void manyKeyValuesInserted_AreAllExistInTheMap() throws IOException {
    long[] packedKeysValues = generateKeyValues(entriesCountToTest);

    for (int i = 0; i < packedKeysValues.length; i++) {
      long packedKeyValue = packedKeysValues[i];
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      multimap.put(key, value);

      assertTrue(multimap.has(key, value),
                 "[" + i + "].has(" + key + "," + value + ") must be true");
    }
  }


  @Test
  public void manyKeyValuesInserted_AreAllExistInTheMap_WithLookup() throws IOException {
    long[] packedKeysValues = generateKeyValues(entriesCountToTest);

    for (int i = 0; i < packedKeysValues.length; i++) {
      long packedKeyValue = packedKeysValues[i];
      int key = key(packedKeyValue);
      int value = value(packedKeyValue);

      multimap.put(key, value);

      assertEquals(value,
                   multimap.lookup(key, v -> v == value),
                   "[" + i + "](key,value) just added must be lookup()-ed in a multimap");
    }
  }

  //TODO RC: test modification of records
  //TODO RC: test multi-mapping (>1 value for the same key)

  /* ======================== infrastructure: ================================================================ */

  //private static void assertInvariant_ValuesForEachKeysAreUnique(final IntToMultiIntMap multimap) throws IOException {
  //  IntOpenHashSet keys = new IntOpenHashSet();
  //  multimap.forEach((key, value) -> keys.add(key));
  //  for (int key : keys) {
  //    IntOpenHashSet values = new IntOpenHashSet();
  //    multimap.lookup(key, value -> {
  //      if (!values.add(value)) {
  //        fail("get(" + key + ") values are non-unique: value[" + value + "] was already reported " + values);
  //      }
  //      return true;
  //    });
  //  }
  //}


  private static @NotNull IntOpenHashSet getValues(@NotNull IntToMultiIntMap multimap,
                                                   int key) throws IOException {
    IntOpenHashSet values = new IntOpenHashSet();
    multimap.lookup(key, value -> {
      values.add(value);
      return true;
    });
    return values;
  }

  private static int key(long packedKeyValue) {
    return (int)(packedKeyValue >> 32);
  }

  private static int value(long packedKeyValue) {
    return (int)packedKeyValue;
  }

  private static long[] generateKeyValues(int size) {
    return ThreadLocalRandom.current().longs()
      .filter(v -> key(v) != IntToMultiIntMap.NO_VALUE
                   && value(v) != IntToMultiIntMap.NO_VALUE)
      .limit(size)
      .toArray();
  }
}