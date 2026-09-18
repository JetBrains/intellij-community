// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;

/// Provides named block stores over database chunks
@ApiStatus.Internal
public final class BlocksDatabaseImpl implements BlocksDatabase {
  private static final String DATABASE_META_FILE_NAME = "database.meta";

  private final @NotNull DatabaseCatalog databaseCatalog;
  private final @NotNull DatabaseChunks databaseChunks;
  private final @NotNull DatabaseBlocks databaseBlocks;

  private final boolean fsyncOnClose;

  private final transient Object databaseLock = new Object();

  /// Set true when [#close] is initiated: logically, the DB is already closed, while technically it may be still under way
  /// Modified only under [databaseLock]
  private volatile boolean closed;

  BlocksDatabaseImpl(@NotNull DatabaseCatalog databaseCatalog,
                     @NotNull DatabaseChunks databaseChunks,
                     @NotNull DatabaseBlocks databaseBlocks,
                     boolean fsyncOnClose) {
    this.databaseCatalog = databaseCatalog;
    this.databaseChunks = databaseChunks;
    this.databaseBlocks = databaseBlocks;
    this.fsyncOnClose = fsyncOnClose;
  }

  public static @NotNull BlocksDatabaseImpl open(@NotNull Path databaseDirectory,
                                                 int chunkSize,
                                                 boolean fsyncOnFlush,
                                                 boolean fsyncOnClose) throws IOException {
    var databaseCatalog = DatabaseCatalogOverAppendOnlyLog.open(
      databaseDirectory.resolve(DATABASE_META_FILE_NAME),
      chunkSize,
      fsyncOnFlush
    );
    return IOUtil.wrapSafely(databaseCatalog, catalog -> {
      var databaseChunks = DatabaseChunks.open(databaseDirectory, catalog, fsyncOnFlush);
      return IOUtil.wrapSafely(databaseChunks, chunks -> {
        var databaseBlocks = DatabaseBlocks.open(catalog, chunks);
        return new BlocksDatabaseImpl(catalog, chunks, databaseBlocks, fsyncOnClose);
      });
    });
  }

  @Override
  public @NotNull BlocksStore openStore(@NotNull String name, int dataVersion) throws IOException {
    synchronized (databaseLock) {
      ensureNotClosed();
      var storeInfo = databaseCatalog.findStore(name);
      if (storeInfo != null) {
        if (storeInfo.dataVersion() != dataVersion) {
          throw new UnsupportedFormatException("store '" + name + "'", dataVersion, storeInfo.dataVersion());
        }
        //BlocksStoreImpl is a thin accessor without its own state -- so we could skip caching it, and just return
        // a new instance on each openStore():
        return new BlocksStoreImpl(this, storeInfo);
      }

      var storeId = databaseCatalog.nextStoreId();
      databaseCatalog.registerNewStore(storeId, name, dataVersion);
      return new BlocksStoreImpl(this, new DatabaseCatalog.StoreInfo(storeId, name, dataVersion));
    }
  }

  @Override
  public @Nullable BlocksStore findStore(@NotNull String name) {
    synchronized (databaseLock) {
      ensureNotClosed();
      var storeInfo = databaseCatalog.findStore(name);
      return storeInfo == null ? null : new BlocksStoreImpl(this, storeInfo);
    }
  }

  @Override
  public @NotNull List<String> storeNames() {
    synchronized (databaseLock) {
      ensureNotClosed();
      return ContainerUtil.map(databaseCatalog.stores(), DatabaseCatalog.StoreInfo::name);
    }
  }

  /// Persists the deletion, then retires all store's blocks
  void dropStore(@NotNull BlocksStore store) throws IOException {
    synchronized (databaseLock) {
      var storeInfo = requireCurrent(requireStoreImplementation(store));
      databaseCatalog.dropStore(storeInfo.storeId());
      try {
        databaseCatalog.fsync();//savepoint: store is dropped
      }
      finally {
        //TODO RC: If fsync fails, blocks are retired, but the store drop might not persist in the metadata catalog.
        //         The storage entry can remain after reopen, so the recovery result is undefined. Still, I decided
        //         that empty storage is close to the intended outcome (dropped storage) than partially-alive storage :)
        databaseBlocks.retireStoreBlocks(storeInfo.storeId());
      }
    }
  }

  @Override
  public boolean isDirty() {
    return databaseCatalog.isDirty();
  }

