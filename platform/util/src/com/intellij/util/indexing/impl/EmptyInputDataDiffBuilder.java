// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@ApiStatus.Internal
public final class EmptyInputDataDiffBuilder<Key, Value> extends DirectInputDataDiffBuilder<Key, Value> {
  public EmptyInputDataDiffBuilder(int inputId) {
    super(inputId);
  }

  @Override
  public @NotNull Collection<Key> getKeys() {
    return Collections.emptySet();
  }

  @Override
  public boolean differentiate(@NotNull Map<Key, Value> newData,
                               @NotNull UpdatedEntryProcessor<? super Key, ? super Value> changesProcessor) throws StorageException {
    return processAllKeyValuesAsAdded(myInputId, newData, changesProcessor);
  }

  public static <Key, Value> boolean processAllKeyValuesAsAdded(int inputId,
                                                                @NotNull Map<Key, Value> addedData,
                                                                @NotNull UpdatedEntryProcessor<? super Key, ? super Value> changesProcessor)
    throws StorageException {
    boolean[] anyAdded = {false};
    try {
      addedData.forEach((key, value) -> {
        try {
          changesProcessor.added(key, value, inputId);
        }
        catch (StorageException e) {
          throw new RuntimeException(e);
        }
        anyAdded[0] = true;
      });
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof StorageException) {
        throw (StorageException)cause;
      }
      throw e;
    }
    return anyAdded[0];
  }

  public static <Key, Value> boolean processAllKeyValuesAsRemoved(int inputId,
                                                                  @NotNull Map<Key, Value> removedData,
                                                                  @NotNull UpdatedEntryProcessor<? super Key, ? super Value> changesProcessor)
    throws StorageException {
    boolean anyRemoved = false;
    for (Key key : removedData.keySet()) {
      changesProcessor.removed(key, inputId);
      anyRemoved = true;
    }
    return anyRemoved;
  }
}
