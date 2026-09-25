// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.impl.layout.BlockHeaderLayout;
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

/// Block of data that belongs to a specific store [#storeId] and has a specific role [#role] inside that store.
/// Block has unique [#blockId] that is unchanged for whole block lifetime;
/// Block represents a logical unit of data -- while e.g. a [DatabaseChunk] represents a physical unit of allocation/compaction;
///
/// Block lifecycle states:
/// 1. [LifecycleState#ALLOCATED]: allocated but not yet usable -- a block-allocating site is still initializing it;
/// 2. [LifecycleState#ACTIVE]:    initialized and usable;
/// 3. [LifecycleState#SEALED]:    changes are prohibited, reads are allowed;
/// 4. [LifecycleState#RETIRED]:   marked for deletion/deleted -- generally, a block in this state **should not** be accessed
///    in any way, except for low-level database-internal code who knows that it is doing;
/// State transitions are always one-way: from ALLOCATED to RETIRED (which is a terminal state).
/// The normal transitions a one-step-at-a-time: ALLOCATED -> ACTIVE -> SEALED -> RETIRED, but there are few shortcuts:
/// - ALLOCATED -> RETIRED: if an allocation hasn't been finished (block hasn't been transitioned to ACTIVE) and an app is
///   crashed -- during recovery an unfinished ALLOCATED blocks are retired and discarded.
/// - (any state) -> RETIRED: dropping the owning store retires all the store's blocks, regardless of theirs states.
final class DatabaseBlock {
  static final int MIN_ROLE = 0;
  static final int MAX_ROLE = 0xFF;

  /// including header
  private final @NotNull MemorySegment blockSegment;
  /// excluding header
  private final @NotNull MemorySegment contentSegment;

  private final int storeId;
  private final int blockId;
  /// Opaque application tag, un-interpreted by DB
  private final int role;

  private final int chunkId;
  private final long blockOffsetInChunk;

  private DatabaseBlock(@NotNull MemorySegment blockSegment,
                        int chunkId,
                        long blockOffsetInChunk,
                        int blockId,
                        int storeId,
                        int role) {
    this.blockSegment = blockSegment;
    this.contentSegment = blockSegment.asSlice(BlockHeaderLayout.HEADER_SIZE);
    this.chunkId = chunkId;
    this.blockOffsetInChunk = blockOffsetInChunk;
    this.blockId = blockId;
    this.storeId = storeId;
    this.role = role;
  }

  static @NotNull DatabaseBlock create(@NotNull MemorySegment chunkSegment,
                                       int chunkId,
                                       long blockOffset,
                                       int blockId,
                                       int blockLength,
                                       int storeId,
                                       int role) throws IOException {
    validateAllocation(chunkSegment, chunkId, blockOffset, blockId, blockLength, storeId, role);
    var blockSegment = chunkSegment.asSlice(blockOffset, blockLength);
    BlockHeaderLayout.initialize(blockSegment, role, storeId, blockId, blockLength);
    return new DatabaseBlock(blockSegment, chunkId, blockOffset, blockId, storeId, role);
  }

