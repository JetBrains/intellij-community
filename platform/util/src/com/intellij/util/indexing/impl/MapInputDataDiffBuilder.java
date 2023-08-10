// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MapInputDataDiffBuilder<Key, Value> extends DirectInputDataDiffBuilder<Key, Value> {
  private final @NotNull Map<Key, Value> myMap;

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
      return EmptyInputDataDiffBuilder.processAllKeyValuesAsRemoved(myInputId, myMap, removeProcessor);
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

    if (IndexDebugProperties.DEBUG) {
      updateStatistics(added, removed, updated, myMap, newData);
    }

    return added != 0 || removed != 0 || updated != 0;
  }

  @Override
  public @NotNull Collection<Key> getKeys() {
    return myMap.keySet();
  }

  private static <Key, Value> void updateStatistics(int added,
                                                    int removed,
                                                    int updated,
                                                    @NotNull Map<Key, Value> oldData,
                                                    @NotNull Map<Key, Value> newData) {
    incrementalAdditions.addAndGet(added);
    incrementalRemovals.addAndGet(removed);
    totalRemovals.addAndGet(oldData.size());
    totalAdditions.addAndGet(newData.size());

    if (added == 0 && removed == 0 && updated == 0) {
      noopModifications.incrementAndGet();
    }

    int requests = totalRequests.incrementAndGet();
    if ((requests & 0xFFFF) == 0) {
      Logger.getInstance(MapInputDataDiffBuilder.class).info(
        "Incremental index diff update: " + requests +
        ", removals: " + totalRemovals + "->" + incrementalRemovals +
        ", additions: " + totalAdditions + "->" + incrementalAdditions +
        ", no op changes: " + noopModifications
      );
    }
  }

  private static final AtomicInteger totalRequests = new AtomicInteger();
  private static final AtomicInteger totalRemovals = new AtomicInteger();
  private static final AtomicInteger totalAdditions = new AtomicInteger();
  private static final AtomicInteger incrementalRemovals = new AtomicInteger();
  private static final AtomicInteger incrementalAdditions = new AtomicInteger();
  private static final AtomicInteger noopModifications = new AtomicInteger();
}
