/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;
import com.intellij.util.indexing.impl.InputData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for the <a href="https://en.wikipedia.org/wiki/Search_engine_indexing#Inverted_indices">inverted indexes</a>.
 */
public interface InvertedIndex<Key, Value, Input> {
  @NotNull
  ValueContainer<Value> getData(@NotNull Key key) throws StorageException;

  /**
   * Maps input as the first stage and returns a computation that does actual index data structure update.
   * It may be used to separate long-running input mapping from writing data to disk.
   * Computable returns `true` if data has been saved without errors, otherwise - `false`.
   */
  @NotNull
  Computable<Boolean> mapInputAndPrepareUpdate(int inputId, @Nullable Input content);

  @NotNull Computable<Boolean> prepareUpdate(int inputId, @NotNull InputData<Key, Value> data);

  void flush() throws StorageException;

  void clear() throws StorageException;

  void dispose();


  /**
   * Method was introduced for analytics purposes, and for that it is not required to be precise,
   * i.e. it is OK to provide estimations, outdated info, include keys just removed, or something like
   * that -- but it should be fast (ideally O(1), but at least sublinear on size).
   * <p>
   * It could be hard/costly to implement this method precisely for data structures with layered caching,
   * and it is not clear would the method be useful in other contexts there precision is important,
   * is it worth to define it as precise, and take associated costs.
   *
   * @return approximated number of keys in index, or -1 if this index doesn't provide such information
   */
  @ApiStatus.Experimental
  int keysCountApproximately();
}
