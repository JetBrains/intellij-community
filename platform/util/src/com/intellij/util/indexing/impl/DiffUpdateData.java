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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.StorageException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DiffUpdateData<Key, Value> extends UpdateData<Key,Value> {
  public static final boolean ourDiffUpdateEnabled = SystemProperties.getBooleanProperty("idea.disable.diff.index.update", true);

  public DiffUpdateData(@NotNull Map<Key, Value> newData,
                        @NotNull ThrowableComputable<ForwardIndex.InputKeyIterator<Key, Value>, IOException> currentData,
                        @NotNull ID<Key, Value> indexId, ThrowableRunnable<IOException> forwardIndexUpdate) {
    super(newData, currentData, indexId, forwardIndexUpdate);
  }

  @Override
  public void iterateKeys(int inputId,
                          KeyValueUpdateProcessor<Key, Value> addProcessor,
                          KeyValueUpdateProcessor<Key, Value> updateProcessor,
                          RemovedKeyProcessor<Key> removeProcessor) throws StorageException {
    final Set<Key> processedKeys = new THashSet<Key>();
    int oldSize = 0; //kept for debug reasons
    int addedKeys = 0;
    int removedKeys = 0;
    boolean newDataIsEmpty = myNewData.isEmpty();
    final ForwardIndex.InputKeyIterator<Key, Value> currentData;
    try {
      currentData = myCurrentData.compute();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    while (currentData.hasNext()) {
      oldSize++;
      Key key = currentData.next();
      if (!newDataIsEmpty) {
        processedKeys.add(key);
      }
      if (newDataIsEmpty || !myNewData.containsKey(key)) {
        removeProcessor.process(key, inputId);
        removedKeys++;
      } else {
        Value newValue = myNewData.get(key);
        if (!currentData.isAssociatedValueEqual(newValue)) {
          updateProcessor.process(key, newValue, inputId);
          removedKeys++;
          addedKeys++;
        }
      }
    }

    if (!newDataIsEmpty) {
      for (Map.Entry<Key, Value> entry : myNewData.entrySet()) {
        if (!processedKeys.contains(entry.getKey())) {
          addProcessor.process(entry.getKey(), entry.getValue(), inputId);
          addedKeys++;
        }
      }
    }

    int totalRequests = requests.incrementAndGet();
    totalRemovals.addAndGet(oldSize);
    totalAdditions.addAndGet(myNewData.size());
    incrementalAdditions.addAndGet(removedKeys);
    incrementalRemovals.addAndGet(addedKeys);

    if ((totalRequests & 0xFFF) == 0 && DebugAssertions.DEBUG) {
      Logger.getInstance(getClass()).info("Incremental index diff update:"+requests +
                                          ", removals:" + totalRemovals + "->" + incrementalRemovals +
                                          ", additions:" +totalAdditions + "->" +incrementalAdditions);
    }
  }

  private static final AtomicInteger requests = new AtomicInteger();
  private static final AtomicInteger totalRemovals = new AtomicInteger();
  private static final AtomicInteger totalAdditions = new AtomicInteger();
  private static final AtomicInteger incrementalRemovals = new AtomicInteger();
  private static final AtomicInteger incrementalAdditions = new AtomicInteger();

  @NotNull
  protected Map<Key, Value> getMap() {
    return myNewData;
  }
}
