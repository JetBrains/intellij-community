// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Class encapsulates index update from a single input (=usually, file) change.
 * <p>
 * The update has 2 sides: the new data itself, as a whole, and changes between the new data and the current data.
 * <p>
 * The update as a whole is going to the forward index, while the changes are going to the inverted index.
 * So the {@link #updateForwardIndex()} is to update the forward index with the new data, and
 * {@link #iterateChanges(UpdatedEntryProcessor)} is to stream
 * the changes against current data to apply on the inverted index.
 * <p>
 * The 2 sides are separated to allow indexes to skip forward index update, and/or to provide an optimized version
 * of changes evaluation.
 */
@ApiStatus.Internal
public final class UpdateData<Key, Value> {

  private final int inputId;
  private final @NotNull IndexId<Key, Value> indexId;

  private final @NotNull ChangesProducer<Key, Value> changesProducer;
  private final @NotNull ForwardIndexUpdate forwardIndexUpdate;

  public UpdateData(int inputId,
                    @NotNull IndexId<Key, Value> indexId,
                    @NotNull ChangesProducer<Key, Value> producer,
                    @NotNull ForwardIndexUpdate update) {
    this.inputId = inputId;
    this.indexId = indexId;
    this.changesProducer = producer;
    this.forwardIndexUpdate = update;
  }

  public int inputId() {
    return inputId;
  }

  //MAYBE RC: move ChangesProducer to the upper level, and make UpdateData implement ChangesProducer, so this
  //          method become .forEachChange()?
  /** @return true if new data is different from current data -- which means at least one processor _was_ called */
  boolean iterateChanges(@NotNull UpdatedEntryProcessor<? super Key, ? super Value> changedEntriesProcessor) throws StorageException {
    return changesProducer.forEachChange(changedEntriesProcessor);
  }

  void updateForwardIndex() throws IOException {
    forwardIndexUpdate.update();
  }

  @Override
  public String toString() {
    return "UpdateData[" + indexId + ": #" + inputId + "]";
  }



  @ApiStatus.Internal
  @FunctionalInterface
  public interface ForwardIndexUpdate {
    void update() throws IOException;


    /** Anonymous class instead of lambda for better readability during debugging*/
    ForwardIndexUpdate NOOP = new ForwardIndexUpdate() {
      @Override
      public void update() { }

      @Override
      public String toString() {
        return "NO_OP";
      }
    };
  }

  /** Set of updates to be applied to inverted index: entries added, entries removed, entries changed -- but via callbacks */
  @ApiStatus.Internal
  @FunctionalInterface
  public interface ChangesProducer<Key, Value> {
    boolean forEachChange(@NotNull UpdatedEntryProcessor<? super Key, ? super Value> changedEntriesProcessor) throws StorageException;
  }
}
