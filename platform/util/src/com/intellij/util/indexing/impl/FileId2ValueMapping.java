// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Helper class: optimizes applying many modifications (add/remove inputId) to {@link ValueContainerImpl}.
 * <br/>
 * Adding/removing a record from {@link ValueContainerImpl} by inputId is O(size). This is
 * because even though logically {@link ValueContainerImpl} is a set of (inputId -> Value)
 * mappings, but implementation stores data as (Value-> Set of inputIds) -- hence to remove
 * an entry for given inputId one basically has to scan through ~half the container.
 * <br/>
 * If the container is big enough, it is much faster to build a kind of 'index' over it, and
 * apply changes via that index instead. This class is doing exactly this: it wraps around
 * {@link ValueContainerImpl}, creates an 'index' over its content ({@link #id2ValueMap}),
 * and uses the index to apply changes faster.
 */
final class FileId2ValueMapping<Value> {
  private final @NotNull Int2ObjectMap<Value> id2ValueMap = new Int2ObjectOpenHashMap<>();
  private final @NotNull ValueContainerImpl<Value> valueContainer;

  FileId2ValueMapping(@NotNull ValueContainerImpl<Value> valueContainer) {
    this.valueContainer = valueContainer;

    List<ObjectIntImmutablePair<Value>> cleanupDeletions = new SmartList<>();

    valueContainer.forEach((id, value) -> {
      Value previousValue = associateFileIdToValueSkippingContainer(id, value);
      if (previousValue != null) {
        cleanupDeletions.add(ObjectIntImmutablePair.of(previousValue, id));
        //ValueContainerImpl.LOG.error("Duplicated value for id = " + id + " in " + valueContainer.getDebugMessage());
      }
      return true;
    });

    for (ObjectIntImmutablePair<Value> deletion : cleanupDeletions) {
      valueContainer.removeValue(deletion.rightInt(), ValueContainerImpl.unwrap(deletion.left()));
    }
  }

  void associateFileIdToValue(int fileId, Value value) {
    Value previousValue = associateFileIdToValueSkippingContainer(fileId, value);
    if (previousValue != null) {
      valueContainer.removeValue(fileId, ValueContainerImpl.unwrap(previousValue));
    }
    valueContainer.addValue(fileId, value);
  }

  Value associateFileIdToValueSkippingContainer(int fileId, Value value) {
    return id2ValueMap.put(fileId, ValueContainerImpl.wrapValue(value));
  }

  boolean removeFileId(int inputId) {
    Value mapped = id2ValueMap.remove(inputId);
    if (mapped != null) {
      valueContainer.removeValue(inputId, ValueContainerImpl.unwrap(mapped));
    }
    return mapped != null;
  }
}
