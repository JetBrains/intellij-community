// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages;

import org.jetbrains.annotations.ApiStatus;

/// Calculates the optimal content size for newly allocated blocks, based on total size of already allocated blocks
@ApiStatus.Internal
@FunctionalInterface
public interface BlockSizingPolicy {
  /// @return preferred content size for the next block (in bytes), based on total size of already allocated blocks
  int preferredBlockSize(long liveCapacityBytes);

  /// Keeps the block content size fixed
  @ApiStatus.Internal
  record FixedSize(int fixedSizeBytes) implements BlockSizingPolicy {
    @Override
    public int preferredBlockSize(long liveCapacityBytes) {
      return fixedSizeBytes;
    }
  }
}
