// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 *
 * @param <DataType> should not hold strong reference to Input because it may lead to OOMs
 */
public interface ForwardIndexAccessor<Key, Value, DataType, Input> {
  /**
   * creates a diff builder for given inputId.
   */
  @NotNull
  InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence sequence) throws IOException;

  /**
   * convert mapped key-values and input to a data type before it will be serialized
   */
  DataType convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input content);

  /**
   * serialize indexed data to forward index format.
   */
  @Nullable
  ByteArraySequence serializeIndexedData(@Nullable DataType data) throws IOException;
}