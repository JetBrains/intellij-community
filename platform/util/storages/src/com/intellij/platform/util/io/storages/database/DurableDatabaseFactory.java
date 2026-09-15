// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database;

import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.database.impl.DurableDatabaseImpl;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabaseFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A factory for durable databases
 *
 * @param fsyncOnFlush controls whether flushing a database calls fsync
 * @param fsyncOnClose controls whether closing a database calls fsync
 */
@ApiStatus.Internal
public record DurableDatabaseFactory(int chunkSize, boolean fsyncOnFlush, boolean fsyncOnClose)
  implements StorageFactory<DurableDatabase> {

  public static final int DEFAULT_CHUNK_SIZE = BlocksDatabaseFactory.DEFAULT_CHUNK_SIZE;
  public static final boolean DEFAULT_FSYNC_ON_FLUSH = BlocksDatabaseFactory.DEFAULT_FSYNC_ON_FLUSH;
  public static final boolean DEFAULT_FSYNC_ON_CLOSE = BlocksDatabaseFactory.DEFAULT_FSYNC_ON_CLOSE;

  public DurableDatabaseFactory(int chunkSize) {
    this(chunkSize, DEFAULT_FSYNC_ON_FLUSH, DEFAULT_FSYNC_ON_CLOSE);
  }

  public DurableDatabaseFactory(int chunkSize, boolean fsyncOnClose) {
    this(chunkSize, DEFAULT_FSYNC_ON_FLUSH, fsyncOnClose);
  }

  public DurableDatabaseFactory {
    if (chunkSize <= 0 || Integer.bitCount(chunkSize) != 1) {
      throw new IllegalArgumentException("chunkSize(=" + chunkSize + ") must be a positive power of 2");
    }
  }

  /** @return factory with the default chunk size */
  public static @NotNull DurableDatabaseFactory withDefaults() {
    return new DurableDatabaseFactory(DEFAULT_CHUNK_SIZE, DEFAULT_FSYNC_ON_FLUSH, DEFAULT_FSYNC_ON_CLOSE);
  }

  public @NotNull DurableDatabaseFactory chunkSize(int newChunkSize) {
    return new DurableDatabaseFactory(newChunkSize, fsyncOnFlush, fsyncOnClose);
  }

  public @NotNull DurableDatabaseFactory fsyncOnFlush(boolean newFsyncOnFlush) {
    return new DurableDatabaseFactory(chunkSize, newFsyncOnFlush, fsyncOnClose);
  }

  public @NotNull DurableDatabaseFactory fsyncOnClose(boolean newFsyncOnClose) {
    return new DurableDatabaseFactory(chunkSize, fsyncOnFlush, newFsyncOnClose);
  }

  /** Opens or creates a database in the specified directory */
  @Override
  public @NotNull DurableDatabase open(@NotNull Path databaseDirectory) throws IOException {
    var blocksDatabase = new BlocksDatabaseFactory(chunkSize, fsyncOnFlush, fsyncOnClose).open(databaseDirectory);
    return new DurableDatabaseImpl(blocksDatabase);
  }
}
