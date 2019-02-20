// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@ApiStatus.Experimental
public interface InputDataProviderForwardIndex<Key, Value, MapValueType> extends ForwardIndex<Key, Value> {
  @Nullable
  MapValueType getInput(int inputId) throws IOException;

  @NotNull
  @Override
  default InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId) throws IOException {
    return getDiffBuilder(inputId, getInput(inputId));
  }

  InputDataDiffBuilder<Key,Value> getDiffBuilder(int inputId, MapValueType input);
}
