package com.intellij.workspace.api

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.*
import java.util.concurrent.atomic.AtomicReference

interface TypedEntityStore {
  val version: Long
  val current: TypedEntityStorage
  fun <R> cachedValue(value: CachedValue<R>): R
  fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R
}

internal class ValuesCache {
  private data class ValuesCacheData(val version: Long, val value: Any?)

  private val cachedValues: Cache<CachedValue<*>, ValuesCacheData> = CacheBuilder.newBuilder().weakValues().build()
  private val cachedValuesWithParameter: Cache<Pair<CachedValueWithParameter<*, *>, *>, ValuesCacheData> =
    CacheBuilder.newBuilder().weakValues().build()

  fun <R> cachedValue(value: CachedValue<R>, version: Long, storage: TypedEntityStorage): R {
    if (storage is TypedEntityStorageBuilder) error("storage must be immutable")
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

  fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P, version: Long, storage: TypedEntityStorage): R {
    if (storage is TypedEntityStorageBuilder) error("storage must be immutable")
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
}

class EntityStoreOnBuilder(private val builder: TypedEntityStorageBuilder): TypedEntityStore {

  private val valuesCache = ValuesCache()
  private val currentSnapshot: AtomicReference<Pair<Long, TypedEntityStorage>> = AtomicReference()

  override val version: Long
    get() = builder.modificationCount

  override val current: TypedEntityStorage
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
}

class EntityStoreOnStorage(private val storage: TypedEntityStorage): TypedEntityStore {

  init {
    if (storage is TypedEntityStorageBuilder) error("storage must be immutable, but got: ${storage.javaClass.name}")
  }

  private val valuesCache = ValuesCache()

  override val version: Long
    get() = 0

  override val current: TypedEntityStorage
    get() = storage

  override fun <R> cachedValue(value: CachedValue<R>): R = valuesCache.cachedValue(value, version, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, version, current)
}

// Note: `source` may be called multiple times and even in parallel
// TODO: future: optimized computations if `source` is pure
// TODO debug by print .ctor call site
class CachedValue<R>(val source: (TypedEntityStorage) -> R)

// Note: `source` may be called multiple times and even in parallel
class CachedValueWithParameter<P, R>(val source: (TypedEntityStorage, P) -> R)

open class EntityStoreImpl(initialStorage: TypedEntityStorage) : TypedEntityStore {

  private val valuesCache = ValuesCache()

  override val current: TypedEntityStorage
    get() = currentPointer.storage

  override val version: Long
    get() = currentPointer.version

  override fun <R> cachedValue(value: CachedValue<R>): R =
    valuesCache.cachedValue(value, version, current)

  override fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R =
    valuesCache.cachedValue(value, parameter, version, current)

  private class Current(val version: Long, val storage: TypedEntityStorage)

  @Volatile
  private var currentPointer: Current = Current(0, initialStorage)

  // Internal API
  @Synchronized
  fun replace(newStorage: TypedEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
    val oldCopy = currentPointer
    if (oldCopy.storage == newStorage) return

    onBeforeChanged(before = oldCopy.storage, after = newStorage, changes = changes)
    currentPointer = Current(version = oldCopy.version + 1, storage = newStorage)
    onChanged(before = oldCopy.storage, after = newStorage, changes = changes)
  }

  protected open fun onBeforeChanged(before: TypedEntityStorage,
                                     after: TypedEntityStorage,
                                     changes: Map<Class<*>, List<EntityChange<*>>>) {

  }

  protected open fun onChanged(before: TypedEntityStorage,
                               after: TypedEntityStorage,
                               changes: Map<Class<*>, List<EntityChange<*>>>) {

  }
}

abstract class EntityStoreChanged(val entityStore: TypedEntityStore) : EventObject(entityStore) {
  abstract val storageBefore: TypedEntityStorage
  abstract val storageAfter: TypedEntityStorage

  abstract fun <T: TypedEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>>

  abstract fun getAllChanges(): Sequence<EntityChange<*>>
}
