// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Class encapsulates index update with single input (usually, file) data.
 * Index has 2 parts: forward and inverted index, so the update process has 2 phases:
 * {@link #iterateKeys(KeyValueUpdateProcessor, KeyValueUpdateProcessor, RemovedKeyProcessor)}
 * is for updating inverted index, and {@link #updateForwardIndex()} is for updating forward index.
 * <p>
 * Inverted index update (a first phase) is more complicated than forward index update because forward index is
 * just [inputId -> Map[Key,Value]], so the update is just replacing an old map with a new one. But inverted
 * index update could require set of updates, (potentially) for each [Key] in the map -- so instead of a simple
 * updateInvertedIndex() it is designed as callback-based {@link #iterateKeys(KeyValueUpdateProcessor, KeyValueUpdateProcessor, RemovedKeyProcessor)},
 * which evaluates a diff between current and new data, and report pieces of the diff into appropriate processors.
 */
@ApiStatus.Internal
public abstract class AbstractUpdateData<Key, Value> {
  //MAYBE RC: split this class into 2 interfaces, ForwardIndexUpdate & InvertedIndexUpdate?
  private final int inputId;

  protected AbstractUpdateData(int inputId) { this.inputId = inputId; }

  public int getInputId() {
    return inputId;
  }

  //MAYBE RC: iterateDifferences() is more clear name?..
  /** @return true if new data is different from current data -- which means at least one processor _was_ called */
  protected abstract boolean iterateKeys(@NotNull KeyValueUpdateProcessor<? super Key, ? super Value> addProcessor,
                                         @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updateProcessor,
                                         @NotNull RemovedKeyProcessor<? super Key> removeProcessor) throws StorageException;

  protected void updateForwardIndex() throws IOException { }
}
