// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public abstract class KeyValueStoreTestBase<S extends KeyValueStore<String, String>> {

  private static final int ENOUGH_KEY_VALUES = 1_000_000;

  protected static int[] keyValuesSubstrate;

  protected abstract @NotNull StorageFactory<S> factory();

  private Path storagePath;
  protected S storage;

  @BeforeAll
  static void beforeAll() {
    keyValuesSubstrate = generateDataSubstrate(ThreadLocalRandom.current(), ENOUGH_KEY_VALUES);
  }

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws IOException {
    StorageFactory<S> factory = factory();
    storagePath = tempDir.resolve("storage");
    storage = factory.open(storagePath);
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
  @Disabled("Null is not supported now")
  public void nullKeyValuePut_returnedByGet() throws IOException {
    String key = null;
    String value = "testValue";

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);

    assertEquals(value,
                 storage.get(key));
  }

  @Test
  public void emptyKeyValuePut_returnedByGet() throws IOException {
    //I want to check key with serialized length=0 is treated normally by the map
    // I implicitly assume empty string is serialized to the byte[0]
    String key = "";
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
      String key = entry.getKey();
      String value = entry.getValue();

      storage.put(key, value);

      assertEquals(value,
                   storage.get(key),
                   "store[" + key + "] must be == [" + value + "]");
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
  public void manyKeysValuesPut_returnedByGet_afterReopen() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Entry<String, String> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }

    reopenStorage();

    for (int substrate : keyValuesSubstrate) {
      Entry<String, String> entry = keyValue(substrate);
      String key = entry.getKey();
      String value = entry.getValue();
      assertEquals(value,
                   storage.get(key),
                   "store[" + key + "] must be == [" + value + "] even after reopen");
    }
  }

  @Test
  public void keysWithCollidingHashes_AreStillPutAndGetDifferently() throws IOException {
    String key = "testKey";
    List<String> collidingKeys = generateHashCollisions(key);

    storage.put(key, key);
    for (String collidingKey : collidingKeys) {
      storage.put(collidingKey, collidingKey);
    }


    assertEquals(key,
                 storage.get(key));
    for (String collidingKey : collidingKeys) {
      assertEquals(collidingKey,
                   storage.get(collidingKey));
    }
  }

  // ============================= infrastructure: ========================================================== //

  private static int[] generateDataSubstrate(@NotNull ThreadLocalRandom rnd,
                                             int size) {
    return rnd.ints(0, Integer.MAX_VALUE)
      .distinct()
      .limit(size)
      .toArray();
  }

  protected static Entry<String, String> keyValue(int substrate) {
    //We need quite a lot of data to test persistent map, so it is taxing to actually store all key-values in memory.
    // Instead, we keep in memory just a single int32, and generate key & value from that int with bijection:
    String key = String.valueOf(substrate);
    String value = key + '.' + key;
    return Map.entry(
      key,
      value
    );
  }

  protected S reopenStorage() throws IOException {
    if (storage != null) {
      storage.close();
    }
    storage = factory().open(storagePath);
    return storage;
  }

  /** @return >1 strings with same hash-code as sample */
  protected static List<String> generateHashCollisions(@NotNull String sample) {
    if (sample.length() <= 1) {
      //just too hard
      throw new IllegalArgumentException("Can't generate hash-collisions for empty and 1-char strings");
    }

    char ch1 = sample.charAt(0);
    char ch2 = sample.charAt(1);
    String suffix = sample.substring(2);

    //31*ch1 + ch2 == 31*x + y
    List<String> hashCollisions = List.of(
      Character.toString(ch1 - 1) + Character.toString(ch2 + 31) + suffix,
      Character.toString(ch1 - 2) + Character.toString(ch2 + 31 * 2) + suffix,
      Character.toString(ch1 - 3) + Character.toString(ch2 + 31 * 3) + suffix
    );
    for (String collision : hashCollisions) {
      if (collision.hashCode() != sample.hashCode()) {
        throw new AssertionError("[" + collision + "].hash=" + collision.hashCode() + " != [" + sample + "].hash=" + sample.hashCode());
      }
    }
    return hashCollisions;
  }
}