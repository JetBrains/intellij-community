// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface IntForwardIndexAccessor<Key, Value> extends ForwardIndexAccessor<Key, Value> {
  @Override
  default @NotNull InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence sequence) throws IOException {
    return getDiffBuilderFromInt(inputId, sequence == null ? 0 : AbstractForwardIndexAccessor.deserializeFromByteSeq(sequence, EnumeratorIntegerDescriptor.INSTANCE));
  }

  @Override
  default @Nullable ByteArraySequence serializeIndexedData(@NotNull InputData<Key, Value> data) throws IOException {
    return AbstractForwardIndexAccessor.serializeValueToByteSeq(serializeIndexedDataToInt(data), EnumeratorIntegerDescriptor.INSTANCE, 8);
  }

  /**
   * creates a diff builder for given inputId.
   */
  @NotNull
  InputDataDiffBuilder<Key, Value> getDiffBuilderFromInt(int inputId, int value) throws IOException;

  int serializeIndexedDataToInt(@NotNull InputData<Key, Value> data);
}