  static @NotNull DatabaseBlock open(@NotNull Path chunkPath,
                                     @NotNull MemorySegment chunkSegment,
                                     int chunkId,
                                     long blockOffset,
                                     long committedLimit) throws IOException {
    if (!isBlockAligned(blockOffset) || blockOffset < ChunkHeaderLayout.HEADER_SIZE) {
      throw corrupted(chunkPath, blockOffset, "the block offset is not valid");
    }
    if (blockOffset + BlockHeaderLayout.HEADER_SIZE > committedLimit || committedLimit > chunkSegment.byteSize()) {
      throw corrupted(chunkPath, blockOffset, "the block header is outside the committed chunk prefix");
    }

    var headerSegment = chunkSegment.asSlice(blockOffset, BlockHeaderLayout.HEADER_SIZE);
    BlockHeaderLayout.validate(chunkPath, headerSegment, blockOffset);
    var blockId = BlockHeaderLayout.readBlockId(headerSegment);
    if (blockId <= 0) {
      throw corrupted(chunkPath, blockOffset, "blockId(=" + blockId + ") must be positive");
    }
    var storeId = BlockHeaderLayout.readStoreId(headerSegment);
    if (storeId <= 0) {
      throw corrupted(chunkPath, blockOffset, "storeId(=" + storeId + ") must be positive");
    }
    var role = BlockHeaderLayout.readRole(headerSegment);
    var blockLength = BlockHeaderLayout.readBlockLength(headerSegment);
    validateBlockLength(chunkPath, blockOffset, blockLength, committedLimit);

    var blockSegment = chunkSegment.asSlice(blockOffset, blockLength);
    return new DatabaseBlock(blockSegment, chunkId, blockOffset, blockId, storeId, role);
  }

  static void validateParameters(int blockId, int blockLength, int storeId, int role) {
    if (blockId <= 0) {
      throw new IllegalArgumentException("blockId(=" + blockId + ") must be positive");
    }
    if (storeId <= 0) {
      throw new IllegalArgumentException("storeId(=" + storeId + ") must be positive");
    }
    if (role < MIN_ROLE || role > MAX_ROLE) {
      throw new IllegalArgumentException("role(=" + role + ") must fit in an unsigned byte");
    }
    if (blockLength < BlockHeaderLayout.HEADER_SIZE || !isBlockAligned(blockLength)) {
      throw new IllegalArgumentException(
        "blockLength(=" + blockLength + ") must be at least " + BlockHeaderLayout.HEADER_SIZE +
        " and a multiple of " + BlockHeaderLayout.BLOCK_ALIGNMENT
      );
    }
  }

  /** @return an aligned physical block length for the requested content length */
  static int blockLengthForContent(int minimumContentLength) {
    if (minimumContentLength < 0) {
      throw new IllegalArgumentException("minimumContentLength(=" + minimumContentLength + ") must be non-negative");
    }
    var alignment = BlockHeaderLayout.BLOCK_ALIGNMENT;
    var alignedContentLength = ((long)minimumContentLength + alignment - 1) & -alignment;
    var blockLength = BlockHeaderLayout.HEADER_SIZE + alignedContentLength;
    if (blockLength > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("minimumContentLength(=" + minimumContentLength + ") is too large");
    }
    return Math.toIntExact(blockLength);
  }

  private static void validateAllocation(@NotNull MemorySegment chunkSegment,
                                         int chunkId,
                                         long blockOffset,
                                         int blockId,
                                         int blockLength,
                                         int storeId,
                                         int role) {
    if (chunkId <= 0) {
      throw new IllegalArgumentException("chunkId(=" + chunkId + ") must be positive");
    }
    if (!isBlockAligned(blockOffset) || blockOffset < ChunkHeaderLayout.HEADER_SIZE) {
      throw new IllegalArgumentException(
        "blockOffset(=" + blockOffset + ") must be " + BlockHeaderLayout.BLOCK_ALIGNMENT + "-byte aligned"
      );
    }
    validateParameters(blockId, blockLength, storeId, role);
    if (blockOffset + blockLength > chunkSegment.byteSize()) {
      throw new IllegalArgumentException("The block does not fit in the chunk");
    }
  }

  private static void validateBlockLength(@NotNull Path chunkPath,
                                          long blockOffset,
                                          int blockLength,
                                          long committedLimit) throws CorruptedException {
    if (blockLength < BlockHeaderLayout.HEADER_SIZE || !isBlockAligned(blockLength)) {
      throw corrupted(
        chunkPath,
        blockOffset,
        "blockLength(=" + blockLength + ") must be at least " + BlockHeaderLayout.HEADER_SIZE +
        " and a multiple of " + BlockHeaderLayout.BLOCK_ALIGNMENT
      );
    }
    if (blockOffset + blockLength > committedLimit) {
      throw corrupted(chunkPath, blockOffset, "the block exceeds the committed chunk prefix");
    }
  }

