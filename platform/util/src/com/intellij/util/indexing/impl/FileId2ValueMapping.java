// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
