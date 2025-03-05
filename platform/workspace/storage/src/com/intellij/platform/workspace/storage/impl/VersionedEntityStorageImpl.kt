// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private class ValuesCache {
  private val cachedValues: Cache<CachedValue<*>, Any?> = Caffeine.newBuilder().build()
  private val cachedValuesWithParameter: Cache<Pair<CachedValueWithParameter<*, *>, *>, Any?> =
    Caffeine.newBuilder().build()

  fun <R> cachedValue(value: CachedValue<R>, storage: ImmutableEntityStorage): R {
    val o: Any? = cachedValues.getIfPresent(value)
    var valueToReturn: R? = null

    // recursive update - loading get cannot be used
    if (o != null) {
      @Suppress("UNCHECKED_CAST")
      valueToReturn = o as R
      cachedValueFromCacheCounter.incrementAndGet()
    }
    else {
      cachedValueCalculatedCounter.incrementAndGet()
      valueToReturn = value.source(storage)!!
      cachedValues.put(value, valueToReturn)
    }

    return requireNotNull(valueToReturn) { "Cached value must not be null" }
  }

  fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P, storage: ImmutableEntityStorage): R {
    // recursive update - loading get cannot be used
    val o = cachedValuesWithParameter.getIfPresent(value to parameter)
    var valueToReturn: R? = null

    if (o != null) {
      @Suppress("UNCHECKED_CAST")
      valueToReturn = o as R
      cachedValueWithParametersFromCacheCounter.incrementAndGet()
    }
    else {
      cachedValueWithParametersCalculatedCounter.incrementAndGet()
      valueToReturn = value.source(storage, parameter)!!
      cachedValuesWithParameter.put(value to parameter, valueToReturn)
    }

    return requireNotNull(valueToReturn) { "Cached value with parameter must not be null" }
  }

  fun <R> clearCachedValue(value: CachedValue<R>) {
    cachedValueClearCounter.incrementAndGet()
    cachedValues.invalidate(value)
  }

  fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) {
    cachedValueWithParametersClearCounter.incrementAndGet()
    cachedValuesWithParameter.invalidate(value to parameter)
  }

  companion object {
    private val cachedValueFromCacheCounter = AtomicLong()
    private val cachedValueCalculatedCounter = AtomicLong()

    private val cachedValueWithParametersFromCacheCounter = AtomicLong()
    private val cachedValueWithParametersCalculatedCounter = AtomicLong()

    private val cachedValueClearCounter = AtomicLong()
    private val cachedValueWithParametersClearCounter = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val cachedValueFromCacheMeterCounter = meter.counterBuilder("workspaceModel.cachedValue.from.cache.count").buildObserver()
      val cachedValueCalculatedMeterCounter = meter.counterBuilder("workspaceModel.cachedValue.calculated.count").buildObserver()
      val cachedValueTotalMeterCounter = meter.counterBuilder("workspaceModel.cachedValue.total.get.count").buildObserver()

      val cachedValueWithParametersFromCacheMeterCounter = meter.counterBuilder("workspaceModel.cachedValueWithParameters.from.cache.count").buildObserver()
      val cachedValueWithParametersCalculatedMeterCounter = meter.counterBuilder("workspaceModel.cachedValueWithParameters.calculated.count").buildObserver()
      val cachedValueWithParametersTotalMeterCounter = meter.counterBuilder("workspaceModel.cachedValueWithParameters.total.get.count").buildObserver()

      val cachedValueClearMeterCounter = meter.counterBuilder("workspaceModel.cachedValue.clear.count").buildObserver()
      val cachedValueWithParametersClearMeterCounter = meter.counterBuilder("workspaceModel.cachedValueWithParameters.clear.count").buildObserver()

      meter.batchCallback(
        {
          cachedValueFromCacheMeterCounter.record(cachedValueFromCacheCounter.get())
          cachedValueCalculatedMeterCounter.record(cachedValueCalculatedCounter.get())
          cachedValueTotalMeterCounter.record(cachedValueFromCacheCounter.get().plus(cachedValueCalculatedCounter.get()))

          cachedValueWithParametersFromCacheMeterCounter.record(cachedValueWithParametersFromCacheCounter.get())
          cachedValueWithParametersCalculatedMeterCounter.record(cachedValueWithParametersCalculatedCounter.get())
          cachedValueWithParametersTotalMeterCounter.record(
            cachedValueWithParametersFromCacheCounter.get().plus(cachedValueWithParametersCalculatedCounter.get())
          )

          cachedValueClearMeterCounter.record(cachedValueClearCounter.get())
          cachedValueWithParametersClearMeterCounter.record(cachedValueWithParametersClearCounter.get())
        },
        cachedValueFromCacheMeterCounter, cachedValueCalculatedMeterCounter, cachedValueTotalMeterCounter,

        cachedValueWithParametersFromCacheMeterCounter, cachedValueWithParametersCalculatedMeterCounter,
        cachedValueWithParametersTotalMeterCounter,

        cachedValueClearMeterCounter, cachedValueWithParametersClearMeterCounter
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(WorkspaceModel))
    }
  }
}

public class VersionedEntityStorageOnBuilder(private val builder: MutableEntityStorage) : VersionedEntityStorage {
  private val currentSnapshot: AtomicReference<StorageSnapshotCache> = AtomicReference()
  private val valuesCache: ValuesCache
    get() = getCurrentSnapshot().cache

