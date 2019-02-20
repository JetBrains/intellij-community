// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Experimental
public interface KeysProviderForwardIndex<Key, Value> extends InputDataProviderForwardIndex<Key, Value, Collection<Key>> {
  @Override
  @NotNull
  default InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable Collection<Key> keys) {
    return new CollectionInputDataDiffBuilder<>(inputId, keys);
  }
}
