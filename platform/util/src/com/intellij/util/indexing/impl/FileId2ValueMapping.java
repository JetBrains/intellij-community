// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

final class FileId2ValueMapping<Value> {
  private final @NotNull Int2ObjectMap<Object> id2ValueMap = new Int2ObjectOpenHashMap<>();
  private final @NotNull ValueContainerImpl<Value> valueContainer;

  FileId2ValueMapping(@NotNull ValueContainerImpl<Value> valueContainer) {
    this.valueContainer = valueContainer;

    valueContainer.forEach((id, value) -> {
      Object previousValue = associateFileIdToValueSkippingContainer(id, value);
      if (previousValue != null) {
        ValueContainerImpl.LOG.error("Duplicated value for id = " + id + " in " + valueContainer.getDebugMessage());
      }
      return true;
    });
  }
  @SuppressWarnings("unchecked")
  void associateFileIdToValue(int fileId, Value value) {
    Object previousValue = associateFileIdToValueSkippingContainer(fileId, value);
    if (previousValue != null) {
      valueContainer.removeValue(fileId, ValueContainerImpl.unwrap((Value)previousValue));
    }
    valueContainer.addValue(fileId, value);
  }
  Object associateFileIdToValueSkippingContainer(int fileId, Value value) {
    return id2ValueMap.put(fileId, value);
  }

  @SuppressWarnings("unchecked")
  boolean removeFileId(int inputId) {
    Object mapped = id2ValueMap.remove(inputId);
    if (mapped != null) {
      valueContainer.removeValue(inputId, ValueContainerImpl.unwrap((Value)mapped));
    }
    return mapped != null;
  }
}
