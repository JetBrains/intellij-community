// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetrics;
import com.intellij.util.io.CorruptedException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ALLOCATED;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.RETIRED;
import static com.intellij.platform.util.io.storages.database.spi.BlocksStore.Block.LifecycleState.SEALED;

/** Catalog and allocator of blocks */
final class DatabaseBlocks implements Closeable {
  private final @NotNull DatabaseCatalog databaseCatalog;
  private final @NotNull DatabaseChunks chunks;
  private final transient @NotNull Object lock = new Object();

  ///`blockId -> DatabaseBlock`
  private final Int2ObjectMap<DatabaseBlock> blocksById = new Int2ObjectOpenHashMap<>();
  ///`storeId -> List[DatabaseBlock]`
  private final Int2ObjectMap<List<DatabaseBlock>> blocksByStoreId = new Int2ObjectOpenHashMap<>();

  //@GuardedBy(lock)
  private int lastBlockId;
  //@GuardedBy(lock)
  private boolean closed;

  // ========================== monitoring counters: ==========================
  private final AtomicInteger allocatedBlocksCurrent = new AtomicInteger();
  private final AtomicInteger activeBlocksCurrent = new AtomicInteger();
  private final AtomicInteger sealedBlocksCurrent = new AtomicInteger();
  private final AtomicInteger retiredBlocksCurrent = new AtomicInteger();
  //@GuardedBy(lock)
  private int blocksAllocated;
  private final AtomicInteger blocksActivated = new AtomicInteger();
  private final AtomicInteger blocksDiscarded = new AtomicInteger();
  private final AtomicInteger blocksSealed = new AtomicInteger();
  private final AtomicInteger blocksRetired = new AtomicInteger();
  //@GuardedBy(lock):
  private int blocksEvacuated;
  private int incompleteBlocksDiscarded;
  private int droppedStoreBlocksRetired;
  private int staleBlockCopiesRetired;
  // ===========================================================================

  private DatabaseBlocks(@NotNull DatabaseCatalog databaseCatalog, @NotNull DatabaseChunks chunks) throws CorruptedException {
    this.databaseCatalog = databaseCatalog;
    this.chunks = chunks;

    var currentStoreIds = new IntOpenHashSet();
    for (var store : databaseCatalog.stores()) {
      currentStoreIds.add(store.storeId());
    }
    Int2ObjectMap<BlocksStore.Block.LifecycleState> recoveredStatesById = new Int2ObjectOpenHashMap<>();
    for (var chunk : chunks.chunks()) {
      for (var block : chunk.blocks()) {
        //Recovery/clean up after possible crash:

        var recoveredState = block.state();
        if (recoveredState == ALLOCATED) {
          //block is allocated, but requestor hasn't finished block initialization => discard
          block.discard();
          incompleteBlocksDiscarded++;
        }
        if (!currentStoreIds.contains(block.storeId())) {
          //active/sealed blocks belongs to removed store: normally, if the block is removed from the store, it must be
          // RETIRED already; but if DB was crashed, some blocks could be left in active/sealed state => fix that:
          var previousState = block.retireForStoreDrop();
          if (previousState != RETIRED) {
            droppedStoreBlocksRetired++;
          }
        }
        registerRecoveredBlock(block, recoveredState, recoveredStatesById);
      }
    }
  }

  static @NotNull DatabaseBlocks open(@NotNull DatabaseCatalog databaseCatalog, @NotNull DatabaseChunks chunks) throws CorruptedException {
    return new DatabaseBlocks(databaseCatalog, chunks);
  }

  @NotNull DatabaseBlock allocateBlock(@NotNull DatabaseCatalog.StoreInfo storeInfo, int role, int blockLength) throws IOException {
    synchronized (lock) {
      ensureNotClosed();

      var storeId = storeInfo.storeId();
      String storeName = storeInfo.name();

      var currentStore = databaseCatalog.findStore(storeName);
      if (!storeInfo.equals(currentStore)) {
        throw new IllegalStateException("Store " + storeName + " with storeId(=" + storeInfo.storeId() + ") is not current");
      }

      var blockId = nextBlockId();
      DatabaseBlock.validateParameters(blockId, blockLength, storeId, role);
      if (blockLength > chunks.chunkSize() - ChunkHeaderLayout.HEADER_SIZE) {
        throw new IllegalArgumentException("blockLength(=" + blockLength + ") does not fit in a database chunk");
      }

      var targetChunk = chunks.chunkForAllocation(blockLength);
      var block = targetChunk.allocateBlock(blockId, storeId, role, blockLength);
      registerBlock(block);
      blocksAllocated++;
      return block;
    }
  }

