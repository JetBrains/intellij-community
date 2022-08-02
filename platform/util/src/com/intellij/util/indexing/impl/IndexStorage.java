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

package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Flushable;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public interface IndexStorage<Key, Value> extends Flushable {

  void addValue(Key key, int inputId, Value value) throws StorageException;

  void removeAllValues(@NotNull Key key, int inputId) throws StorageException;

  default void updateValue(Key key, int inputId, Value newValue) throws StorageException {
    removeAllValues(key, inputId);
    addValue(key, inputId, newValue);
  }

  void clear() throws StorageException;

  @NotNull
  ValueContainer<Value> read(Key key) throws StorageException;

  void clearCaches();

  void close() throws StorageException;

  @Override
  void flush() throws IOException;

  /**
   * Method was introduced for analytics purposes, and for that it is not required to be precise,
   * i.e. it is OK to provide estimations, outdated info, include keys just removed, or something like
   * that -- but it should be fast (ideally O(1), but at least sublinear on size).
   * 
   * It could be hard/costly to implement this method precisely for data structures with layered caching,
   * and it is not clear would the method be useful in other contexts there precision is important,
   * is it worth to define it as precise, and take associated costs.
   *
   * @return approximated number of keys in index, or -1 if this index doesn't provide such information
   */
  @ApiStatus.Experimental
  int keysCountApproximately();
}
