// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi;

import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.database.impl.BlocksDatabaseImpl;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.util.io.IOUtil.MiB;

/**
 * A factory for block databases
 *
 * @param fsyncOnFlush controls whether flushing a database calls fsync
 * @param fsyncOnClose controls whether closing a database calls fsync
 */
@ApiStatus.Internal
public record BlocksDatabaseFactory(int chunkSize, boolean fsyncOnFlush, boolean fsyncOnClose)
  implements StorageFactory<BlocksDatabase> {

  public static final int DEFAULT_CHUNK_SIZE = 128 * MiB;
  public static final boolean DEFAULT_FSYNC_ON_FLUSH = MMappedFileStorage.FSYNC_ON_FLUSH_BY_DEFAULT;
  public static final boolean DEFAULT_FSYNC_ON_CLOSE = false;

  public BlocksDatabaseFactory(int chunkSize) {
    this(chunkSize, DEFAULT_FSYNC_ON_FLUSH, DEFAULT_FSYNC_ON_CLOSE);
  }

  public BlocksDatabaseFactory {
    if (chunkSize <= 0 || Integer.bitCount(chunkSize) != 1) {
      throw new IllegalArgumentException("chunkSize(=" + chunkSize + ") must be a positive power of 2");
    }
  }

  /// @return a factory with the default configuration
  public static @NotNull BlocksDatabaseFactory withDefaults() {
    return new BlocksDatabaseFactory(DEFAULT_CHUNK_SIZE, DEFAULT_FSYNC_ON_FLUSH, DEFAULT_FSYNC_ON_CLOSE);
  }

  public @NotNull BlocksDatabaseFactory chunkSize(int newChunkSize) {
    return new BlocksDatabaseFactory(newChunkSize, fsyncOnFlush, fsyncOnClose);
  }

  public @NotNull BlocksDatabaseFactory fsyncOnFlush(boolean newFsyncOnFlush) {
    return new BlocksDatabaseFactory(chunkSize, newFsyncOnFlush, fsyncOnClose);
  }

  public @NotNull BlocksDatabaseFactory fsyncOnClose(boolean newFsyncOnClose) {
    return new BlocksDatabaseFactory(chunkSize, fsyncOnFlush, newFsyncOnClose);
  }

  /// Opens or creates a block database in the specified directory
  @Override
  public @NotNull BlocksDatabase open(@NotNull Path databaseDirectory) throws IOException {
    return BlocksDatabaseImpl.open(databaseDirectory.toAbsolutePath(), chunkSize, fsyncOnFlush, fsyncOnClose);
  }
}
