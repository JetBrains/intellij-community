// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The issue with property-based tests of durable Maps is: because Map is durable, small and large worksets
 * may trigger different code paths. So to really stress most of branches in the code one need to generate
 * quite a lot of key-values -- ideally, more than fit into a heap. But so many key-values trigger OutOfMemory
 * quite quickly -- especially because we have not very big -Xmx in TeamCity runs.
 * <p>
 * So the trick: generate not key-values, but a memory-frugal 'substrate' -- an int. Introduce a 'substrate decoder',
 * function [int->Entry(key, value)]. Keep only the substrate in memory (=frugal) and convert the substrate to
 * the Entry as-needed -- don't keep the generated Entry in memory.
 */
public abstract class KeyValueStoreTestBase<K, V, S extends KeyValueStore<K, V>> {

  private static final int ENOUGH_KEY_VALUES = 1_000_000;

  /** Simplest key-value substrate decoder */
  public static final IntFunction<Entry<String, String>> STRING_SUBSTRATE_DECODER = substrate -> {
    String key = String.valueOf(substrate);
    if(substrate % 1024 == 1023){
      String veryLongKey = StringUtil.repeat(key, 1024);
      return Map.entry(
        veryLongKey,
        String.valueOf(substrate) + '.' + veryLongKey
      );
    }

    return Map.entry(
      key,
      String.valueOf(substrate) + '.' + substrate
    );
  };

  protected static int[] keyValuesSubstrate;

  protected abstract @NotNull StorageFactory<? extends S> factory();

  private Path storagePath;

  protected S storage;

  /**
   * Converts int (substrate) into a Entry(key, value).
   * Property: for different input substrate (int) function must return both Key and Value different
   */
  private final IntFunction<Entry<K, V>> keyValueSubstrateDecoder;

  @BeforeAll
  static void beforeAll() {
    keyValuesSubstrate = generateDataSubstrate(ThreadLocalRandom.current(), ENOUGH_KEY_VALUES);
  }


  protected KeyValueStoreTestBase(@NotNull IntFunction<Entry<K, V>> decoder) { keyValueSubstrateDecoder = decoder; }

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws IOException {
    StorageFactory<? extends S> factory = factory();
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
    Entry<K, V> entry = keyValue(42);

    K key = entry.getKey();
    V value = entry.getValue();

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);

    assertEquals(value,
                 storage.get(key));
  }

  @Test
  @Disabled("Null is not supported now")
  public void nullKeyValuePut_returnedByGet() throws IOException {
    Entry<K, V> entry = keyValue(42);

    K key = null;
    V value = entry.getValue();

    assertNull(storage.get(key),
               "Empty store contains no keys");

    storage.put(key, value);

    assertEquals(value,
                 storage.get(key));
  }


  @Test
  public void manyKeysValuesPut_returnedByGet() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();

      storage.put(key, value);

      assertEquals(value,
                   storage.get(key),
                   "store[" + key + "] must be == [" + value + "]");
    }

    for (int substrate : keyValuesSubstrate) {
      Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();
      assertEquals(value,
                   storage.get(key),
                   "store[" + key + "] must be == [" + value + "]");
    }
  }

  @Test
  public void manyKeysValuesPut_returnedByGet_afterReopen() throws IOException {
    for (int substrate : keyValuesSubstrate) {
      Entry<K, V> entry = keyValue(substrate);
      storage.put(entry.getKey(), entry.getValue());
    }

    reopenStorage();

    for (int substrate : keyValuesSubstrate) {
      Entry<K, V> entry = keyValue(substrate);
      K key = entry.getKey();
      V value = entry.getValue();
      assertEquals(value,
                   storage.get(key),
                   "store[" + key + "] must be == [" + value + "] even after reopen");
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

  protected Entry<K, V> keyValue(int substrate) {
    //We need quite a lot of data to test persistent map, so it is taxing to actually store all key-values in memory.
    // Instead, we keep in memory just a single int32, and generate key & value from that int with bijection:
    return keyValueSubstrateDecoder.apply(substrate);
  }

  protected S reopenStorage() throws IOException {
    if (storage != null) {
      storage.close();
    }
    storage = factory().open(storagePath);
    return storage;
  }
}