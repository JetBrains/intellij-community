// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public interface IndexStorage<Key, Value> extends Flushable, Closeable {

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

  @Override
  void flush() throws IOException;

  boolean isDirty();

  @Override
  void close() throws IOException;

  @Internal
  boolean isClosed();
}
