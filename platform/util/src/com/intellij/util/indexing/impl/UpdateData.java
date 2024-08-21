// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

/**
 * Class encapsulates index update from a single input (=usually, file) change.
 * <p>
 * The update has 2 sides: the new data itself, as a whole, and changes between the new data and the current data.
 * <p>
 * The update as a whole is going to the forward index, while the changes are going to the inverted index.
 * So the {@link #updateForwardIndex()} is to update the forward index with the new data, and
 * {@link #iterateKeys(KeyValueUpdateProcessor, KeyValueUpdateProcessor, RemovedKeyProcessor)} is to stream
 * the changes against current data to apply on the inverted index.
 */
@ApiStatus.Internal
public final class UpdateData<Key, Value>/* implements UpdateData.ChangesProducer<Key, Value> */ {

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

  //MAYBE RC: iterateChanges() is more clear name?..
  /** @return true if new data is different from current data -- which means at least one processor _was_ called */
  boolean iterateKeys(@NotNull KeyValueUpdateProcessor<? super Key, ? super Value> addedEntriesProcessor,
                      @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updatedEntriesProcessor,
                      @NotNull RemovedKeyProcessor<? super Key> removedEntriesProcessor) throws StorageException {
    return changesProducer.forEachChange(
      addedEntriesProcessor,
      updatedEntriesProcessor,
      removedEntriesProcessor
    );
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

  /**
   * It is basically an inverse-index update (pair to {@link ForwardIndexUpdate}), but designed in a more complex way:
   * it exposes the set of updates to be applied to inverted index, but via callbacks
   */
  @ApiStatus.Internal
  @FunctionalInterface
  public interface ChangesProducer<Key, Value> {
    boolean forEachChange(@NotNull KeyValueUpdateProcessor<? super Key, ? super Value> addedEntriesProcessor,
                          @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updatedEntriesProcessor,
                          @NotNull RemovedKeyProcessor<? super Key> removedEntriesProcessor) throws StorageException;

    class LazyChangesProducer<Key, Value> implements ChangesProducer<Key, Value> {
      private final @NotNull Map<Key, Value> newData;
      private final @NotNull ThrowableComputable<? extends InputDataDiffBuilder<Key, Value>, ? extends IOException> currentDataEvaluator;

      public LazyChangesProducer(@NotNull Map<Key, Value> newData,
                                 @NotNull ThrowableComputable<? extends InputDataDiffBuilder<Key, Value>, ? extends IOException> currentDataEvaluator) {
        this.newData = newData;
        this.currentDataEvaluator = currentDataEvaluator;
      }

      @Override
      public boolean forEachChange(@NotNull KeyValueUpdateProcessor<? super Key, ? super Value> addedEntriesProcessor,
                                   @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updatedEntriesProcessor,
                                   @NotNull RemovedKeyProcessor<? super Key> removedEntriesProcessor) throws StorageException {
        try {
          InputDataDiffBuilder<Key, Value> diffBuilderAgainstCurrentData = currentDataEvaluator.compute();
          return diffBuilderAgainstCurrentData.differentiate(
            newData,
            addedEntriesProcessor,
            updatedEntriesProcessor,
            removedEntriesProcessor
          );
        }
        catch (IOException e) {
          throw new StorageException("Error while applying " + this, e);
        }
      }
    }
  }
}
