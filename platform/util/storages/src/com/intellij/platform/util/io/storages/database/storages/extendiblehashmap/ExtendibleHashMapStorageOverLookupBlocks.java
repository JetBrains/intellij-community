// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.extendiblehashmap;

import com.intellij.platform.util.io.storages.database.storages.durablemap.LookupBlocks;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapStorage;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.CorruptedException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ALLOCATED;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;
import static com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapBlockLayout.BLOCK_HEADER_SIZE;
import static com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapBlockLayout.HEADER_AND_SEGMENTS_TABLE_BLOCK_MARKER;
import static com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapBlockLayout.headerAndSegmentsTableBlockPayloadSize;
import static com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapBlockLayout.segmentsTableOffset;

/// Uses single block for the hash map header and segments table, and a separate block for each hash map data-segment
@ApiStatus.Internal
public final class ExtendibleHashMapStorageOverLookupBlocks implements ExtendibleHashMapStorage {
  /// Persistent identifier of the extendible hash map lookup format
  public static final int IMPLEMENTATION_ID = 1;

  /// Generation used before a map descriptor selects a generation
  public static final int INITIAL_GENERATION = 1;


  private final @NotNull LookupBlocks lookupBlocks;
  private final int headerSize;
  private final int segmentSize;


  private final Int2ObjectMap<LookupBlocks.Block> blocksBySegmentIndex = new Int2ObjectOpenHashMap<>();

  /// Block that contains static header + segmentsTable
  private LookupBlocks.Block metadataBlock;
  /// segments table offset in [#metadataBlock] payload
  private final int metadataBlockSegmentsTableOffset;
  /// total size of [#metadataBlock]'s payload: headerSize & segmentSize, but with alignment
  private final int metadataBlockTotalPayloadSize;

  private boolean open = true;

  public ExtendibleHashMapStorageOverLookupBlocks(@NotNull LookupBlocks lookupBlocks,
                                                  int headerSize,
                                                  int segmentSize) throws IOException {
    if (segmentSize <= 0) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be positive");
    }
    if (headerSize <= 0 || headerSize > segmentSize) {
      throw new IllegalArgumentException("headerSize(=" + headerSize + ") must be in [1.." + segmentSize + "]");
    }

    metadataBlockSegmentsTableOffset = segmentsTableOffset(headerSize);
    metadataBlockTotalPayloadSize = headerAndSegmentsTableBlockPayloadSize(headerSize, segmentSize);

    this.lookupBlocks = lookupBlocks;
    this.headerSize = headerSize;
    this.segmentSize = segmentSize;