  override val version: Long
    get() = builder.instrumentation.modificationCount

  override val current: ImmutableEntityStorage
    get() = getCurrentSnapshot().storage

  override val base: MutableEntityStorage
    get() = builder

  override fun <R> cachedValue(value: CachedValue<R>): R = valuesCache.cachedValue(value, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, current)

  override fun <R> clearCachedValue(value: CachedValue<R>): Unit = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P): Unit =
    valuesCache.clearCachedValue(value, parameter)

  private fun getCurrentSnapshot(): StorageSnapshotCache {
    val snapshotCache = currentSnapshot.get()
    if (snapshotCache == null || builder.instrumentation.modificationCount != snapshotCache.storageVersion) {
      val storageSnapshotCache = StorageSnapshotCache(builder.instrumentation.modificationCount, ValuesCache(), builder.toSnapshot())
      currentSnapshot.set(storageSnapshotCache)
      return storageSnapshotCache
    }
    return snapshotCache
  }
}

public class VersionedEntityStorageOnSnapshot(private val storage: ImmutableEntityStorage) : VersionedEntityStorage {
  private val valuesCache = ValuesCache()

  override val version: Long
    get() = 0

  override val current: ImmutableEntityStorage
    get() = storage

  override val base: EntityStorage
    get() = storage

  override fun <R> cachedValue(value: CachedValue<R>): R = valuesCache.cachedValue(value, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, current)

  override fun <R> clearCachedValue(value: CachedValue<R>): Unit = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P): Unit =
    valuesCache.clearCachedValue(value, parameter)
}

public class DummyVersionedEntityStorage(private val builder: MutableEntityStorage) : VersionedEntityStorage {
  override val version: Long
    get() = builder.instrumentation.modificationCount

  override val current: EntityStorage
    get() = builder

  override val base: EntityStorage
    get() = builder

  override fun <R> cachedValue(value: CachedValue<R>): R = value.source(current)
  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R = value.source(current, parameter)
  override fun <R> clearCachedValue(value: CachedValue<R>) {}
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) {}
}

public open class VersionedEntityStorageImpl(initialStorage: ImmutableEntityStorage) : VersionedEntityStorage {
  private val currentSnapshot: AtomicReference<StorageSnapshotCache> = AtomicReference()
  private val valuesCache: ValuesCache
    get() {
      val snapshotCache = currentSnapshot.get()
      if (snapshotCache == null || version != snapshotCache.storageVersion) {
        val cache = ValuesCache()
        currentSnapshot.set(StorageSnapshotCache(version, cache, current))
        return cache
      }
      return snapshotCache.cache
    }

  override val current: ImmutableEntityStorage
    get() = currentPointer.storage

  override val base: EntityStorage
    get() = current

  override val version: Long
    get() = currentPointer.version

  public val pointer: Current
    get() = currentPointer

  override fun <R> cachedValue(value: CachedValue<R>): R =
    valuesCache.cachedValue(value, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, current)

  override fun <R> clearCachedValue(value: CachedValue<R>): Unit = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P): Unit =
    valuesCache.clearCachedValue(value, parameter)

  public class Current(public val version: Long, public val storage: ImmutableEntityStorage)

  @Volatile
  private var currentPointer: Current = Current(0, initialStorage)

  /**
   * About [changes] parameter:
   * Here is a bit weird situation that we collect changes and pass them in this function as the parameter.
   *   Moreover, we collect changes even if two storages are equal.
   * Unfortunately, we have to do it because we initialize bridges on the base of the changes.
   * We may calculate the change in this function as we won't need the changes for bridges initialization.
   */
  @Synchronized
  public fun replace(
    newStorage: ImmutableEntityStorage,
    changes: Map<Class<*>, List<EntityChange<*>>>,
    beforeChanged: (VersionedStorageChange) -> Unit,
    afterChanged: (VersionedStorageChange) -> Unit,
  ) {
    val oldCopy = currentPointer
    if (oldCopy.storage == newStorage) return
    val change = VersionedStorageChangeImpl(oldCopy.storage, newStorage, changes)
    try {
      (newStorage as? AbstractEntityStorage)?.isEventHandling = true
      beforeChanged(change)
      currentPointer = Current(version = oldCopy.version + 1, storage = newStorage)
      afterChanged(change)
    }
    finally {
      (newStorage as? AbstractEntityStorage)?.isEventHandling = false
    }
  }
}

@ApiStatus.Internal
public interface VersionedStorageChangeInternal : VersionedStorageChange {
  /** Use [getChanges] to process changes of the specific entities. */
  @ApiStatus.Internal
  @ApiStatus.Obsolete
  public fun getAllChanges(): Sequence<EntityChange<*>>
}

private class VersionedStorageChangeImpl(
  override val storageBefore: ImmutableEntityStorage,
  override val storageAfter: ImmutableEntityStorage,
  private val changes: Map<Class<*>, List<EntityChange<*>>>,
) : VersionedStorageChangeInternal {
  @Suppress("UNCHECKED_CAST")
  override fun <T : WorkspaceEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>> {
    return (changes[entityClass] as? List<EntityChange<T>>) ?: emptyList()
  }

  override fun getAllChanges(): Sequence<EntityChange<*>> = changes.values.asSequence().flatten()
}

private data class StorageSnapshotCache(val storageVersion: Long, val cache: ValuesCache, val storage: ImmutableEntityStorage)