  int chunkId() {
    return chunkId;
  }

  long blockOffset() {
    return blockOffsetInChunk;
  }

  int blockId() {
    return blockId;
  }

  int blockLength() {
    return Math.toIntExact(blockSegment.byteSize());
  }

  int storeId() {
    return storeId;
  }

  int role() {
    return role;
  }

  /// if returns [LifecycleState#RETIRED] => the block can be already unmapped, hence can't be safely accessed
  @NotNull LifecycleState state() {
    return BlockHeaderLayout.readState(blockSegment);
  }

  /// Publishes the block after its content is initialized: state transition ALLOCATED -> ACTIVE
  void activate() {
    BlockHeaderLayout.transitionState(blockSegment, LifecycleState.ALLOCATED, LifecycleState.ACTIVE);
  }

  /// Discards a block whose content initialization did not complete: state transition ALLOCATED -> RETIRED
  void discard() {
    BlockHeaderLayout.transitionState(blockSegment, LifecycleState.ALLOCATED, LifecycleState.RETIRED);
  }

  /// Marks the block as immutable after the application finishes writing it: : state transition ACTIVE -> SEALED
  void seal() {
    BlockHeaderLayout.transitionState(blockSegment, LifecycleState.ACTIVE, LifecycleState.SEALED);
  }

  /// Marks a sealed block as unreachable application data that compaction can reclaim: state transition SEALED -> RETIRED
  void retire() {
    BlockHeaderLayout.transitionState(blockSegment, LifecycleState.SEALED, LifecycleState.RETIRED);
  }

  /// Unconditional transition: `<any state>` -> [LifecycleState#RETIRED]
  /// Used exclusively to discard the block when it is removed from its owning store
  ///
  /// @return block previous state; [LifecycleState#RETIRED] if block was already retired (i.e., no actual transition happened)
  @NotNull LifecycleState retireForStoreDrop() {
    //Could be implemented as if(active) -> seal(); if(sealed) -> retire()
    // but I prefer slightly faster method:
    return BlockHeaderLayout.retireForStoreDrop(blockSegment);
  }

  /// Retires this physical copy after evacuation publishes the same logical block in a newer chunk.
  ///
  /// @return block previous state; [LifecycleState#RETIRED] if block was already retired (i.e., no actual transition happened)
  @NotNull LifecycleState retireEvacuatedBlock() {
    return BlockHeaderLayout.retireEvacuatedBlock(blockSegment);
  }

  /// Copies full block, including header, onto target;
  /// target size must be == current block size
  void copyTo(@NotNull MemorySegment target) {
    if (target.byteSize() != blockSegment.byteSize()) {
      throw new IllegalArgumentException(
        "Target size(=" + target.byteSize() + ") must match blockLength(=" + blockSegment.byteSize() + ")"
      );
    }
    MemorySegment.copy(blockSegment, 0, target, 0, blockSegment.byteSize());
  }

  @NotNull MemorySegment contentSegment() {
    return contentSegment;
  }

  @Override
  public String toString() {
    // avoid accessing blockSegment's data: it can be closed already
    return "DatabaseBlock[#" + blockId +
           ", store: " + storeId +
           ", role: " + role +
           ", size: " + blockSegment.byteSize() +
           "]{@" + blockOffsetInChunk + " of chunk #" + chunkId + '}';
  }

  private static boolean isBlockAligned(long value) {
    return (value & (BlockHeaderLayout.BLOCK_ALIGNMENT - 1)) == 0;
  }

  private static @NotNull CorruptedException corrupted(@NotNull Path chunkPath, long blockOffset, @NotNull String details) {
    return new CorruptedException("[" + chunkPath + "]: invalid block at offset " + blockOffset + ": " + details);
  }
}
