// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.UpdatedEntryProcessor;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

@Internal
public interface ForwardIndexAccessor<Key, Value> {
  /**
   * Method deserializes sequence bytes back into a {@link InputData}, and creates a diff-builder from this input
   * (with id=inputId).
   * The diff-builder could be used later to calculate a diff between that inputData, and any other inputData
   * (see {@link InputDataDiffBuilder#differentiate(Map, UpdatedEntryProcessor)})
   */
  @NotNull
  InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence sequence) throws IOException;

  /** serialize indexed data to forward index format. */
  @Nullable
  ByteArraySequence serializeIndexedData(@NotNull InputData<Key, Value> data) throws IOException;
}