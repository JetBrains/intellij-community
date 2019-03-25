// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MapForwardIndexAccessor<Key, Value, Input> extends AbstractMapForwardIndexAccessor<Key, Value, Map<Key, Value>, Input> {
  public MapForwardIndexAccessor(@NotNull DataExternalizer<Map<Key, Value>> externalizer) {
    super(externalizer);
  }

  @Nullable
  @Override
  protected Map<Key, Value> convertToMap(@Nullable Map<Key, Value> inputData) {
    return inputData;
  }

  @Override
  protected int getBufferInitialSize(@NotNull Map<Key, Value> map) {
    return 4 * map.size();
  }

  @Override
  public Map<Key, Value> convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input content) {
    return map;
  }
}