    for (var block : lookupBlocks.blocks()) {
      if (block.state() != RETIRED) {
        addExistingBlock(block);
      }
    }
    if (metadataBlock == null && !blocksBySegmentIndex.isEmpty()) {
      throw new CorruptedException("Hash map data exists without a header (metadata) block");
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isEmpty() throws IOException {
    ensureOpen();
    return metadataBlock == null && blocksBySegmentIndex.isEmpty();
  }

  @Override
  public int segmentSize() {
    return segmentSize;
  }

  @Override
  public int headerSize() {
    return headerSize;
  }

  @Override
  public @NotNull MemorySegment header() throws IOException {
    ensureOpen();
    if (metadataBlock == null) {
      allocateMetadataBlock();
    }
    return headerPayload(metadataBlock);
  }

  @Override
  public @NotNull MemorySegment segmentsTable() throws IOException {
    ensureOpen();
    ensureHeaderAllocated();
    return segmentsTablePayload(metadataBlock);
  }

  @Override
  public @NotNull MemorySegment segment(int segmentIndex) throws IOException {
    ensureOpen();
    ensureHeaderAllocated();
    checkSegmentIndex(segmentIndex);
    var block = blocksBySegmentIndex.get(segmentIndex);
    if (block == null) {
      throw new CorruptedException("Missing hash map segment #" + segmentIndex);
    }
    return segmentPayload(block);
  }

  @Override
  public @NotNull MemorySegment allocateSegment(int segmentIndex) throws IOException {
    ensureOpen();
    ensureHeaderAllocated();
    checkSegmentIndex(segmentIndex);
    if (blocksBySegmentIndex.containsKey(segmentIndex)) {
      throw new IllegalArgumentException("Hash map segment #" + segmentIndex + " already exists");
    }

    var block = lookupBlocks.allocate(BLOCK_HEADER_SIZE + segmentSize);
    try {
      var content = block.payload();
      ExtendibleHashMapBlockLayout.initializeSegmentIndex(content, segmentIndex);
      var payload = segmentPayload(block);
      block.activate();
      blocksBySegmentIndex.put(segmentIndex, block);
      return payload;
    }
    catch (Throwable failure) {
      if (block.state() == ALLOCATED) {
        //Not strictly required: ALLOCATED blocks are discarded on recovery phase of DB startup
        // but why not do it right here, if we can?
        block.discard();
      }
      throw failure;
    }
  }

  @Override
  public void clear() throws IOException {
    ensureOpen();
    retireOwnedBlocks();
  }

  @Override
  public void flush() throws IOException {
    ensureOpen();
    lookupBlocks.flush();
  }

  @Override
  public void close() {
    open = false;
    metadataBlock = null;
    blocksBySegmentIndex.clear();
  }

  @Override
  public void closeAndClean() {
    retireOwnedBlocks();
    open = false;
  }

  private void allocateMetadataBlock() throws IOException {
    var block = lookupBlocks.allocate(metadataBlockTotalPayloadSize);
    try {
      var content = block.payload();
      if (content.byteSize() < metadataBlockTotalPayloadSize) {
        throw corrupted(block, "the content is too small for the header and segments table");
      }
      ExtendibleHashMapBlockLayout.initializeHeaderAndSegmentsTable(content);
      block.activate();
      metadataBlock = block;
    }
    catch (Throwable failure) {
      if (block.state() == ALLOCATED) {
        block.discard();
      }
      throw failure;
    }
  }

  private void addExistingBlock(@NotNull LookupBlocks.Block block) throws IOException {
    if (block.state() != ACTIVE) {
      // SEALED blocks can't be used in Indexes because sealed means 'read-only', and Index impl
      // usually do modify its blocks
      throw corrupted(block, "the block is not active");
    }

    var content = block.payload();
    if (content.byteSize() < BLOCK_HEADER_SIZE) {
      throw corrupted(block, "the content(=" + content.byteSize() + "b) is too small for the block header(=" + BLOCK_HEADER_SIZE + "b)");
    }
    var segmentIndex = ExtendibleHashMapBlockLayout.segmentIndex(content);

    if (segmentIndex == HEADER_AND_SEGMENTS_TABLE_BLOCK_MARKER) {
      if (metadataBlock != null) {
        throw corrupted(block, "header and segments table also belong to block " + metadataBlock.id());
      }
      headerPayload(block);
      segmentsTablePayload(block);
      metadataBlock = block;
      return;
    }

    if (segmentIndex < 0) {
      throw corrupted(block, "segment index " + segmentIndex + " is negative");
    }
    if (content.byteSize() < BLOCK_HEADER_SIZE + (long)segmentSize) {
      throw corrupted(block, "the content is too small for a " + segmentSize + "-byte segment");
    }
    var previous = blocksBySegmentIndex.putIfAbsent(segmentIndex, block);
    if (previous != null) {
      throw corrupted(block, "segment #" + segmentIndex + " also belongs to block " + previous.id());
    }
  }

  /// @return payload of a segment-block
  private @NotNull MemorySegment segmentPayload(@NotNull LookupBlocks.Block block) throws IOException {
    var content = block.payload();
    if (content.byteSize() < BLOCK_HEADER_SIZE + (long)segmentSize) {
      throw corrupted(block, "the content is too small for a " + segmentSize + "-byte segment");
    }
    return content.asSlice(BLOCK_HEADER_SIZE, segmentSize);
  }

  /// @return the header region of the metadata block
  private @NotNull MemorySegment headerPayload(@NotNull LookupBlocks.Block block) throws IOException {
    var content = block.payload();
    if (content.byteSize() < metadataBlockTotalPayloadSize) {
      throw corrupted(block, "the content is too small for the header and segments table");
    }
    return content.asSlice(BLOCK_HEADER_SIZE, headerSize);
  }

  private @NotNull MemorySegment segmentsTablePayload(@NotNull LookupBlocks.Block block) throws IOException {
    var content = block.payload();
    if (content.byteSize() < metadataBlockTotalPayloadSize) {
      throw corrupted(block, "the content is too small for the header and segments table");
    }
    return content.asSlice(metadataBlockSegmentsTableOffset, segmentSize);
  }

  private void retireOwnedBlocks() {
    lookupBlocks.retireAll();
    metadataBlock = null;
    blocksBySegmentIndex.clear();
  }

  private void ensureHeaderAllocated() {
    if (metadataBlock == null) {
      throw new IllegalStateException("Call header() before accessing the segments table or hash map segments");
    }
  }

  private void ensureOpen() throws ClosedStorageException {
    if (!open) {
      throw new ClosedStorageException("The hash map block storage is closed");
    }
  }

  private static void checkSegmentIndex(int segmentIndex) {
    if (segmentIndex < 0) {
      throw new IllegalArgumentException("segmentIndex(=" + segmentIndex + ") must not be negative");
    }
  }

  private static @NotNull CorruptedException corrupted(@NotNull LookupBlocks.Block block,
                                                       @NotNull String details) {
    return new CorruptedException("Invalid hash map block #" + block.id() + ": " + details);
  }
}
