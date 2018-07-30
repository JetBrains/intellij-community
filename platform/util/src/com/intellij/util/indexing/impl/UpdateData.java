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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

@ApiStatus.Experimental
public class UpdateData<Key, Value> {
  private final Map<Key, Value> myNewData;
  private final ThrowableComputable<InputDataDiffBuilder<Key, Value>, IOException> myCurrentDataEvaluator;
  private final IndexId<Key, Value> myIndexId;
  private final ThrowableRunnable<IOException> myForwardIndexUpdate;

  public UpdateData(@NotNull Map<Key, Value> newData,
                    @NotNull ThrowableComputable<InputDataDiffBuilder<Key, Value>, IOException> currentDataEvaluator,
                    @NotNull IndexId<Key, Value> indexId,
                    @Nullable ThrowableRunnable<IOException> forwardIndexUpdate) {
    myNewData = newData;
    myCurrentDataEvaluator = currentDataEvaluator;
    myIndexId = indexId;
    myForwardIndexUpdate = forwardIndexUpdate;
  }

  boolean iterateKeys(@NotNull KeyValueUpdateProcessor<Key, Value> addProcessor,
                      @NotNull KeyValueUpdateProcessor<Key, Value> updateProcessor,
                      @NotNull RemovedKeyProcessor<Key> removeProcessor) throws StorageException {
    final InputDataDiffBuilder<Key, Value> currentData;
    try {
      currentData = getCurrentDataEvaluator().compute();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    return currentData.differentiate(myNewData, addProcessor, updateProcessor, removeProcessor);
  }

  @NotNull
  protected ThrowableComputable<InputDataDiffBuilder<Key, Value>, IOException> getCurrentDataEvaluator() {
    return myCurrentDataEvaluator;
  }

  @NotNull
  protected Map<Key, Value> getNewData() {
    return myNewData;
  }

  @NotNull
  public IndexId<Key, Value> getIndexId() {
    return myIndexId;
  }

  void updateForwardIndex() throws IOException {
    if (myForwardIndexUpdate != null) {
      myForwardIndexUpdate.run();
    }
  }

  @Override
  public String toString() {
    return myIndexId + "," + getClass().getName();
  }
}