  @NotNull List<DatabaseBlock> blocks(int storeId) {
    synchronized (lock) {
      var blocks = blocksByStoreId.get(storeId);
      return blocks == null ? List.of() : List.copyOf(blocks);
    }
  }

  @Nullable DatabaseBlock findBlock(int blockId) {
    synchronized (lock) {
      return blocksById.get(blockId);
    }
  }

  /// Evacuates block: copies it into the newly allocated region, and retires the old copy afterward;
  /// Method replaces old copy with the new one in blocks catalog, but it DOES NOT replace external references to the old
  /// block -- its caller's responsibility to deal with such references, if any.
  void evacuateBlock(@NotNull DatabaseBlock blockToEvacuate) throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      if (blocksById.get(blockToEvacuate.blockId()) != blockToEvacuate) {
        throw new IllegalArgumentException("Block " + blockToEvacuate.blockId() + " is not the current physical copy");
      }
      var targetChunk = chunks.chunkForAllocation(blockToEvacuate.blockLength());
      var evacuatedBlock = targetChunk.copyBlock(blockToEvacuate);

      blocksById.put(evacuatedBlock.blockId(), evacuatedBlock);
      blocksByStoreId.computeIfAbsent(evacuatedBlock.storeId(), _ -> new ArrayList<>()).add(evacuatedBlock);
      blockToEvacuate.retireEvacuatedBlock();
      blocksEvacuated++;
    }
  }

  void removeChunkBlocks(@NotNull DatabaseChunk chunk) {
    synchronized (lock) {
      for (var block : chunk.blocks()) {
        if (blocksById.get(block.blockId()) == block) {
          blocksById.remove(block.blockId());
          updateCurrentBlocks(block.state(), /*delta:*/-1);
        }//else: old copy of evacuated block
        var storeBlocks = blocksByStoreId.get(block.storeId());
        if (storeBlocks == null || !storeBlocks.remove(block)) {
          throw new IllegalStateException("Block " + block.blockId() + " is absent from storeId(=" + block.storeId() + ")");
        }
        if (storeBlocks.isEmpty()) {
          blocksByStoreId.remove(block.storeId());
        }
      }
    }
  }

  /// Discards the store's blocks after its deletion reaches persistent storage
  void retireStoreBlocks(int storeId) throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      var blocks = blocksByStoreId.get(storeId);
      if (blocks != null) {
        for (var block : blocks) {
          var previousState = block.retireForStoreDrop();
          if (blocksById.get(block.blockId()) == block && previousState != RETIRED) {
            recordCurrentTransition(previousState, RETIRED);
          }
        }
      }
    }
  }

  void recordBlockActivated() {
    recordCurrentTransition(ALLOCATED, ACTIVE);
    blocksActivated.incrementAndGet();
  }

  void recordBlockDiscarded() {
    recordCurrentTransition(ALLOCATED, RETIRED);
    blocksDiscarded.incrementAndGet();
  }

  void recordBlockSealed() {
    recordCurrentTransition(ACTIVE, SEALED);
    blocksSealed.incrementAndGet();
  }

  void recordBlockRetired() {
    recordCurrentTransition(SEALED, RETIRED);
    blocksRetired.incrementAndGet();
  }

  ///Pure function, does not change the counter -- counter is updated in [registerBlock]
  //GuardedBy(lock)
  private int nextBlockId() {
    if (lastBlockId == Integer.MAX_VALUE) {
      throw new IllegalStateException("The database exhausted all positive block identifiers");
    }
    return lastBlockId + 1;
  }

  private void registerBlock(@NotNull DatabaseBlock block) throws CorruptedException {
    synchronized (lock) {
      var blockId = block.blockId();
      var previous = blocksById.putIfAbsent(blockId, block);
      if (previous != null) {
        throw new CorruptedException(
          "Duplicate blockId(=" + blockId + ") in chunks " + previous.chunkId() + " and " + block.chunkId()
        );
      }
      blocksByStoreId.computeIfAbsent(block.storeId(), _ -> new ArrayList<>()).add(block);
      updateCurrentBlocks(block.state(), /*delta: */ 1);
      lastBlockId = Math.max(lastBlockId, blockId);
    }
  }

  private void registerRecoveredBlock(@NotNull DatabaseBlock block,
                                      @NotNull BlocksStore.Block.LifecycleState recoveredState,
                                      @NotNull Int2ObjectMap<BlocksStore.Block.LifecycleState> recoveredStatesById) throws CorruptedException {
    synchronized (lock) {
      var blockId = block.blockId();
      var previous = blocksById.get(blockId);
      BlocksStore.Block.LifecycleState previousState = null;
      if (previous != null) {
        //likely, the block was evacuated, but old remnants remains => check is it true
        var previousRecoveredState = recoveredStatesById.get(blockId);
        if (previousRecoveredState == null) {
          throw new IllegalStateException("Missing recovered state for block " + blockId);
        }
        validateEvacuatedCopy(previous, previousRecoveredState, block, recoveredState);
        previousState = previous.retireEvacuatedBlock(); //if not yet retired (e.g. crash interrupts evacuation) => fix it
        if (previousRecoveredState != RETIRED) {
          staleBlockCopiesRetired++;
        }
      }
      blocksById.put(blockId, block);
      if (previousState != null) {
        updateCurrentBlocks(previousState, -1);
      }
      updateCurrentBlocks(block.state(), 1);
      recoveredStatesById.put(blockId, recoveredState);
      blocksByStoreId.computeIfAbsent(block.storeId(), _ -> new ArrayList<>()).add(block);
      lastBlockId = Math.max(lastBlockId, blockId);
    }
  }

  private static void validateEvacuatedCopy(@NotNull DatabaseBlock previous,
                                            @NotNull BlocksStore.Block.LifecycleState previousRecoveredState,
                                            @NotNull DatabaseBlock current,
                                            @NotNull BlocksStore.Block.LifecycleState currentRecoveredState) throws CorruptedException {
    if (currentRecoveredState == ALLOCATED) {
      throw duplicateBlock(previous, current, "the newer copy is ALLOCATED");
    }
    if (previous.chunkId() >= current.chunkId()) {
      throw duplicateBlock(previous, current, "copies are not ordered by increasing chunkId");
    }
    if (previous.storeId() != current.storeId() ||
        previous.role() != current.role() ||
        previous.blockLength() != current.blockLength()) {
      throw duplicateBlock(previous, current, "immutable block metadata does not match");
    }
    if (previousRecoveredState == ALLOCATED) {
      throw duplicateBlock(previous, current, "the older copy is ALLOCATED");
    }
  }

  private static @NotNull CorruptedException duplicateBlock(@NotNull DatabaseBlock previous,
                                                             @NotNull DatabaseBlock current,
                                                             @NotNull String details) {
    return new CorruptedException(
      "Duplicate blockId(=" + current.blockId() + ") in chunks " + previous.chunkId() + " and " + current.chunkId() + ": " + details
    );
  }

  private void recordCurrentTransition(@NotNull BlocksStore.Block.LifecycleState from,
                                       @NotNull BlocksStore.Block.LifecycleState to) {
    updateCurrentBlocks(from, -1);
    updateCurrentBlocks(to, 1);
  }

  private void updateCurrentBlocks(@NotNull BlocksStore.Block.LifecycleState state, int delta) {
    switch (state) {
      case ALLOCATED -> allocatedBlocksCurrent.addAndGet(delta);
      case ACTIVE -> activeBlocksCurrent.addAndGet(delta);
      case SEALED -> sealedBlocksCurrent.addAndGet(delta);
      case RETIRED -> retiredBlocksCurrent.addAndGet(delta);
    }
  }

  /// Returns the current block metrics.
  ///
  /// @param snapshotMetrics true requires a consistent snapshot; false permits weakly consistent values without locking
  @NotNull DatabaseMetrics.BlocksMetrics metrics(@SuppressWarnings("unused") boolean snapshotMetrics) {
    // TODO RC: implement weakly-consistent metrics without locking
    synchronized (lock) {
      return new DatabaseMetrics.BlocksMetrics(
        blocksById.size(),
        allocatedBlocksCurrent.get(), activeBlocksCurrent.get(), sealedBlocksCurrent.get(), retiredBlocksCurrent.get(),
        blocksAllocated, blocksActivated.get(), blocksDiscarded.get(), blocksSealed.get(), blocksRetired.get(),
        blocksEvacuated,
        incompleteBlocksDiscarded, droppedStoreBlocksRetired, staleBlockCopiesRetired
      );
    }
  }

  @Override
  public void close() {
    synchronized (lock) {
      blocksById.clear();
      blocksByStoreId.clear();
      allocatedBlocksCurrent.set(0);
      activeBlocksCurrent.set(0);
      sealedBlocksCurrent.set(0);
      retiredBlocksCurrent.set(0);
      closed = true;
    }
  }

  private void ensureNotClosed() throws IOException {
    if (closed || chunks.isClosed()) {
      throw new IOException("Database blocks are already closed");
    }
  }
}
