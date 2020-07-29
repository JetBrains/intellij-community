// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.workspaceModel.storage.*
import java.util.concurrent.atomic.AtomicReference

internal class ValuesCache {
  private data class ValuesCacheData(val version: Long, val value: Any?)

  private val cachedValues: Cache<CachedValue<*>, ValuesCacheData> = CacheBuilder.newBuilder().build()
  private val cachedValuesWithParameter: Cache<Pair<CachedValueWithParameter<*, *>, *>, ValuesCacheData> =
    CacheBuilder.newBuilder().build()

  fun <R> cachedValue(value: CachedValue<R>, version: Long, storage: WorkspaceEntityStorage): R {
    if (storage is WorkspaceEntityStorageBuilder) error("storage must be immutable")
    val o = cachedValues.getIfPresent(value)
    if (o != null && o.version == version) {
      @Suppress("UNCHECKED_CAST")
      return o.value as R
    }
    else {
      val newValue = value.source(storage)
      cachedValues.put(value, ValuesCacheData(version, newValue))
      return newValue
    }
  }

  fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P, version: Long, storage: WorkspaceEntityStorage): R {
    if (storage is WorkspaceEntityStorageBuilder) error("storage must be immutable")
    val o = cachedValuesWithParameter.getIfPresent(value to parameter)
    if (o != null && o.version == version) {
      @Suppress("UNCHECKED_CAST")
      return o.value as R
    }
    else {
      val newValue = value.source(storage, parameter)
      cachedValuesWithParameter.put(value to parameter, ValuesCacheData(version, newValue))
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

class VersionedEntityStorageOnBuilder(private val builder: WorkspaceEntityStorageBuilder) : VersionedEntityStorage {

  private val valuesCache = ValuesCache()
  private val currentSnapshot: AtomicReference<Pair<Long, WorkspaceEntityStorage>> = AtomicReference()

  override val version: Long
    get() = builder.modificationCount

  override val current: WorkspaceEntityStorage
    get() {
      val pair = currentSnapshot.get()
      if (pair == null || builder.modificationCount != pair.first) {
        val snapshot = builder.toStorage()
        val count = builder.modificationCount
        currentSnapshot.set(count to snapshot)
        return snapshot
      }

      return pair.second
    }

  override fun <R> cachedValue(value: CachedValue<R>): R = valuesCache.cachedValue(value, version, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, version, current)

  override fun <R> clearCachedValue(value: CachedValue<R>) = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) =
    valuesCache.clearCachedValue(value, parameter)
}

class VersionedEntityStorageOnStorage(private val storage: WorkspaceEntityStorage) : VersionedEntityStorage {
  init {
    if (storage is WorkspaceEntityStorageBuilder) error("storage must be immutable, but got: ${storage.javaClass.name}")
  }

  private val valuesCache = ValuesCache()

  override val version: Long
    get() = 0

  override val current: WorkspaceEntityStorage
    get() = storage

  override fun <R> cachedValue(value: CachedValue<R>): R = valuesCache.cachedValue(value, version, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, version, current)

  override fun <R> clearCachedValue(value: CachedValue<R>) = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) =
    valuesCache.clearCachedValue(value, parameter)
}

open class VersionedEntityStorageImpl(initialStorage: WorkspaceEntityStorage) : VersionedEntityStorage {

  private val valuesCache = ValuesCache()

  override val current: WorkspaceEntityStorage
    get() = currentPointer.storage

  override val version: Long
    get() = currentPointer.version

  override fun <R> cachedValue(value: CachedValue<R>): R =
    valuesCache.cachedValue(value, version, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, version, current)

  override fun <R> clearCachedValue(value: CachedValue<R>) = valuesCache.clearCachedValue(value)
  override fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P) =
    valuesCache.clearCachedValue(value, parameter)

  private class Current(val version: Long, val storage: WorkspaceEntityStorage)

  @Volatile
  private var currentPointer: Current = Current(0, initialStorage)

  @Synchronized
  fun replace(newStorage: WorkspaceEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>,
              beforeChanged: (VersionedStorageChange) -> Unit, afterChanged: (VersionedStorageChange) -> Unit) {
    val oldCopy = currentPointer
    if (oldCopy.storage == newStorage) return
    val change = VersionedStorageChangeImpl(this, oldCopy.storage, newStorage, changes)
    beforeChanged(change)
    currentPointer = Current(version = oldCopy.version + 1, storage = newStorage)
    afterChanged(change)
  }
}

private class VersionedStorageChangeImpl(entityStorage: VersionedEntityStorage,
                                         override val storageBefore: WorkspaceEntityStorage,
                                         override val storageAfter: WorkspaceEntityStorage,
                                         private val changes: Map<Class<*>, List<EntityChange<*>>>) : VersionedStorageChange(
  entityStorage) {
  @Suppress("UNCHECKED_CAST")
  override fun <T : WorkspaceEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>> =
    (changes[entityClass] as? List<EntityChange<T>>) ?: emptyList()

  override fun getAllChanges(): Sequence<EntityChange<*>> = changes.values.asSequence().flatten()
}