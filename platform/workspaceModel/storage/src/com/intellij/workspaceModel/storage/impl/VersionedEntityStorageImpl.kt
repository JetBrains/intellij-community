// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.workspaceModel.storage.*
import java.util.concurrent.atomic.AtomicReference

internal class ValuesCache {
  private val cachedValues: Cache<CachedValue<*>, Any?> = Caffeine.newBuilder().build()
  private val cachedValuesWithParameter: Cache<Pair<CachedValueWithParameter<*, *>, *>, Any?> =
    Caffeine.newBuilder().build()

  fun <R> cachedValue(value: CachedValue<R>, storage: EntityStorage): R {
    if (storage is MutableEntityStorage) {
      error("storage must be immutable")
    }

    val o = cachedValues.getIfPresent(value)
    // recursive update - loading get cannot be used
    if (o != null) {
      @Suppress("UNCHECKED_CAST")
      return o as R
    }
    else {
      val newValue = value.source(storage)!!
      cachedValues.put(value, newValue)
      return newValue
    }
  }

  fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P, storage: EntityStorage): R {
    if (storage is MutableEntityStorage) {
      error("storage must be immutable")
    }

    // recursive update - loading get cannot be used
    val o = cachedValuesWithParameter.getIfPresent(value to parameter)
    if (o != null) {
      @Suppress("UNCHECKED_CAST")
      return o as R
    }
    else {
      val newValue = value.source(storage, parameter)!!
      cachedValuesWithParameter.put(value to parameter, newValue)
      return newValue
    }
  }

  fun <R> clearCachedValue(value: CachedValue<R>) {
    cachedValues.invalidate(value)
  }

  fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) {
    cachedValuesWithParameter.invalidate(value to parameter)
  }
}

class VersionedEntityStorageOnBuilder(private val builder: MutableEntityStorage) : VersionedEntityStorage {
  private val currentSnapshot: AtomicReference<StorageSnapshotCache> = AtomicReference()
  private val valuesCache: ValuesCache
    get() = getCurrentSnapshot().cache

  override val version: Long
    get() = builder.modificationCount

  override val current: EntityStorage
    get() = getCurrentSnapshot().storage

  override val base: MutableEntityStorage
    get() = builder

  override fun <R> cachedValue(value: CachedValue<R>): R = valuesCache.cachedValue(value, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, current)

  override fun <R> clearCachedValue(value: CachedValue<R>) = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) =
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

class VersionedEntityStorageOnStorage(private val storage: EntityStorage) : VersionedEntityStorage {
  init {
    if (storage is MutableEntityStorage) error("storage must be immutable, but got: ${storage.javaClass.name}")
  }

  private val valuesCache = ValuesCache()

  override val version: Long
    get() = 0

  override val current: EntityStorage
    get() = storage

  override val base: EntityStorage
    get() = storage

  override fun <R> cachedValue(value: CachedValue<R>): R = valuesCache.cachedValue(value, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, current)

  override fun <R> clearCachedValue(value: CachedValue<R>) = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) =
    valuesCache.clearCachedValue(value, parameter)
}

class DummyVersionedEntityStorage(private val builder: MutableEntityStorage) : VersionedEntityStorage {
  override val version: Long
    get() = builder.modificationCount

  override val current: EntityStorage
    get() = builder

  override val base: EntityStorage
    get() = builder

  override fun <R> cachedValue(value: CachedValue<R>): R = value.source(current)
  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R = value.source(current, parameter)
  override fun <R> clearCachedValue(value: CachedValue<R>) { }
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) {}
}

open class VersionedEntityStorageImpl(initialStorage: EntityStorageSnapshot) : VersionedEntityStorage {
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

  override val current: EntityStorage
    get() = currentPointer.storage

  override val base: EntityStorage
    get() = current

  override val version: Long
    get() = currentPointer.version

  val pointer: Current
    get() = currentPointer

  override fun <R> cachedValue(value: CachedValue<R>): R =
    valuesCache.cachedValue(value, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, current)

  override fun <R> clearCachedValue(value: CachedValue<R>) = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) =
    valuesCache.clearCachedValue(value, parameter)

  class Current(val version: Long, val storage: EntityStorageSnapshot)

  @Volatile
  private var currentPointer: Current = Current(0, initialStorage)

  @Synchronized
  fun replace(newStorage: EntityStorageSnapshot, changes: Map<Class<*>, List<EntityChange<*>>>,
              beforeChanged: (VersionedStorageChange) -> Unit, afterChanged: (VersionedStorageChange) -> Unit) {
    val oldCopy = currentPointer
    if (oldCopy.storage == newStorage) return
    val change = VersionedStorageChangeImpl(this, oldCopy.storage, newStorage, changes)
    beforeChanged(change)
    currentPointer = Current(version = oldCopy.version + 1, storage = newStorage)
    afterChanged(change)
  }

  @Synchronized
  fun replaceSilently(newStorage: EntityStorageSnapshot) {
    val oldCopy = currentPointer
    if (oldCopy.storage == newStorage) return
    currentPointer = Current(version = oldCopy.version + 1, storage = newStorage)
  }
}

private class VersionedStorageChangeImpl(entityStorage: VersionedEntityStorage,
                                         override val storageBefore: EntityStorageSnapshot,
                                         override val storageAfter: EntityStorageSnapshot,
                                         private val changes: Map<Class<*>, List<EntityChange<*>>>) : VersionedStorageChange(
  entityStorage) {
  @Suppress("UNCHECKED_CAST")
  override fun <T : WorkspaceEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>> =
    (changes[entityClass] as? List<EntityChange<T>>) ?: emptyList()

  override fun getAllChanges(): Sequence<EntityChange<*>> = changes.values.asSequence().flatten()
}

private data class StorageSnapshotCache(val storageVersion: Long, val cache: ValuesCache, val storage: EntityStorage)