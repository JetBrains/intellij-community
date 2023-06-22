// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MapForwardIndexAccessor<Key, Value> extends AbstractMapForwardIndexAccessor<Key, Value, Map<Key, Value>> {
  public MapForwardIndexAccessor(@NotNull DataExternalizer<Map<Key, Value>> externalizer) {
    super(externalizer);
  }

  @Override
  protected @Nullable Map<Key, Value> convertToMap(int inputId, @Nullable Map<Key, Value> inputData) {
    return inputData;
  }

  @Override
  protected int getBufferInitialSize(@NotNull Map<Key, Value> map) {
    return 4 * map.size();
  }
  @Override
  public @Nullable Map<Key, Value> convertToDataType(@NotNull InputData<Key, Value> data) {
    Map<Key, Value> map = data.getKeyValues();
    return map.isEmpty() ? null : map;
  }
}
