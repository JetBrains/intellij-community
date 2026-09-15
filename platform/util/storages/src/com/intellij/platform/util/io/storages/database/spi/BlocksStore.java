// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.List;

/// SPI for a particular named store _instance_ to interact with Database:
/// - allocate blocks: persistent memory segments to store data into
/// - find already allocated blocks
/// - seal/retire blocks
///
/// The blocks accessible through this interface all belong to the specific named store instance.
/// @see BlocksDatabase#openStore(String, int)
/// @see BlocksDatabase#findStore(String)
@ApiStatus.Internal
public interface BlocksStore {
  /// @return the application data format version
  int dataVersion();

  /// @return all blocks that belong to this store, including retired ones
  @NotNull List<Block> blocks();

  /// @return the owned block, or `null` when the block does not exist
  @Nullable Block findBlock(int blockId);

  /// Allocates one block with at least the specified number of content bytes.
  /// The returned content could be slightly larger, e.g. it can include alignment padding.
  @NotNull Block allocateBlock(int role, int minimumContentLength) throws IOException;

  /// @return true when completed store changes can require a flush
  boolean isDirty();

  /// Flushes all completed changes. The database configuration controls fsync during this operation.
  void flush() throws IOException;

  /// Drops this store and retires all its blocks.
  /// Other store operations reject its old handles.
  /// Records the deletion before retiring blocks.
  /// It retires blocks even when forcing the deletion to persistent storage fails.
  /// An I/O failure can leave the deletion incomplete. Reopen the database to recover from its journal.
  void drop() throws IOException;

  /// A continuous memory region with an identifier, a role, and a lifecycle state
  interface Block {
    /// @return the stable block identifier
    int id();

    /// @return the opaque application role
    int role();

    /// Gets current block's lifecycle state;
    /// When the owning store is dropped from DB, it's blocks are all moved to [LifecycleState#RETIRED];
    /// When block's underlying chunk is unmapped, method throws [IllegalStateException];
    ///
    /// TODO RC: currently there is no way to clearly say when block could be unmmapped; it means that almost every access to
    ///          [state] must be ready to get [IllegalStateException], because it could be the accessed block gets retired,
    ///          and compaction unmaps the whole chunk. To protect against it we should probably introduce something like
    ///          a reader-leases, that 1) could be acquired only on non-retired blocks 2) leases > 0 prevents blocks from
    ///          being unmmapped. This should be done during compaction implementation
    ///
    ///
    /// @return the current lifecycle state;
    /// @throws IllegalStateException if the block's memory segment is no longer alive
    @NotNull LifecycleState state() throws IllegalStateException;

    /// @return the content of the block usable for the application
    /// The caller must stop writes before it seals the block. A previously returned segment cannot become read-only.
    @NotNull MemorySegment content();

    /// Marks the block as immutable after the application finishes all writes
    void seal();

    /// Marks the sealed block as unreachable data that compaction can reclaim
    void retire();

    /// The lifecycle state of a database block
    @ApiStatus.Internal
    enum LifecycleState {ACTIVE, SEALED, RETIRED}
  }
}
