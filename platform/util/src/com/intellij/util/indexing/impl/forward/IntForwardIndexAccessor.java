// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public interface IntForwardIndexAccessor<Key, Value, Input> extends ForwardIndexAccessor<Key, Value, Integer, Input> {
  @NotNull
  @Override
  default InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence sequence) throws IOException {
    return getDiffBuilderFromInt(inputId, sequence == null ? 0 : AbstractForwardIndexAccessor.deserializeFromByteSeq(sequence, EnumeratorIntegerDescriptor.INSTANCE));
  }

  @Override
  default Integer convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input content) {
    return convertToInt(map, content);
  }

  @Nullable
  @Override
  default ByteArraySequence serializeIndexedData(@Nullable Integer data) throws IOException {
    return data == null ? null : AbstractForwardIndexAccessor.serializeToByteSeq(data, EnumeratorIntegerDescriptor.INSTANCE, 8);
  }

  /**
   * creates a diff builder for given inputId.
   */
  @NotNull
  InputDataDiffBuilder<Key, Value> getDiffBuilderFromInt(int inputId, int value) throws IOException;

  /**
   * convert mapped key-values and input to a data type before it will be serialized
   */
  int convertToInt(@Nullable Map<Key, Value> map, @Nullable Input content);
}