  @Override
  public void flush() throws IOException {
    databaseChunks.flush();
    databaseCatalog.flush();
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  private void ensureNotClosed() {
    if (closed) {
      throw new IllegalStateException("The database is already closed");
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (databaseLock) {
      if (closed) {
        return;
      }
      closed = true; //logically: db is closed after this point
    }

    IOException failure = null;
    if (fsyncOnClose) {
      try {
        fsync();
      }
      catch (IOException e) {
        failure = e;
      }
    }

    try {
      IOUtil.closeAllSafely(databaseBlocks, databaseChunks, databaseCatalog);
    }
    catch (IOException e) {
      if (failure == null) {
        failure = e;
      }
      else {
        failure.addSuppressed(e);
      }
    }

    if (failure != null) {
      throw failure;
    }
  }

  private void fsync() throws IOException {
    databaseChunks.fsync();
    databaseCatalog.fsync();
  }

  /// @return the data version after checking that the store is current
  int dataVersion(@NotNull BlocksStoreImpl store) {
    synchronized (databaseLock) {
      return requireCurrent(store).dataVersion();
    }
  }

  /// Allocates a block that belongs to the current store
  @NotNull DatabaseBlock allocateBlock(@NotNull BlocksStoreImpl store,
                                       int role,
                                       int minimumContentLength) throws IOException {
    synchronized (databaseLock) {
      var blockLength = DatabaseBlock.blockLengthForContent(minimumContentLength);
      return databaseBlocks.allocateBlock(requireCurrent(store), role, blockLength);
    }
  }

  /// @return all physical blocks of the current store, including retired blocks
  @NotNull List<DatabaseBlock> blocks(@NotNull BlocksStoreImpl store) {
    synchronized (databaseLock) {
      return databaseBlocks.blocks(requireCurrent(store).storeId());
    }
  }

  /// Finds a block and rejects a block that belongs to another store
  @Nullable DatabaseBlock findBlock(@NotNull BlocksStoreImpl store, int blockId) {
    synchronized (databaseLock) {
      var storeInfo = requireCurrent(store);
      var block = databaseBlocks.findBlock(blockId);
      if (block != null && block.storeId() != storeInfo.storeId()) {
        throw new IllegalArgumentException(
          "Block " + blockId + " belongs to storeId(=" + block.storeId() + "), not storeId(=" + storeInfo.storeId() + ")"
        );
      }
      return block;
    }
  }

  /// @return the content after checking the store and block ownership
  @NotNull MemorySegment blockContent(@NotNull BlocksStoreImpl store, @NotNull DatabaseBlock block) {
    synchronized (databaseLock) {
      var ownedBlock = requireOwnedBlock(store, block);
      return switch (ownedBlock.state()) {
        case ALLOCATED, ACTIVE -> ownedBlock.contentSegment();
        case SEALED -> ownedBlock.contentSegment().asReadOnly();
        case RETIRED -> throw new IllegalStateException("Block " + ownedBlock.blockId() + " is retired");
      };
    }
  }

  /// Retires a block.
  /// The block must be sealed, and it must belong to store -- otherwise exception is thrown
  void retireBlock(@NotNull BlocksStoreImpl store, @NotNull DatabaseBlock block) {
    synchronized (databaseLock) {
      requireOwnedBlock(store, block).retire();
    }
  }

  /// Resolves the same block instance without allowing access across store boundaries
  private @NotNull DatabaseBlock requireOwnedBlock(@NotNull BlocksStoreImpl store, @NotNull DatabaseBlock block) {
    var currentBlock = findBlock(store, block.blockId());
    if (currentBlock != block) {
      throw new IllegalArgumentException("Unknown blockId(=" + block.blockId() + ")");
    }
    return currentBlock;
  }

  /// Rejects stale handles after a drop or same-name replacement
  private @NotNull DatabaseCatalog.StoreInfo requireCurrent(@NotNull BlocksStoreImpl store) {
    ensureNotClosed();
    if (!store.isOwnedBy(this)) {
      throw new IllegalArgumentException("The store belongs to another database");
    }
    var storeInfo = store.info();
    var currentStoreInfo = databaseCatalog.findStore(storeInfo.name());
    if (!storeInfo.equals(currentStoreInfo)) {
      throw new IllegalStateException("Store " + storeInfo.name() + " with storeId(=" + storeInfo.storeId() + ") is not current");
    }
    return storeInfo;
  }

  private @NotNull BlocksStoreImpl requireStoreImplementation(@NotNull BlocksStore store) {
    if (!(store instanceof BlocksStoreImpl implementation) || !implementation.isOwnedBy(this)) {
      throw new IllegalArgumentException("The store belongs to another database");
    }
    return implementation;
  }
}
