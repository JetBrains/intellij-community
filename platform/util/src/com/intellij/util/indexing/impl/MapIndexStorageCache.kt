// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.SLRUCache
import com.intellij.util.containers.hash.EqualityPolicy
import com.intellij.util.io.IOCancellationCallbackHolder
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.ceil

@Internal
interface MapIndexStorageCache<Key : Any, Value> {
  fun read(key: Key): ChangeTrackingValueContainer<Value>

  fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>?

  //TODO RC: This is only used in .isDirty, to find out is _any_ of values isDirty
  //         Such a method could be implemented more efficiently inside the cache itself -- no need to allocate
  //         a potentially big collection to just find a first element matching a predicate
  fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>>

  //TODO RC: this method is used to flush _modified_ elements from a cache. There is no need to invalidate all the
  //         cache content then, and the method semantics would be clearer if it is named 'flush()'
  fun invalidateAll()
}


@Internal
interface MapIndexStorageCacheProvider {
  fun <Key : Any, Value> createCache(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    hashingStrategy: EqualityPolicy<Key>,
    cacheSizeHint: Int,
  ): MapIndexStorageCache<Key, Value>


  //===============  statistics: ===============

  /** Number of read requests to _all_ the caches created by this provider */
  fun totalReads(): Long

  /**
   * Number of read requests to _all_ the caches created by this provider that require _loading_ a value
   * (via `valueReader.apply()`).
   * In other words: total number of cache-misses across all the caches created by this provider.
   */
  fun totalReadsUncached(): Long

  /**
   * Number of entries evicted from all the caches created by this provider.
   * Basically it is a number of calls to evictedValuesPersister across all the caches created by this provider
   */
  fun totalEvicted(): Long


  companion object {
    private const val CACHE_PROVIDER_CLASS_PROPERTY = "indexes.storage-cache.provider-class"

    val actualProvider: MapIndexStorageCacheProvider by lazy {
      val providerClass = System.getProperty(CACHE_PROVIDER_CLASS_PROPERTY)
      if (providerClass != null) {
        try {
          val provider = ReflectionUtil.newInstance(Class.forName(providerClass)) as MapIndexStorageCacheProvider
          thisLogger().info("Index storage cache provider [$providerClass] is used")
          return@lazy provider
        }
        catch (e: Throwable) {
          thisLogger().error("Failed to instantiate index cache provider: [$CACHE_PROVIDER_CLASS_PROPERTY='$providerClass']", e)
        }
      }
      val provider = ServiceLoader.load(MapIndexStorageCacheProvider::class.java).firstOrNull()
      if (provider != null) {
        thisLogger().info("Index storage cache provider [$provider: loaded from services] is used")
        return@lazy provider
      }
      thisLogger().info("Default index storage cache provider [$SlruIndexStorageCacheProvider] is used")
      return@lazy SlruIndexStorageCacheProvider
    }
  }
}

/** Implementation uses a [SLRUCache] under the hood */
@Internal
object SlruIndexStorageCacheProvider : MapIndexStorageCacheProvider {
  //RC: unfortunately, we can't create thread-unsafe cache now and rely on storage lock, because storage lock is RW, and we
  // often _modify_ the cache under storage read operation/storage read lock
  private const val THREAD_SAFE_IMPL = true

  init {
    thisLogger().info("SLRU cache will be used for indexes")
  }

  //cache efficacy statistics:
  //MAYBE RC: statistics could be implemented universally, with wrapper around MapIndexStorageCache impl -- need to intercept
  //          get()/getIfCached(), and valueReader
  private val totalReads: AtomicLong = AtomicLong()
  private val totalUncachedReads: AtomicLong = AtomicLong()
  private val totalEvicted: AtomicLong = AtomicLong()

  override fun totalReads(): Long = totalReads.get()

  override fun totalReadsUncached(): Long = totalUncachedReads.get()

  override fun totalEvicted(): Long = totalEvicted.get()

  override fun <Key : Any, Value> createCache(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    hashingStrategy: EqualityPolicy<Key>,
    cacheSizeHint: Int,
  ): MapIndexStorageCache<Key, Value> {
    val underlyingCache = SlruCache(valueReader, evictedValuesPersister, hashingStrategy, cacheSizeHint)
    return if (THREAD_SAFE_IMPL) {
      LockedCacheWrapper(underlyingCache)
    }
    else {
      underlyingCache
    }
  }

  private class SlruCache<Key : Any, Value>(
    val valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    val evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    hashingStrategy: EqualityPolicy<Key>,
    cacheSize: Int,
  ) : MapIndexStorageCache<Key, Value> {
    private val cache = object : SLRUCache<Key, ChangeTrackingValueContainer<Value>>(
      cacheSize,
      ceil(cacheSize * 0.25).toInt(),
      hashingStrategy
    ) {
      override fun createValue(key: Key): ChangeTrackingValueContainer<Value> {
        totalUncachedReads.incrementAndGet()
        return valueReader.apply(key)
      }

      override fun onDropFromCache(key: Key, valueContainer: ChangeTrackingValueContainer<Value>) {
        totalEvicted.incrementAndGet()

        evictedValuesPersister.accept(key, valueContainer)
      }
    }

    override fun read(key: Key): ChangeTrackingValueContainer<Value> {
      totalReads.incrementAndGet()
      return cache.get(key)
    }

    override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? {
      totalReads.incrementAndGet()
      return cache.getIfCached(key)
    }

    override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> {
      return cache.values()
    }

    override fun invalidateAll() {
      cache.clear()
    }
  }
}


