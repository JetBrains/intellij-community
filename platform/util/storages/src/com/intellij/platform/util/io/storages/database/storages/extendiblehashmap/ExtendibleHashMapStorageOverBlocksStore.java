// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.extendiblehashmap;

import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapStorage;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.CorruptedException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ALLOCATED;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;
import static com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapSegmentBlockLayout.HEADER_SIZE;

/// Maps fixed-size logical segments to database blocks with the specified role
@ApiStatus.Internal
public final class ExtendibleHashMapStorageOverBlocksStore implements ExtendibleHashMapStorage {
  private final @NotNull BlocksStore store;
  private final int blockRole;
  private final int segmentSize;

  /// Blocks are added here even before their segment-header is initialized
  private final ArrayList<BlocksStore.Block> ownedBlocks = new ArrayList<>();
  /// Blocks are added here after their segment-header is initialized
  private final Int2ObjectMap<BlocksStore.Block> blocksBySegmentIndex = new Int2ObjectOpenHashMap<>();

  private boolean open = true;

  public ExtendibleHashMapStorageOverBlocksStore(@NotNull BlocksStore store,
                                                 int blockRole,
                                                 int segmentSize) throws IOException {
    if (segmentSize <= 0) {
      throw new IllegalArgumentException("segmentSize(=" + segmentSize + ") must be positive");
    }
    this.store = store;
    this.blockRole = blockRole;
    this.segmentSize = segmentSize;

    for (var block : store.blocks()) {
      if (block.role() == blockRole && block.state() != RETIRED) {
        addExistingBlock(block);
      }
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isEmpty() throws IOException {
    ensureOpen();
    return blocksBySegmentIndex.isEmpty();
  }

  @Override
  public int segmentSize() {
    return segmentSize;
  }

  @Override
  public @NotNull MemorySegment segment(int segmentIndex) throws IOException {
    ensureOpen();
    checkSegmentIndex(segmentIndex);
    var block = blocksBySegmentIndex.get(segmentIndex);
    if (block == null) {
      throw new CorruptedException("Missing hashmap segment #" + segmentIndex);
    }
    return segmentPayload(block);
  }

  @Override
  public @NotNull MemorySegment allocateSegment(int segmentIndex) throws IOException {
    ensureOpen();
    checkSegmentIndex(segmentIndex);
    if (blocksBySegmentIndex.containsKey(segmentIndex)) {
      throw new IllegalArgumentException("Hashmap segment #" + segmentIndex + " already exists");
    }

    var block = store.allocateBlock(blockRole, Math.addExact(HEADER_SIZE, segmentSize));
    ownedBlocks.add(block);
    var initialized = false;
    try {
      var content = block.content();
      ExtendibleHashMapSegmentBlockLayout.initializeSegmentIndex(content, segmentIndex);
      var payload = segmentPayload(block);
      block.activate();
      blocksBySegmentIndex.put(segmentIndex, block);
      initialized = true;
      return payload;
    }
    finally {
      if (!initialized && block.state() == ALLOCATED) {
        block.discard();
      }
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
    store.flush();
  }

  @Override
  public void close() {
    open = false;
    blocksBySegmentIndex.clear();
  }

  @Override
  public void closeAndClean() {
    retireOwnedBlocks();
    open = false;
  }

  private void addExistingBlock(@NotNull BlocksStore.Block block) throws IOException {
    if (block.state() != ACTIVE) {
      throw corrupted(block, "the block is not active");
    }
    var content = block.content();
    if (content.byteSize() < HEADER_SIZE) {
      throw corrupted(block, "the content is too small for the block header");
    }
    ownedBlocks.add(block);
    var segmentIndex = ExtendibleHashMapSegmentBlockLayout.segmentIndex(content);
    if (segmentIndex < 0) {
      throw corrupted(block, "segment index " + segmentIndex + " is negative");
    }
    if (content.byteSize() < HEADER_SIZE + (long)segmentSize) {
      throw corrupted(block, "the content is too small for a " + segmentSize + "-byte segment");
    }
    var previous = blocksBySegmentIndex.putIfAbsent(segmentIndex, block);
    if (previous != null) {
      throw corrupted(block, "segment #" + segmentIndex + " also belongs to block " + previous.id());
    }
  }

  private @NotNull MemorySegment segmentPayload(@NotNull BlocksStore.Block block) throws CorruptedException {
    var content = block.content();
    if (content.byteSize() < HEADER_SIZE + (long)segmentSize) {
      throw corrupted(block, "the content is too small for a " + segmentSize + "-byte segment");
    }
    return content.asSlice(HEADER_SIZE, segmentSize);
  }

  private void retireOwnedBlocks() {
    for (var block : ownedBlocks) {
      switch (block.state()) {
        case ALLOCATED -> block.discard();
        case ACTIVE -> {
          block.seal();
          block.retire();
        }
        case SEALED -> block.retire();
        case RETIRED -> { }
      }
    }
    ownedBlocks.clear();
    blocksBySegmentIndex.clear();
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

  private static @NotNull CorruptedException corrupted(@NotNull BlocksStore.Block block,
                                                        @NotNull String details) {
    return new CorruptedException("Invalid hashmap segment block #" + block.id() + ": " + details);
  }
}
