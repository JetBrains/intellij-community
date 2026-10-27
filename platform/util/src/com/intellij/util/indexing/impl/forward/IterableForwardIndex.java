// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.IntPredicate;

/// Gives access to forward index keys, filtered by input IDs;
/// Optional capability of [ForwardIndex] implementations, used in some optimizations.
@Internal
public interface IterableForwardIndex extends ForwardIndex {
  /// Iterates through keys, there inputId matches `inputIdFilter`.
  /// Keys can repeat many times across different inputIds: the caller must remove duplicates if needed.
  ///
  /// @return true if all keys were processed, false if the processor stopped the traversal
  <Key> boolean processFilteredKeys(@NotNull IntPredicate inputIdFilter,
                                    @NotNull AbstractMapForwardIndexAccessor<Key, ?, ?> accessor,
                                    @NotNull Processor<? super Key> processor) throws IOException;
}