/**
 * Wrapper around a cache implementation: adds simple ReentrantLock over every operation.
 * Simplest and primitive way to add a thread-safety a non-thread-safe cache implementation
 */
internal class LockedCacheWrapper<Key : Any, Value>(private val underlyingCache: MapIndexStorageCache<Key, Value>) : MapIndexStorageCache<Key, Value> {
  private val cacheAccessLock = ReentrantLock()


  override fun read(key: Key): ChangeTrackingValueContainer<Value> = cacheAccessLock.withLock { underlyingCache.read(key) }

  override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? = cacheAccessLock.withLock { underlyingCache.readIfCached(key) }

  override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> = cacheAccessLock.withLock { underlyingCache.getCachedValues() }

  override fun invalidateAll() = cacheAccessLock.withLock {
    while (!cacheAccessLock.tryLock(10, MILLISECONDS)) {
      IOCancellationCallbackHolder.checkCancelled()
    }
    try {
      underlyingCache.invalidateAll()
    }
    finally {
      cacheAccessLock.unlock()
    }
  }
}

/** Implementation uses a very simple MRU-cache under the hood */
@Suppress("unused")
@Internal
class MRUIndexStorageCacheProvider : MapIndexStorageCacheProvider {

  init {
    thisLogger().info("MRU cache will be used for indexes")
  }

  //cache efficacy statistics:
  //MAYBE RC: statistics could be implemented universally, with wrapper around MapIndexStorageCache impl -- need to intercept
  //          get()/getIfCached(), and valueReader
  private val totalReads: AtomicLong = AtomicLong()
  private val totalUncachedReads: AtomicLong = AtomicLong()
  private val totalEvicted: AtomicLong = AtomicLong()

  override fun totalReads(): Long = totalReads.get()

  override fun totalReadsUncached(): Long = totalUncachedReads.get()

  override fun totalEvicted(): Long = totalEvicted.get()

  override fun <Key : Any, Value> createCache(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    hashingStrategy: EqualityPolicy<Key>,
    cacheSizeHint: Int,
  ): MapIndexStorageCache<Key, Value> {
    val cache = MRUCache<Key, ChangeTrackingValueContainer<Value>>(
      { key -> totalUncachedReads.incrementAndGet(); valueReader.apply(key) },
      { key, value -> totalEvicted.incrementAndGet(); evictedValuesPersister.accept(key, value) },
      hashingStrategy,
      cacheSizeHint * 5 / 4 //to match SLRU cache sizing
    )

    return object : MapIndexStorageCache<Key, Value> {
      override fun read(key: Key): ChangeTrackingValueContainer<Value> {
        totalReads.incrementAndGet()
        return cache.get(key)
      }

      override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? {
        totalReads.incrementAndGet()
        return cache.getIfCached(key)
      }

      //MAYBE RC: totalReads.addAndGet(cache.values().size)?
      override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> = cache.values()

      override fun invalidateAll() = cache.invalidateAll()
    }
  }

  /** 'weak consistency' cache */
  private class MRUCache<Key : Any, Value>(
    val valueReader: Function<Key, Value>,
    val evictedValuesPersister: BiConsumer<Key, Value>,
    val hashingStrategy: EqualityPolicy<Key>,
    cacheSize: Int,
  ) {

    data class CacheEntry<K : Any, V>(val key: K, val value: V)

    private val table: AtomicReferenceArray<CacheEntry<Key, Value>?> = AtomicReferenceArray(cacheSize)

    fun get(key: Key): Value {
      val hash = hashingStrategy.getHashCode(key)
      val index = abs(hash % table.length())
      val entry = table[index]
      if (entry != null && hashingStrategy.isEqual(entry.key, key)) {
        return entry.value
      }

      val value = valueReader.apply(key)
      table[index] = CacheEntry(key, value)
      if (entry != null) {
        evictedValuesPersister.accept(entry.key, entry.value)
      }
      return value
    }

    fun getIfCached(key: Key): Value? {
      val hash = hashingStrategy.getHashCode(key)
      val index = abs(hash % table.length())
      val entry = table[index]
      if (entry != null && hashingStrategy.isEqual(entry.key, key)) {
        return entry.value
      }
      return null
    }

    fun values(): Collection<Value> {
      val values = mutableListOf<Value>()
      for (i in 0 until table.length()) {
        table[i]?.let { values.add(it.value) }
      }
      return values
    }

    fun invalidateAll() {
      for (i in 0 until table.length()) {
        table[i]?.let { entry ->
          evictedValuesPersister.accept(entry.key, entry.value)
        }
        table[i] = null
      }
    }
  }
}