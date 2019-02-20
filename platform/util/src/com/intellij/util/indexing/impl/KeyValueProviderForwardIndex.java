// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@ApiStatus.Experimental
public interface KeyValueProviderForwardIndex<Key, Value> extends InputDataProviderForwardIndex<Key, Value, Map<Key, Value>> {
  @Override
  @NotNull
  default InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable Map<Key, Value> map) {
    return new MapInputDataDiffBuilder<>(inputId, map);
  }
}