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

import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

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
                               @NotNull final KeyValueUpdateProcessor<? super Key, ? super Value> addProcessor,
                               @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updateProcessor,
                               @NotNull RemovedKeyProcessor<? super Key> removeProcessor) throws StorageException {
    return processAllKeyValuesAsAdded(myInputId, newData, addProcessor);
  }

  public static <Key, Value> boolean processAllKeyValuesAsAdded(int inputId,
                                                                @NotNull Map<Key, Value> addedData,
                                                                @NotNull final KeyValueUpdateProcessor<? super Key, ? super Value> addProcessor) throws StorageException {
    boolean anyAdded = false;
    for (Map.Entry<Key, Value> entry : addedData.entrySet()) {
      addProcessor.process(entry.getKey(), entry.getValue(), inputId);
      anyAdded = true;
    }

    return anyAdded;
  }

  public static <Key, Value> boolean processAllKeyValuesAsRemoved(int inputId,
                                                                  @NotNull Map<Key, Value> removedData,
                                                                  @NotNull RemovedKeyProcessor<? super Key> removedProcessor) throws StorageException {
    boolean anyRemoved = false;
    for (Key key : removedData.keySet()) {
      removedProcessor.process(key, inputId);
      anyRemoved = true;
    }
    return anyRemoved;
  }
}
