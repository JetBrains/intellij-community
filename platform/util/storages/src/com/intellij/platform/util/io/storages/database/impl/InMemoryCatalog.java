// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.ACTIVE;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.RETIRED;
import static com.intellij.platform.util.io.storages.database.impl.DatabaseCatalog.ChunkState.SEALED;

/**
 * The current database catalog.
 * Not thread-safe.
 */
final class InMemoryCatalog {
  private final Map<Integer, DatabaseCatalog.ChunkInfo> chunksById = new LinkedHashMap<>();
  private int lastChunkId;

  private final Map<Integer, DatabaseCatalog.StoreInfo> storesById = new LinkedHashMap<>();
  private final Map<String, DatabaseCatalog.StoreInfo> storesByName = new LinkedHashMap<>();
  private int lastStoreId;

  /** @return the next identifier without changing the recovered catalog */
  int nextStoreId() {
    if (lastStoreId == Integer.MAX_VALUE) {
      throw new IllegalStateException("The database exhausted all positive store identifiers");
    }
    return lastStoreId + 1;
  }

  /** @return a stable snapshot in identifier order */
  @NotNull List<DatabaseCatalog.StoreInfo> stores() {
    return List.copyOf(storesById.values());
  }

  @Nullable DatabaseCatalog.StoreInfo findStore(@NotNull String name) {
    return storesByName.get(name);
  }

  void validateStoreCreation(int storeId, @NotNull String name) {
    Objects.requireNonNull(name, "name");
    if (storeId <= 0) {
      throw new IllegalArgumentException("storeId(=" + storeId + ") must be positive");
    }
    if (storeId <= lastStoreId) {
      throw new IllegalStateException("storeId(=" + storeId + ") must be greater than the last storeId(=" + lastStoreId + ")");
    }
    var existingStore = storesByName.get(name);
    if (existingStore != null) {
      throw new IllegalStateException("Store name is already registered by storeId(=" + existingStore.storeId() + "): " + name);
    }
  }

  /** Applies a creation record after its append or during recovery. */
  void addStore(int storeId, @NotNull String name, int dataVersion) {
    validateStoreCreation(storeId, name);
    var store = new DatabaseCatalog.StoreInfo(storeId, name, dataVersion);
    storesById.put(storeId, store);
    storesByName.put(name, store);
    lastStoreId = storeId;
  }

  void validateStoreDrop(int storeId) {
    if (!storesById.containsKey(storeId)) {
      throw new IllegalArgumentException("Unknown storeId(=" + storeId + ")");
    }
  }

  /** Applies a drop record while keeping the identifier reserved. */
  void dropStore(int storeId) {
    validateStoreDrop(storeId);
    var store = storesById.remove(storeId);
    storesByName.remove(store.name());
  }

  /** @return the next identifier without changing the recovered catalog */
  int nextChunkId() {
    if (lastChunkId == Integer.MAX_VALUE) {
      throw new IllegalStateException("The database exhausted all positive chunk identifiers");
    }
    return lastChunkId + 1;
  }

  /** @return a stable snapshot in identifier order */
  @NotNull List<DatabaseCatalog.ChunkInfo> chunks() {
    return List.copyOf(chunksById.values());
  }

  @Nullable DatabaseCatalog.ChunkInfo findChunk(int chunkId) {
    return chunksById.get(chunkId);
  }

  /** Applies a creation record after its append or during recovery */
  void addChunk(int chunkId) {
    if (chunkId <= 0) {
      throw new IllegalArgumentException("chunkId(=" + chunkId + ") must be positive");
    }
    if (chunkId <= lastChunkId) {
      throw new IllegalStateException("chunkId(=" + chunkId + ") must be greater than the last chunkId(=" + lastChunkId + ")");
    }

    chunksById.put(chunkId, new DatabaseCatalog.ChunkInfo(chunkId, ACTIVE));
    lastChunkId = chunkId;
  }

  void validateTransition(int chunkId, @NotNull DatabaseCatalog.ChunkState expectedState) {
    var chunk = chunksById.get(chunkId);
    if (chunk == null) {
      throw new IllegalArgumentException("Unknown chunkId(=" + chunkId + ")");
    }
    if (chunk.state() != expectedState) {
      throw new IllegalStateException(
        "chunkId(=" + chunkId + ") is " + chunk.state() + ", expected " + expectedState
      );
    }
  }

  /** Changes the chunk state from active to sealed. */
  void sealChunk(int chunkId) {
    validateTransition(chunkId, ACTIVE);
    replaceState(chunkId, SEALED);
  }

  /** Applies the only valid transition from sealed to retired. */
  void retireChunk(int chunkId) {
    validateTransition(chunkId, SEALED);
    replaceState(chunkId, RETIRED);
  }

  private void replaceState(int chunkId, @NotNull DatabaseCatalog.ChunkState newState) {
    var chunk = chunksById.get(chunkId);
    chunksById.put(chunkId, new DatabaseCatalog.ChunkInfo(chunk.chunkId(), newState));
  }
}
