// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.appenablerecords;

import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/// Provides blocks owned by one multi-block appendable record storage
@ApiStatus.Internal
public interface AppendableRecordBlocks {
  /// Blocks can become retired after this method returns
  /// @return an immutable snapshot of non-retired blocks in allocation order
  @NotNull List<BlocksStore.Block> blocksInAllocationOrder();

  /// Allocates a block, adds it to the end of the allocation order, and returns it in the `ALLOCATED` state.
  @NotNull BlocksStore.Block allocateBlock(int minimumContentLength) throws IOException;
}
