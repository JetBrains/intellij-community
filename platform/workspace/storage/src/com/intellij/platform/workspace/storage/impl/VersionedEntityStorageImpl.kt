// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.*
import com.intellij.platform.workspace.storage.*
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private class ValuesCache {
  private val cachedValues: Cache<CachedValue<*>, Any?> = Caffeine.newBuilder().build()
  private val cachedValuesWithParameter: Cache<Pair<CachedValueWithParameter<*, *>, *>, Any?> =
    Caffeine.newBuilder().build()

  fun <R> cachedValue(value: CachedValue<R>, storage: EntityStorageSnapshot): R {
    val start = System.nanoTime()
    val o: Any? = cachedValues.getIfPresent(value)
    var valueToReturn: R? = null

    // recursive update - loading get cannot be used
    if (o != null) {
      @Suppress("UNCHECKED_CAST")
      valueToReturn = o as R
      cachedValueFromCacheNanosec.addElapsedTimeNanosec(start)
    }
    else {
      cachedValueCalculatedNanosec.addMeasuredTimeNanosec {
        valueToReturn = value.source(storage)!!
        cachedValues.put(value, valueToReturn)
      }
    }

    return requireNotNull(valueToReturn) { "Cached value must not be null" }
  }

  fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P, storage: EntityStorageSnapshot): R {
    val start = System.nanoTime()
    // recursive update - loading get cannot be used
    val o = cachedValuesWithParameter.getIfPresent(value to parameter)
    var valueToReturn: R? = null

    if (o != null) {
      @Suppress("UNCHECKED_CAST")
      valueToReturn = o as R
      cachedValueWithParametersFromCacheNanosec.addElapsedTimeNanosec(start)
    }
    else {
      cachedValueWithParametersCalculatedNanosec.addMeasuredTimeNanosec {
        valueToReturn = value.source(storage, parameter)!!
        cachedValuesWithParameter.put(value to parameter, valueToReturn)
      }
    }

    return requireNotNull(valueToReturn) { "Cached value with parameter must not be null" }
  }

  fun <R> clearCachedValue(value: CachedValue<R>) {
    cachedValueClearNanosec.addMeasuredTimeNanosec { cachedValues.invalidate(value) }
  }

  fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) {
    cachedValueWithParametersClearNanosec.addMeasuredTimeNanosec { cachedValuesWithParameter.invalidate(value to parameter) }
  }

  companion object {
    private val cachedValueFromCacheNanosec: AtomicLong = AtomicLong()
    private val cachedValueCalculatedNanosec: AtomicLong = AtomicLong()

    private val cachedValueWithParametersFromCacheNanosec: AtomicLong = AtomicLong()
    private val cachedValueWithParametersCalculatedNanosec: AtomicLong = AtomicLong()

    private val cachedValueClearNanosec: AtomicLong = AtomicLong()
    private val cachedValueWithParametersClearNanosec: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val cachedValueFromCacheCounter = meter.counterBuilder("workspaceModel.cachedValue.from.cache.ms").buildObserver()
      val cachedValueCalculatedCounter = meter.counterBuilder("workspaceModel.cachedValue.calculated.ms").buildObserver()
      val cachedValueTotalCounter = meter.counterBuilder("workspaceModel.cachedValue.total.get.ms").buildObserver()

      val cachedValueWithParametersFromCacheCounter = meter.counterBuilder("workspaceModel.cachedValueWithParameters.from.cache.ms").buildObserver()
      val cachedValueWithParametersCalculatedCounter = meter.counterBuilder("workspaceModel.cachedValueWithParameters.calculated.ms").buildObserver()
      val cachedValueWithParametersTotalCounter = meter.counterBuilder("workspaceModel.cachedValueWithParameters.total.get.ms").buildObserver()

      val cachedValueClearCounter = meter.counterBuilder("workspaceModel.cachedValue.clear.ms").buildObserver()
      val cachedValueWithParametersClearCounter = meter.counterBuilder("workspaceModel.cachedValueWithParameters.clear.ms").buildObserver()

      meter.batchCallback(
        {
          cachedValueFromCacheCounter.record(cachedValueFromCacheNanosec.fromNanosecToMillis())
          cachedValueCalculatedCounter.record(cachedValueCalculatedNanosec.fromNanosecToMillis())
          cachedValueTotalCounter.record(cachedValueFromCacheNanosec.fromNanosecToMillis().plus(cachedValueCalculatedNanosec.fromNanosecToMillis()))

          cachedValueWithParametersFromCacheCounter.record(cachedValueWithParametersFromCacheNanosec.fromNanosecToMillis())
          cachedValueWithParametersCalculatedCounter.record(cachedValueWithParametersCalculatedNanosec.fromNanosecToMillis())
          cachedValueWithParametersTotalCounter.record(
            cachedValueWithParametersFromCacheNanosec.fromNanosecToMillis().plus(cachedValueWithParametersCalculatedNanosec.fromNanosecToMillis())
          )

          cachedValueClearCounter.record(cachedValueClearNanosec.fromNanosecToMillis())
          cachedValueWithParametersClearCounter.record(cachedValueWithParametersClearNanosec.fromNanosecToMillis())
        },
        cachedValueFromCacheCounter, cachedValueCalculatedCounter, cachedValueTotalCounter,

        cachedValueWithParametersFromCacheCounter, cachedValueWithParametersCalculatedCounter,
        cachedValueWithParametersTotalCounter,

        cachedValueClearCounter, cachedValueWithParametersClearCounter
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