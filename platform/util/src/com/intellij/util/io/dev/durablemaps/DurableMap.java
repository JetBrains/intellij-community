// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.durablemaps;

import com.intellij.openapi.Forceable;
import com.intellij.util.Processor;
import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.KeyValueStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

/**
 * Analog of {@link com.intellij.util.io.PersistentMap}
 * Introduced to separate API guaranties for development phase -- could be merged later on, if actual
 * API/guarantees happen to be compatible enough.
 */
@ApiStatus.Internal
public interface DurableMap<K, V> extends KeyValueStore<K, V>,
                                          Compactable<DurableMap<K, V>>,
                                          CleanableStorage, Forceable, Closeable {

  //TODO RC:
  //  1. Nullability: do we need to allow null keys? null values?
  //     So far I decided to allow null values, but prohibit null keys.
  //  2. Should it be any difference between .remove(key) and .put(key, null)?
  //     So far I assume they are the same
  //  3. should null keys/values be implemented on the DataExternalizerEx level, or it is
  //     Map's responsibility to serialize nulls, while DataExternalizerEx should be @NotNull?
  //     Current implementation avoid passing nulls to Externalizers, and uses own schema to
  //     mark null records in persistent format.

  @Override
  V get(@NotNull K key) throws IOException;

  @Override
  void put(@NotNull K key,
           @Nullable V value) throws IOException;

  void remove(@NotNull K key) throws IOException;

  boolean containsMapping(@NotNull K key) throws IOException;

  /**
   * Process all keys registered in the map.
   * Note that keys which were removed at some point might be returned as well.
   */
  boolean processKeys(@NotNull Processor<? super K> processor) throws IOException;

  @Override
  boolean isDirty();

  @Override
  void force() throws IOException;


  boolean isClosed();
}
