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

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public final class UpdateData<Key, Value> extends AbstractUpdateData<Key, Value> {
  private final Map<Key, Value> myNewData;
  private final @NotNull ThrowableComputable<? extends InputDataDiffBuilder<Key, Value>, IOException> myCurrentDataEvaluator;
  private final IndexId<Key, Value> myIndexId;
  private final ThrowableRunnable<? extends IOException> myForwardIndexUpdate;

  public UpdateData(int inputId,
                    @NotNull Map<Key, Value> newData,
                    @NotNull ThrowableComputable<? extends InputDataDiffBuilder<Key, Value>, IOException> currentDataEvaluator,
                    @NotNull IndexId<Key, Value> indexId,
                    @Nullable ThrowableRunnable<? extends IOException> forwardIndexUpdate) {
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
    if (myForwardIndexUpdate != null) {
      myForwardIndexUpdate.run();
    }
  }

  @Override
  public String toString() {
    return "update data for " + getInputId() + " of " + myIndexId;
  }
}
