// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.database.spi.StoreMetadata;
import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetrics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.List;

/** The persistent catalog of one database */
@ApiStatus.Internal
public interface DatabaseCatalog extends Closeable, Flushable {

  /** @return DB identifier (kind-of unique) */
  long databaseId();

  /** @return size of data chunk configured for the database */
  int chunkSize();

  /** @return all current stores in identifier order */
  @NotNull List<StoreInfo> stores();

  /** @return the current store with the specified name, or {@code null} */
  @Nullable StoreInfo findStore(@NotNull String name);

  /** @return the next store identifier without changing persistent metadata */
  int nextStoreId();

  /** Registers a new store without store metadata. */
  default void registerNewStore(int storeId, @NotNull String name, int dataVersion) throws IOException {
    registerNewStore(storeId, name, dataVersion, StoreMetadata.EMPTY);
  }

  /** Registers a new store with its initial metadata */
  void registerNewStore(int storeId,
                        @NotNull String name,
                        int dataVersion,
                        @NotNull StoreMetadata storeMetadata) throws IOException;

  /** Replaces the opaque metadata of a current store */
  void updateStoreMetadata(int storeId, @NotNull StoreMetadata storeMetadata) throws IOException;

  /** Removes a current store while keeping its identifier reserved */
  void dropStore(int storeId) throws IOException;

  /** @return all registered chunks, including retired chunks that reserve their identifiers */
  List<ChunkInfo> chunks();

  /** @return the registered chunk with the specified identifier, or {@code null} */
  @Nullable ChunkInfo findChunk(int chunkId);

  /**
   * @return the next chunkId _without registering_ it in persistent metadata;
   * Use {@link #registerNewChunk(int)} fot it
   */
  int nextChunkId();

  /** Registers an initialized chunk after its header reaches persistent storage */
  void registerNewChunk(int chunkId) throws IOException;

  /** Marks the chunk as closed for new block allocation */
  void markChunkSealed(int chunkId) throws IOException;

  /** Marks the chunk for removal; used for that compaction; (should chunk be sealed first?) */
  void markChunkRetired(int chunkId) throws IOException;

  /** @return true when completed metadata changes still require a flush */
  boolean isDirty();

  /// Returns the current catalog metrics.
  ///
  /// @param snapshotMetrics true requires a consistent snapshot; false permits weakly consistent values without locking
  @NotNull DatabaseMetrics.CatalogMetrics metrics(boolean snapshotMetrics);

  /** Flushes all completed metadata changes according to the storage configuration */
  @Override
  void flush() throws IOException;

  /** Forces all completed metadata changes to persistent storage */
  void fsync() throws IOException;

  /** @return true after this storage releases its resources */
  boolean isClosed();

  @Override
  void close() throws IOException;


  // ========================== Structs: ============================================================================== //


  /** The lifecycle state of a chunk */
  enum ChunkState {
    ACTIVE(1),
    SEALED(2),
    RETIRED(3);

    private final int persistentCode;

    ChunkState(int persistentCode) {
      this.persistentCode = persistentCode;
    }

    public int persistentCode() {
      return persistentCode;
    }

    public static @Nullable DatabaseCatalog.ChunkState fromPersistentCode(int persistentCode) {
      for (var state : values()) {
        if (state.persistentCode == persistentCode) {
          return state;
        }
      }
      return null;
    }
  }

  /** A catalog entry for one chunk */
  record ChunkInfo(int chunkId, ChunkState state) {}

  /** A catalog entry for one current store */
  record StoreInfo(int storeId,
                   @NotNull String name,
                   int dataVersion,
                   @NotNull StoreMetadata storeMetadata) {
    StoreInfo(int storeId, @NotNull String name, int dataVersion) {
      this(storeId, name, dataVersion, StoreMetadata.EMPTY);
    }
  }
}
