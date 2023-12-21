// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public abstract class KeyValueStoreTestBase<S extends KeyValueStore<String, String>> {

  private static final int ENOUGH_KEY_VALUES = 1000_000;

  protected static int[] keyValuesSubstrate;

  protected abstract @NotNull StorageFactory<S> factory();

  protected S storage;

  @BeforeAll
  static void beforeAll() {
    keyValuesSubstrate = generateDataSubstrate(ThreadLocalRandom.current(), ENOUGH_KEY_VALUES);
  }

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws IOException {
    StorageFactory<S> factory = factory();
    storage = factory.open(tempDir.resolve("storage"));
  }

  @AfterEach
  void tearDown() throws IOException {
    if (storage != null) {
      storage.close();
      if (storage instanceof CleanableStorage cleanableStorage) {
        cleanableStorage.closeAndClean();
      }
    }
  }

  @Test
  public void emptyStorage_IsNotDirty() {
    assertFalse(storage.isDirty());
  }

  @Test
  public void singleKeyValuePut_returnedByGet() throws IOException {
    String key = "testKey";
    String value = "testValue";

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);

    assertEquals(value,
                 storage.get(key));
  }

  @Test
  public void manyKeysValuesPut_returnedByGet() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Entry<String, String> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }

    for (int substrate : keyValuesSubstrate) {
      Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();
      assertEquals(value,
                   storage.get(key),
                   "store[" + key + "] must be == [" + value + "]");
    }
  }

  @Test
  @Disabled("Null is not supported by current Descriptors/Externalizers")
  public void nullKey_isAllowed() throws IOException {
    String key = null;
    String value = "testValue";

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);

    assertEquals(value,
                 storage.get(key));
  }


  private static int[] generateDataSubstrate(@NotNull ThreadLocalRandom rnd,
                                             int size) {
    return rnd.ints(0, Integer.MAX_VALUE)
      .distinct()
      .limit(size)
      .toArray();
  }

  protected static Entry<String, String> keyValue(int substrate) {
    //We need quite a lot of data to test persistent map, so it is taxing to actually store all key-values in memory.
    // Instead, we keep in memory just a single int, and generate key & value from that int with bijection:
    String key = String.valueOf(substrate);
    String value = key + '.' + key;
    return Map.entry(
      key,
      value
    );
  }
}