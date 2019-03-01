// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public abstract class AbstractMapProviderForwardIndexAccessor<Key, Value, Data, Input> extends AbstractForwardIndexAccessor<Key, Value, Data, Input>  {
  protected AbstractMapProviderForwardIndexAccessor(@NotNull DataExternalizer<Data> externalizer) {
    super(externalizer);
  }

  @Nullable
  public abstract Map<Key, Value> getMapFromData(@Nullable Data data) throws IOException;

  @Nullable
  @Override
  protected Collection<Key> getKeysFromData(@Nullable Data data) throws IOException {
    Map<Key, Value> map = getMapFromData(data);
    return map == null ? null : map.keySet();
  }

  @Override
  public InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence data, @Nullable Input input) throws IOException {
    return new MapInputDataDiffBuilder<>(inputId, getMapFromData(getData(data)));
  }
}
