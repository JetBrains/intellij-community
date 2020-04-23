// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapInputDataDiffBuilder;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractMapForwardIndexAccessor<Key, Value, DataType> extends AbstractForwardIndexAccessor<Key, Value, DataType> {
  public AbstractMapForwardIndexAccessor(@NotNull DataExternalizer<DataType> externalizer) {
    super(externalizer);
  }

  @Override
  protected final InputDataDiffBuilder<Key, Value> createDiffBuilder(int inputId, @Nullable DataType inputData) throws IOException {
    Map<Key, Value> map = convertToMap(inputId, inputData);
    return createDiffBuilderByMap(inputId, map);
  }

  @NotNull
  public InputDataDiffBuilder<Key, Value> createDiffBuilderByMap(int inputId, @Nullable Map<Key, Value> map) throws IOException {
    return new MapInputDataDiffBuilder<>(inputId, map);
  }

  @Nullable
  protected abstract Map<Key, Value> convertToMap(int inputId, @Nullable DataType inputData) throws IOException;

  @Nullable
  public Map<Key, Value> convertToInputDataMap(int inputId, @Nullable ByteArraySequence sequence) throws IOException {
    return convertToMap(inputId, deserializeData(sequence));
  }
}
