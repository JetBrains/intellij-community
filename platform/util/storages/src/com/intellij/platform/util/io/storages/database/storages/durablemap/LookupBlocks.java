// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.List;

/// Provides access _only_ to the blocks that belong to one durable map lookup.
/// (e.g. hides allocation of non-LOOKUP blocks, or dropping the owning store)
@ApiStatus.Internal
public interface LookupBlocks {
  /// @return lookup blocks available in this scope
  @NotNull List<Block> blocks();

  /// Allocates a lookup block, initializes its common header, and adds it to this scope.
  /// The returned block remains [BlocksStore.Block.LifecycleState#ALLOCATED] until the provider initializes its payload.
  @NotNull Block allocate(int minimumPayloadLength) throws IOException;

  /// Retires all blocks in this scope.
  void retireAll();

  /// Flushes all completed changes in the owning store.
  void flush() throws IOException;

  /// A lookup block that exposes only provider-owned payload.
  interface Block {
    int id();

    @NotNull BlocksStore.Block.LifecycleState state();

    @NotNull MemorySegment payload() throws IOException;

    /// Publishes the block after the lookup provider initializes its payload.
    void activate();

    /// Discards the block when the lookup provider cannot initialize its payload.
    void discard();

    void seal();

    void retire();
  }
}
