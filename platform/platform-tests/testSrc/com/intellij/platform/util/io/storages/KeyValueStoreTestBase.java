// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.KeyValueStore;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.function.IntFunction;

public abstract class KeyValueStoreTestBase<K, V, S extends KeyValueStore<K, V>>
  implements KeyValueStoreTestContext<K, V, S> {

  protected abstract @NotNull StorageFactory<? extends S> factory();

  private Path storagePath;

  protected S storage;

  /**
   * Converts int (substrate) into an Entry(key, value).
   * Property: for different input substrate (int) function must return both Key and Value different
   */
  private final IntFunction<? extends Entry<K, V>> keyValueSubstrateDecoder;

  protected KeyValueStoreTestBase(@NotNull IntFunction<? extends Entry<K, V>> decoder) { keyValueSubstrateDecoder = decoder; }

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

  // ============================= infrastructure: ========================================================== //

  @Override
  public final @NotNull S storageUnderTest() {
    return storage;
  }

  @Override
  public final @NotNull IntFunction<? extends Entry<K, V>> keyValueSubstrateDecoder() {
    return keyValueSubstrateDecoder;
  }

  @Override
  public final @NotNull S reopenStorage() throws IOException {
    if (storage != null) {
      storage.close();
    }
    storage = factory().open(storagePath);
    return storage;
  }

  protected Path storagePath() {
    return storagePath;
  }
}
