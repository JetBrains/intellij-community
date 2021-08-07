// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * A main interface to provide custom inverted index implementation.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public interface IndexStorageLayout<Key, Value> {
  @NotNull IndexStorage<Key, Value> openIndexStorage() throws IOException;

  default @Nullable ForwardIndex openForwardIndex() throws IOException {
    return null;
  }

  default @Nullable ForwardIndexAccessor<Key, Value> getForwardIndexAccessor() throws IOException {
    return null;
  }
}
