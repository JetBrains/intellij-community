// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.database.DurableDatabase;
import com.intellij.platform.util.io.storages.database.impl.housekeeping.DatabaseHousekeepingCoordinator;
import com.intellij.platform.util.io.storages.database.impl.housekeeping.DatabaseHousekeepingScheduler;
import com.intellij.platform.util.io.storages.database.spi.housekeeping.Housekeeper;
import com.intellij.platform.util.io.storages.database.spi.housekeeping.HousekeeperInstaller;
import com.intellij.platform.util.io.storages.database.impl.housekeeping.HousekeepingRegistration;
import com.intellij.platform.util.io.storages.database.spi.BlocksDatabase;
import com.intellij.platform.util.io.storages.database.spi.BlocksStore;
import com.intellij.platform.util.io.storages.database.spi.StoreMetadata;
import com.intellij.platform.util.io.storages.database.storages.durablemap.DurableMapOverBlocks;
import com.intellij.platform.util.io.storages.durablemap.DefaultEntryExternalizer;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/// Provides named durable maps over a block database
@ApiStatus.Internal
public final class DurableDatabaseImpl implements DurableDatabase {
  private final @NotNull BlocksDatabase blocksDatabase;

  private final @NotNull DatabaseHousekeepingCoordinator housekeepingCoordinator = new DatabaseHousekeepingCoordinator();

  private final transient Object lock = new Object();
  /// GuardedBy(lock)
  private final Map<String, OpenedMap> openedMapsByName = new HashMap<>();

  /// Set true when [#close] is initiated
  /// GuardedBy(lock)
  private boolean closed;

  /// Creates a facade that owns the specified block database
  public DurableDatabaseImpl(@NotNull BlocksDatabase blocksDatabase) {
    this.blocksDatabase = blocksDatabase;
    if (blocksDatabase instanceof BlocksDatabaseImpl implementation) {
      housekeepingCoordinator.register(new RetireUnusedChunksHousekeeper(implementation));
    }
  }

  @NotNull HousekeepingRegistration registerHousekeeper(@NotNull Housekeeper housekeeper) {
    return housekeepingCoordinator.register(housekeeper);
  }

  @NotNull DatabaseHousekeepingScheduler startHousekeeping(@NotNull ScheduledExecutorService scheduler,
                                                           @NotNull Executor executor) {
    return housekeepingCoordinator.startScheduling(scheduler, executor);
  }

  @Override
  public <K, V> @NotNull DurableMap<K, V> openMap(@NotNull String name,
                                                  int dataVersion,
                                                  @NotNull KeyDescriptorEx<K> keyDescriptor,
                                                  @NotNull DataExternalizerEx<V> valueExternalizer) throws IOException {
    synchronized (lock) {
      ensureNotClosed();
      var cachedMap = openedMapsByName.get(name);
      if (cachedMap != null && !cachedMap.map.isClosed()) {
        return cachedMap.ifCompatible(name, dataVersion);
      }

      var blocksStore = new HousekeepingBlocksStore(
        blocksDatabase.openStore(name, dataVersion),
        housekeepingCoordinator
      );

      var entryExternalizer = new DefaultEntryExternalizer<>(keyDescriptor, valueExternalizer);
      DurableMap<K, V> map;
      EqualityPolicy<? super V> valueEquality = equalityIfSupported(valueExternalizer);
      if (valueExternalizer instanceof PatchableDurableMap.PatchableValueExternalizer<V, ?> patchExternalizer) {
        map = DurableMapOverBlocks.openPatchable(
          blocksStore, DurableMapOverBlocks.DEFAULT_DATA_BLOCK_CONTENT_LENGTH, keyDescriptor,
          valueEquality, entryExternalizer, patchExternalizer
        );
      }
      else {
        map = DurableMapOverBlocks.open(
          blocksStore, DurableMapOverBlocks.DEFAULT_DATA_BLOCK_CONTENT_LENGTH, keyDescriptor,
          valueEquality, entryExternalizer
        );
      }
      openedMapsByName.put(name, new OpenedMap(blocksStore.dataVersion(), map));
      return map;
    }
  }

