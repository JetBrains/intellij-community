// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing;

import com.intellij.util.indexing.impl.InputData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for the <a href="https://en.wikipedia.org/wiki/Search_engine_indexing#Inverted_indices">inverted indexes</a>.
 */
public interface InvertedIndex<Key, Value, Input> {
  @NotNull ValueContainer<Value> getData(@NotNull Key key) throws StorageException;

  /**
   * Maps input as the first stage and returns a computation that does actual index data structure update.
   * It may be used to separate long-running input mapping from writing data to disk.
   * Computable returns `true` if data has been saved without errors, otherwise - `false`.
   */
  @NotNull StorageUpdate mapInputAndPrepareUpdate(int inputId, @Nullable Input content);

  /**
   * Second part of {@link #mapInputAndPrepareUpdate(int, Object)}): input already parsed and mapped to InputData[Key,Value]
   * and this method creates an object to apply parsed input to index storages.
   */
  @NotNull StorageUpdate prepareUpdate(int inputId, @NotNull InputData<Key, Value> data);

  void flush() throws StorageException;

  void clear() throws StorageException;

  void dispose();
}
