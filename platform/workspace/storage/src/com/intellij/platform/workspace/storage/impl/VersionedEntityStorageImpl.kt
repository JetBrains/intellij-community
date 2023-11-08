// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMs
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMs
import com.intellij.platform.workspace.storage.*
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private class ValuesCache {
  private val cachedValues: Cache<CachedValue<*>, Any?> = Caffeine.newBuilder().build()
  private val cachedValuesWithParameter: Cache<Pair<CachedValueWithParameter<*, *>, *>, Any?> =
    Caffeine.newBuilder().build()

  fun <R> cachedValue(value: CachedValue<R>, storage: EntityStorageSnapshot): R {
    val start = System.currentTimeMillis()
    val o: Any? = cachedValues.getIfPresent(value)
    var valueToReturn: R? = null

    // recursive update - loading get cannot be used
    if (o != null) {
      @Suppress("UNCHECKED_CAST")
      valueToReturn = o as R
      cachedValueFromCacheMs.addElapsedTimeMs(start)
    }
    else {
      cachedValueCalculatedMs.addMeasuredTimeMs {
        valueToReturn = value.source(storage)!!
        cachedValues.put(value, valueToReturn)
      }
    }

    return requireNotNull(valueToReturn) { "Cached value must not be null" }
  }

  fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P, storage: EntityStorageSnapshot): R {
    val start = System.currentTimeMillis()
    // recursive update - loading get cannot be used
    val o = cachedValuesWithParameter.getIfPresent(value to parameter)
    var valueToReturn: R? = null

    if (o != null) {
      @Suppress("UNCHECKED_CAST")
      valueToReturn = o as R
      cachedValueWithParametersFromCacheMs.addElapsedTimeMs(start)
    }
    else {
      cachedValueWithParametersCalculatedMs.addMeasuredTimeMs {
        valueToReturn = value.source(storage, parameter)!!
        cachedValuesWithParameter.put(value to parameter, valueToReturn)
      }
    }

    return requireNotNull(valueToReturn) { "Cached value with parameter must not be null" }
  }

  fun <R> clearCachedValue(value: CachedValue<R>) {
    cachedValueClear.addMeasuredTimeMs { cachedValues.invalidate(value) }
  }

  fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) {
    cachedValueWithParametersClear.addMeasuredTimeMs { cachedValuesWithParameter.invalidate(value to parameter) }
  }

  companion object {
    private val cachedValueFromCacheMs: AtomicLong = AtomicLong()
    private val cachedValueCalculatedMs: AtomicLong = AtomicLong()

    private val cachedValueWithParametersFromCacheMs: AtomicLong = AtomicLong()
    private val cachedValueWithParametersCalculatedMs: AtomicLong = AtomicLong()

    private val cachedValueClear: AtomicLong = AtomicLong()
    private val cachedValueWithParametersClear: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val cachedValueFromCacheGauge = meter.gaugeBuilder("workspaceModel.cachedValue.from.cache.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val cachedValueCalculatedGauge = meter.gaugeBuilder("workspaceModel.cachedValue.calculated.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val cachedValueTotalGauge = meter.gaugeBuilder("workspaceModel.cachedValue.total.get.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      val cachedValueWithParametersFromCacheGauge = meter.gaugeBuilder("workspaceModel.cachedValueWithParameters.from.cache.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val cachedValueWithParametersCalculatedGauge = meter.gaugeBuilder("workspaceModel.cachedValueWithParameters.calculated.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val cachedValueWithParametersTotalGauge = meter.gaugeBuilder("workspaceModel.cachedValueWithParameters.total.get.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      val cachedValueClearGauge = meter.gaugeBuilder("workspaceModel.cachedValue.clear.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val cachedValueWithParametersClearGauge = meter.gaugeBuilder("workspaceModel.cachedValueWithParameters.clear.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          cachedValueFromCacheGauge.record(cachedValueFromCacheMs.get())
          cachedValueCalculatedGauge.record(cachedValueCalculatedMs.get())
          cachedValueTotalGauge.record(cachedValueFromCacheMs.get().plus(cachedValueCalculatedMs.get()))

          cachedValueWithParametersFromCacheGauge.record(cachedValueWithParametersFromCacheMs.get())
          cachedValueWithParametersCalculatedGauge.record(cachedValueWithParametersCalculatedMs.get())
          cachedValueWithParametersTotalGauge.record(
            cachedValueWithParametersFromCacheMs.get().plus(cachedValueWithParametersCalculatedMs.get())
          )

          cachedValueClearGauge.record(cachedValueClear.get())
          cachedValueWithParametersClearGauge.record(cachedValueWithParametersClear.get())
        },
        cachedValueFromCacheGauge, cachedValueCalculatedGauge, cachedValueTotalGauge,

        cachedValueWithParametersFromCacheGauge, cachedValueWithParametersCalculatedGauge,
        cachedValueWithParametersTotalGauge,

        cachedValueClearGauge, cachedValueWithParametersClearGauge
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
    get() = builder.modificationCount

  override val current: EntityStorageSnapshot
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
    if (snapshotCache == null || builder.modificationCount != snapshotCache.storageVersion) {
      val storageSnapshotCache = StorageSnapshotCache(builder.modificationCount, ValuesCache(), builder.toSnapshot())
      currentSnapshot.set(storageSnapshotCache)
      return storageSnapshotCache
    }
    return snapshotCache
  }
}

public class VersionedEntityStorageOnSnapshot(private val storage: EntityStorageSnapshot) : VersionedEntityStorage {
  private val valuesCache = ValuesCache()

  override val version: Long
    get() = 0

  override val current: EntityStorageSnapshot
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
    get() = builder.modificationCount

  override val current: EntityStorage
    get() = builder

  override val base: EntityStorage
    get() = builder

  override fun <R> cachedValue(value: CachedValue<R>): R = value.source(current)
  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R = value.source(current, parameter)
  override fun <R> clearCachedValue(value: CachedValue<R>) {}
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) {}
}

public open class VersionedEntityStorageImpl(initialStorage: EntityStorageSnapshot) : VersionedEntityStorage {
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

  override val current: EntityStorageSnapshot
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

  public class Current(public val version: Long, public val storage: EntityStorageSnapshot)

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
  public fun replace(newStorage: EntityStorageSnapshot, changes: Map<Class<*>, List<EntityChange<*>>>,
                     beforeChanged: (VersionedStorageChange) -> Unit, afterChanged: (VersionedStorageChange) -> Unit) {
    val oldCopy = currentPointer
    if (oldCopy.storage == newStorage) return
    val change = VersionedStorageChangeImpl(this, oldCopy.storage, newStorage, changes)
    beforeChanged(change)
    currentPointer = Current(version = oldCopy.version + 1, storage = newStorage)
    afterChanged(change)
  }
}

private class VersionedStorageChangeImpl(
  entityStorage: VersionedEntityStorage,
  override val storageBefore: EntityStorageSnapshot,
  override val storageAfter: EntityStorageSnapshot,
  private val changes: Map<Class<*>, List<EntityChange<*>>>
) : VersionedStorageChange(entityStorage) {
  @Suppress("UNCHECKED_CAST")
  override fun <T : WorkspaceEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>> {
    return (changes[entityClass] as? List<EntityChange<T>>) ?: emptyList()
  }

  override fun getAllChanges(): Sequence<EntityChange<*>> = changes.values.asSequence().flatten()
}

private data class StorageSnapshotCache(val storageVersion: Long, val cache: ValuesCache, val storage: EntityStorageSnapshot)