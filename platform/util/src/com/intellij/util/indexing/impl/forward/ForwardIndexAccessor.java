// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface ForwardIndexAccessor<Key, Value> {
  /**
   * creates a diff builder for given inputId.
   */
  @NotNull
  InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence sequence) throws IOException;

  /**
   * serialize indexed data to forward index format.
   */
  @Nullable
  ByteArraySequence serializeIndexedData(@NotNull InputData<Key, Value> data) throws IOException;
}