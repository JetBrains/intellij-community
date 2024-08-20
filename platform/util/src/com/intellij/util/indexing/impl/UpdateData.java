// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

@ApiStatus.Internal
public final class UpdateData<Key, Value> extends AbstractUpdateData<Key, Value> {
  private final Map<Key, Value> myNewData;
  private final @NotNull ThrowableComputable<? extends InputDataDiffBuilder<Key, Value>, ? extends IOException> myCurrentDataEvaluator;
  private final IndexId<Key, Value> myIndexId;
  private final ThrowableRunnable<? extends IOException> myForwardIndexUpdate;

  public UpdateData(int inputId,
                    @NotNull Map<Key, Value> newData,
                    @NotNull ThrowableComputable<? extends InputDataDiffBuilder<Key, Value>, ? extends IOException> currentDataEvaluator,
                    @NotNull IndexId<Key, Value> indexId,
                    @NotNull ThrowableRunnable<? extends IOException> forwardIndexUpdate) {
    super(inputId);
    myNewData = newData;
    myCurrentDataEvaluator = currentDataEvaluator;
    myIndexId = indexId;
    myForwardIndexUpdate = forwardIndexUpdate;
  }

  @Override
  protected boolean iterateKeys(@NotNull KeyValueUpdateProcessor<? super Key, ? super Value> addProcessor,
                                @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updateProcessor,
                                @NotNull RemovedKeyProcessor<? super Key> removeProcessor) throws StorageException {
    final InputDataDiffBuilder<Key, Value> currentData;
    try {
      currentData = myCurrentDataEvaluator.compute();
    }
    catch (IOException e) {
      throw new StorageException("Error while applying " + this, e);
    }
    return currentData.differentiate(myNewData, addProcessor, updateProcessor, removeProcessor);
  }

  @Override
  protected void updateForwardIndex() throws IOException {
    myForwardIndexUpdate.run();
  }

  @Override
  public String toString() {
    return "update data for " + getInputId() + " of " + myIndexId;
  }
}
