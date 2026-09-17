// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.database.DurableDatabase;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapOverBlocks;
import com.intellij.platform.util.io.storages.durablemap.DefaultEntryExternalizer;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Provides named durable maps over a block database
@ApiStatus.Internal
public final class DurableDatabaseImpl implements DurableDatabase {
  private final @NotNull BlocksDatabase blocksDatabase;

  private final transient Object lock = new Object();
  /// It is either [DurableMap], or [PatchableDurableMap].
  /// GuardedBy(lock)
  private final Map<String, DurableMap<?, ?>> openedMapsByName = new HashMap<>();

  /// Set true when [#close] is initiated
  /// GuardedBy(lock)
  private boolean closed;

  /// Creates a facade that owns the specified block database
  public DurableDatabaseImpl(@NotNull BlocksDatabase blocksDatabase) {
    this.blocksDatabase = blocksDatabase;
  }

  @Override
  public <K, V> @NotNull DurableMap<K, V> openMap(@NotNull String name,
                                                  int dataVersion,
                                                  @NotNull KeyDescriptorEx<K> keyDescriptor,
                                                  @NotNull DataExternalizerEx<V> valueExternalizer) throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      var store = blocksDatabase.openStore(name, dataVersion);

      var cachedMap = openedMapsByName.get(name);
      if (cachedMap != null && !cachedMap.isClosed()) {
        return castMap(cachedMap);
      }

      var entryExternalizer = new DefaultEntryExternalizer<>(keyDescriptor, valueExternalizer);
      DurableMap<K, V> map;
      EqualityPolicy<? super V> valueEquality = equalityIfSupported(valueExternalizer);
      if (valueExternalizer instanceof PatchableDurableMap.PatchableValueExternalizer<V, ?> patchExternalizer) {
        map = DurableMapOverBlocks.openPatchable(
          store, DurableMapOverBlocks.DEFAULT_DATA_BLOCK_CONTENT_LENGTH, keyDescriptor,
          valueEquality, entryExternalizer, patchExternalizer
        );
      }
      else {
        map = DurableMapOverBlocks.open(
          store, DurableMapOverBlocks.DEFAULT_DATA_BLOCK_CONTENT_LENGTH, keyDescriptor,
          valueEquality, entryExternalizer
        );
      }
      openedMapsByName.put(name, map);
      return map;
    }
  }

  @Override
  public <K, V, P> @NotNull PatchableDurableMap<K, V, P> openMap(@NotNull String name,
                                                                int dataVersion,
                                                                @NotNull KeyDescriptorEx<K> keyDescriptor,
                                                                @NotNull PatchableDurableMap.PatchableValueExternalizer<V, P> valueExternalizer) throws IOException {
    var map = openMap(name, dataVersion, keyDescriptor, (DataExternalizerEx<V>)valueExternalizer);
    if (!(map instanceof PatchableDurableMap<?, ?, ?>)) {
      throw new IllegalStateException("The map is already open without patch support: " + name);
    }
    return (PatchableDurableMap<K, V, P>)map;
  }

  @SuppressWarnings("unchecked")
  private static <V> @Nullable EqualityPolicy<? super V> equalityIfSupported(@NotNull DataExternalizerEx<V> externalizer) {
    return externalizer instanceof EqualityPolicy<?> ? (EqualityPolicy<? super V>)externalizer : null;
  }

  @Override
  public @NotNull List<String> mapNames() {
    synchronized (lock) {
      ensureNotClosed();
      return blocksDatabase.storeNames();
    }
  }

  @Override
  public void dropMap(@NotNull String name) throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      var cachedMap = openedMapsByName.get(name);
      if (cachedMap != null && !cachedMap.isClosed()) {
        throw new IllegalStateException("The map is still open: " + name);
      }
      openedMapsByName.remove(name, cachedMap);

      var store = blocksDatabase.findStore(name);
      if (store != null) {
        store.drop();
      }
    }
  }

  @Override
  public boolean isDirty() {
    return blocksDatabase.isDirty();
  }

  @Override
  public void flush() throws IOException {
    blocksDatabase.flush();
  }

  @Override
  public boolean isClosed() {
    synchronized (lock) {
      return closed;
    }
  }

  @Override
  public void close() throws IOException {
    List<DurableMap<?, ?>> mapsToClose;
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
      mapsToClose = List.copyOf(openedMapsByName.values());
    }

    IOException failure = null;
    for (var map : mapsToClose) {
      try {
        map.close();
      }
      catch (IOException e) {
        if (failure == null) {
          failure = e;
        }
        else {
          failure.addSuppressed(e);
        }
      }
    }

    try {
      blocksDatabase.close();
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

  @SuppressWarnings("unchecked")
  private static <K, V> @NotNull DurableMap<K, V> castMap(@NotNull DurableMap<?, ?> map) {
    return (DurableMap<K, V>)map;
  }

  private void ensureNotClosed() {
    if (closed) {
      throw new IllegalStateException("The database is already closed");
    }
  }
}