  @Override
  public <K, V, P> @NotNull PatchableDurableMap<K, V, P> openMap(@NotNull String name,
                                                                 int dataVersion,
                                                                 @NotNull KeyDescriptorEx<K> keyDescriptor,
                                                                 @NotNull PatchableDurableMap.PatchableValueExternalizer<V, P> valueExternalizer)
    throws IOException {
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
      if (cachedMap != null && !cachedMap.map.isClosed()) {
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
      mapsToClose = ContainerUtil.map(openedMapsByName.values(), openedMap -> openedMap.map);
    }

    Throwable failure = null;
    try {
      housekeepingCoordinator.close();
    }
    catch (IOException | RuntimeException | Error e) {
      failure = e;
    }

    for (var map : mapsToClose) {
      try {
        map.close();
      }
      catch (IOException | RuntimeException | Error e) {
        failure = collectFailure(failure, e);
      }
    }

    try {
      blocksDatabase.close();
    }
    catch (IOException | RuntimeException | Error e) {
      failure = collectFailure(failure, e);
    }

    throwFailure(failure);
  }

  private static @NotNull Throwable collectFailure(@Nullable Throwable failure, @NotNull Throwable nextFailure) {
    if (failure == null) {
      return nextFailure;
    }
    failure.addSuppressed(nextFailure);
    return failure;
  }

  private static void throwFailure(@Nullable Throwable failure) throws IOException {
    if (failure instanceof IOException e) {
      throw e;
    }
    if (failure instanceof RuntimeException e) {
      throw e;
    }
    if (failure instanceof Error e) {
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private static <K, V> @NotNull DurableMap<K, V> castMap(@NotNull DurableMap<?, ?> map) {
    return (DurableMap<K, V>)map;
  }

  private record OpenedMap(int dataVersion, @NotNull DurableMap<?, ?> map) {

    @NotNull<K, V> DurableMap<K, V> ifCompatible(@NotNull String name,
                                                 int dataVersion) throws UnsupportedFormatException {
      if (this.dataVersion != dataVersion) {
        throw new UnsupportedFormatException("store '" + name + "'", dataVersion, this.dataVersion);
      }
      return castMap(this.map);
    }
  }

  private void ensureNotClosed() {
    if (closed) {
      throw new IllegalStateException("The database is already closed");
    }
  }

  /// Adds housekeeping registration to a block store without exposing the coordinator to the storage implementation
  static final class HousekeepingBlocksStore implements BlocksStore, HousekeeperInstaller {
    private final @NotNull BlocksStore delegate;
    private final @NotNull DatabaseHousekeepingCoordinator coordinator;

    private @Nullable HousekeepingRegistration registration;
    private boolean registrationClosed;

    HousekeepingBlocksStore(@NotNull BlocksStore delegate,
                            @NotNull DatabaseHousekeepingCoordinator coordinator) {
      this.delegate = delegate;
      this.coordinator = coordinator;
    }

    @Override
    public @NotNull AutoCloseable installHousekeeper(@NotNull Housekeeper housekeeper) {
      synchronized (this) {
        if (registration != null || registrationClosed) {
          throw new IllegalStateException("The store handle already installed a housekeeper");
        }
        registration = coordinator.register(housekeeper);
        return new AutoCloseable() {
          @Override
          public void close() {
            HousekeepingRegistration registrationToClose;
            synchronized (HousekeepingBlocksStore.this) {
              if (registrationClosed) {
                return;
              }
              registrationClosed = true;
              registrationToClose = registration;
            }
            if (registrationToClose != null) {
              registrationToClose.close();
            }
          }
        };
      }
    }

    @Override
    public int dataVersion() {
      return delegate.dataVersion();
    }

    @Override
    public @NotNull StoreMetadata storeMetadata() {
      return delegate.storeMetadata();
    }

    @Override
    public void updateStoreMetadata(@NotNull StoreMetadata storeMetadata) throws IOException {
      delegate.updateStoreMetadata(storeMetadata);
    }

    @Override
    public @NotNull List<Block> blocks() {
      return delegate.blocks();
    }

    @Override
    public @Nullable Block findBlock(int blockId) {
      return delegate.findBlock(blockId);
    }

    @Override
    public @NotNull Block allocateBlock(int role, int minimumContentLength) throws IOException {
      return delegate.allocateBlock(role, minimumContentLength);
    }

    @Override
    public boolean isDirty() {
      return delegate.isDirty();
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void drop() throws IOException {
      delegate.drop();
    }

    @Override
    public String toString() {
      return "HousekeepingBlocksStore{wrapped: " + delegate + "}";
    }
  }
}
