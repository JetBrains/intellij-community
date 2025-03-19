// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@FunctionalInterface
public interface UpdatedEntryProcessor<Key, Value> {

  enum UpdateKind {ADDED, UPDATED, REMOVED}

  default void added(Key key, Value value, int inputId) throws StorageException {
    process(UpdateKind.ADDED, key, value, inputId);
  }

  default void updated(Key key, Value value, int inputId) throws StorageException {
    process(UpdateKind.UPDATED, key, value, inputId);
  }

  default void removed(Key key, int inputId) throws StorageException {
    process(UpdateKind.REMOVED, key, /*value: */ null, inputId);
  }

  void process(@NotNull UpdateKind kind, Key key, Value value, int inputId) throws StorageException;
}
