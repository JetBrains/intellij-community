// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.io.ByteArraySequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@ApiStatus.Experimental
public interface ForwardIndexAccessor<Key, Value, Input> {
  default InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence data, @Nullable Input input) throws IOException {
    return new CollectionInputDataDiffBuilder<>(inputId, getKeys(data));
  }

  @Nullable
  Collection<Key> getKeys(ByteArraySequence data) throws IOException;

  @Nullable
  ByteArraySequence serialize(@Nullable Map<Key, Value> map, @Nullable Input input) throws IOException;
}
