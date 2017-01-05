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
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public class SimpleUpdateData<Key, Value> extends UpdateData<Key,Value> {
  public SimpleUpdateData(@NotNull Map<Key, Value> newData,
                          @NotNull ThrowableComputable<ForwardIndex.InputKeyIterator<Key, Value>, IOException> currentData,
                          @NotNull ID<Key, Value> indexId,
                          ThrowableRunnable<IOException> forwardIndexUpdate) {
    super(newData, currentData, indexId, forwardIndexUpdate);
  }

  @Override
  public void iterateKeys(int inputId,
                          KeyValueUpdateProcessor<Key, Value> addProcessor,
                          KeyValueUpdateProcessor<Key, Value> updateProcessor,
                          RemovedKeyProcessor<Key> removeProcessor) throws StorageException {
    final ForwardIndex.InputKeyIterator<Key, Value> currentData;
    try {
      currentData = myCurrentData.compute();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    iterateKeys(inputId, addProcessor, removeProcessor, currentData);
  }

  protected void iterateKeys(int inputId,
                             KeyValueUpdateProcessor<Key, Value> addProcessor,
                             RemovedKeyProcessor<Key> removeProcessor, ForwardIndex.InputKeyIterator<Key, Value> currentData)
    throws StorageException {
    while (currentData.hasNext()) {
      removeProcessor.process(currentData.next(), inputId);
    }
    for (Map.Entry<Key, Value> entry : myNewData.entrySet()) {
      addProcessor.process(entry.getKey(), entry.getValue(), inputId);
    }
  }
}
