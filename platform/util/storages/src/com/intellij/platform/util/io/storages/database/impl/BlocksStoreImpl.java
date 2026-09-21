// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.spi.StoreMetadata;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/// Provides access to the blocks that belong to one store
final class BlocksStoreImpl implements BlocksStore {
  private final @NotNull BlocksDatabaseImpl database;
  private final @NotNull DatabaseCatalog.StoreInfo storeInfo;
  private final Int2ObjectMap<BlockAccessor> blockAccessors = new Int2ObjectOpenHashMap<>();

  BlocksStoreImpl(@NotNull BlocksDatabaseImpl database,
                  @NotNull DatabaseCatalog.StoreInfo storeInfo) {
    this.database = database;
    this.storeInfo = storeInfo;
  }

  /** @return the application data format version */
  @Override
  public int dataVersion() {
    return database.dataVersion(this);
  }

  @Override
  public @NotNull StoreMetadata storeMetadata() {
    return database.storeInfo(this).storeMetadata();
  }

  @Override
  public void updateStoreMetadata(@NotNull StoreMetadata storeMetadata) throws IOException {
    database.updateStoreMetadata(this, storeMetadata);
  }

  /** Allocates one block for this store */
  @Override
  public @NotNull Block allocateBlock(int role, int minimumContentLength) throws IOException {
    return wrap(database.allocateBlock(this, role, minimumContentLength));
  }

  /** @return all physical blocks of this store, including retired blocks */
  @Override
  public @NotNull List<Block> blocks() {
    var blocks = database.blocks(this);
    var handles = new ArrayList<Block>(blocks.size());
    for (var block : blocks) {
      handles.add(wrap(block));
    }
    return List.copyOf(handles);
  }

  /** Finds one owned block without exposing blocks of another store */
  @Override
  public @Nullable Block findBlock(int blockId) {
    var block = database.findBlock(this, blockId);
    return block == null ? null : wrap(block);
  }

  @Override
  public boolean isDirty() {
    return database.isDirty();
  }

  @Override
  public void flush() throws IOException {
    database.flush();
  }

  @Override
  public void drop() throws IOException {
    database.dropStore(this);
  }

  /** @return true when this handle belongs to the specified database */
  boolean isOwnedBy(@NotNull BlocksDatabaseImpl database) {
    return this.database == database;
  }

  /** @return the immutable catalog identity used to detect a stale handle */
  @NotNull DatabaseCatalog.StoreInfo info() {
    return storeInfo;
  }

  @Override
  public String toString() {
    return "BlocksStore[" + storeInfo.name() + '#' + storeInfo.storeId() + ", v=" + storeInfo.dataVersion() + ']';
  }

  /// Adapts [DatabaseBlock] to [Block] SPI interface
  private synchronized @NotNull BlockAccessor wrap(@NotNull DatabaseBlock block) {
    return blockAccessors.computeIfAbsent(block.blockId(), _ -> new BlockAccessor(block));
  }

  /** An implementation of [Block] SPI over [DatabaseBlock] */
  private final class BlockAccessor implements Block {
    private final @NotNull DatabaseBlock block;

    private BlockAccessor(@NotNull DatabaseBlock block) {
      this.block = block;
    }

    @Override
    public int id() {
      return block.blockId();
    }

    @Override
    public int role() {
      return block.role();
    }

    @Override
    public @NotNull LifecycleState state() {
      return block.state();
    }

    @Override
    public @NotNull MemorySegment content() {
      return database.blockContent(BlocksStoreImpl.this, block);
    }

    @Override
    public void activate() {
      block.activate();
    }

    @Override
    public void discard() {
      block.discard();
    }

    @Override
    public void seal() {
      block.seal();
    }

    @Override
    public void retire() {
      database.retireBlock(BlocksStoreImpl.this, block);
    }

    @Override
    public String toString() {
      return "BlockAccessor[" + block + ']';
    }
  }
}
