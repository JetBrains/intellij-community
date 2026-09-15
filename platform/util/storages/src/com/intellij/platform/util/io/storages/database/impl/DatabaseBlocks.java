// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout;
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

  private DatabaseBlocks(@NotNull DatabaseCatalog databaseCatalog, @NotNull DatabaseChunks chunks) throws CorruptedException {
    this.databaseCatalog = databaseCatalog;
    this.chunks = chunks;

    var currentStoreIds = new IntOpenHashSet();
    for (var store : databaseCatalog.stores()) {
      currentStoreIds.add(store.storeId());
    }
    for (var chunk : chunks.chunks()) {
      for (var block : chunk.blocks()) {
        registerBlock(block);
        // Part of recovery: normally, all blocks removed from the store must be RETIRED already, but if DB was crashed
        //  some of them could be left in active/sealed state => fix that:
        if (!currentStoreIds.contains(block.storeId())) {
          block.retireForStoreDrop();
        }
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

  /// Discards the store's blocks after its deletion reaches persistent storage
  void retireStoreBlocks(int storeId) throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      var blocks = blocksByStoreId.get(storeId);
      if (blocks != null) {
        for (var block : blocks) {
          block.retireForStoreDrop();
        }
      }
    }
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
      lastBlockId = Math.max(lastBlockId, blockId);
    }
  }

  @Override
  public void close() {
    synchronized (lock) {
      blocksById.clear();
      blocksByStoreId.clear();
      closed = true;
    }
  }

  private void ensureNotClosed() throws IOException {
    if (closed || chunks.isClosed()) {
      throw new IOException("Database blocks are already closed");
    }
  }
}
