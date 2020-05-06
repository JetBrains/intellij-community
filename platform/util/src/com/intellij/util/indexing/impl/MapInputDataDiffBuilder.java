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

import com.intellij.openapi.util.Comparing;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public final class MapInputDataDiffBuilder<Key, Value> extends DirectInputDataDiffBuilder<Key, Value> {
  @NotNull
  private final Map<Key, Value> myMap;

  public MapInputDataDiffBuilder(int inputId, @Nullable Map<Key, Value> map) {
    super(inputId);
    myMap = map == null ? Collections.emptyMap() : map;
  }

  @Override
  public boolean differentiate(@NotNull Map<Key, Value> newData,
                               @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> addProcessor,
                               @NotNull KeyValueUpdateProcessor<? super Key, ? super Value> updateProcessor,
                               @NotNull RemovedKeyProcessor<? super Key> removeProcessor) throws StorageException {
    if (myMap.isEmpty()) {
      return EmptyInputDataDiffBuilder.processAllKeyValuesAsAdded(myInputId, newData, addProcessor);
    }
    if (newData.isEmpty()) {
      return EmptyInputDataDiffBuilder.processAllKeyValuesAsRemoved(myInputId, newData, removeProcessor);
    }

    int added = 0;
    int removed = 0;
    int updated = 0;

    for (Map.Entry<Key, Value> e : myMap.entrySet()) {
      Key key = e.getKey();
      Value oldValue = e.getValue();
      Value newValue = newData.get(key);
      if (!Comparing.equal(oldValue, newValue) || (newValue == null && !newData.containsKey(key))) {
        if (newData.containsKey(key)) {
          updateProcessor.process(key, newValue, myInputId);
          updated++;
        }
        else {
          removeProcessor.process(key, myInputId);
          removed++;
        }
      }
    }

    for (Map.Entry<Key, Value> e : newData.entrySet()) {
      final Key newKey = e.getKey();
      if (!myMap.containsKey(newKey)) {
        addProcessor.process(newKey, e.getValue(), myInputId);
        added++;
      }
    }
    return added != 0 || removed != 0 || updated != 0;
  }

  @NotNull
  @Override
  public Collection<Key> getKeys() {
    return myMap.keySet();
  }
}
