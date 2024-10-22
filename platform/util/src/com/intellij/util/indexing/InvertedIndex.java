// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing;

import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.ValueContainerProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for the <a href="https://en.wikipedia.org/wiki/Search_engine_indexing#Inverted_indices">inverted indexes</a>.
 * Thread-safety is up to implementation
 */
public interface InvertedIndex<Key, Value, Input> {
  /**
   * This method is deprecated, because returned value container is hard to make thread-safe.
   * So {@link #withData(Object, ValueContainerProcessor)} is a new method, and this method now returns a copy, which is
   * thread-safe, but not very effective
   */
  @Deprecated
  default @NotNull ValueContainer<Value> getData(@NotNull Key key) throws StorageException {
    ValueContainerImpl<Value> defensiveCopy = ValueContainerImpl.createNewValueContainer();
    withData(
      key,
      container -> container.forEach((id, value) -> {
        defensiveCopy.addValue(id, value);
        return true;
      })
    );
    return defensiveCopy;
  }


  /**
   * The processor will be invoked on a {@link ValueContainer} corresponding to the given key.
   * <b>NOTE</b>: processor could be invoked <b>more than once</b> e.g. if an actual storage is sharded -- so the processor
   * code must be ready to aggregate results of >1 container passed in.
   *
   * @return true if all data was processed, false if stopped prematurely because processor returns false at some
   * point
   */
  <E extends Exception> boolean withData(@NotNull Key key,
                                         @NotNull ValueContainerProcessor<Value, E> processor) throws StorageException, E;


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

  /**
   * TODO RC: the interface doesn't extend {@link com.intellij.openapi.Disposable}, so the name is misleading -- the method is
   * better renamed to .close() with interface extending (Auto){@link java.io.Closeable}
   */
  void dispose();
}
