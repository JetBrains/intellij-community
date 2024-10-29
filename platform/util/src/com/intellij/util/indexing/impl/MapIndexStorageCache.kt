// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ReflectionUtil
import com.intellij.util.SystemProperties.getBooleanProperty
import com.intellij.util.containers.SLRUCache
import com.intellij.util.containers.hash.EqualityPolicy
import com.intellij.util.io.IOCancellationCallbackHolder
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
  ): MapIndexStorageCache<Key, Value> = SlruCache(valueReader, evictedValuesPersister, hashingStrategy, cacheSizeHint)

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
        assert(cacheAccessLock.isHeldByCurrentThread)

        totalEvicted.incrementAndGet()

        evictedValuesPersister.accept(key, valueContainer)
      }
    }

    private val cacheAccessLock = ReentrantLock()

    override fun read(key: Key): ChangeTrackingValueContainer<Value> = cacheAccessLock.withLock {
      totalReads.incrementAndGet()
      cache.get(key)
    }

    override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? = cacheAccessLock.withLock {
      totalReads.incrementAndGet()
      cache.getIfCached(key)
    }

    override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> = cacheAccessLock.withLock { cache.values() }

    override fun invalidateAll() {
      while (!cacheAccessLock.tryLock(10, MILLISECONDS)) {
        IOCancellationCallbackHolder.checkCancelled()
      }
      try {
        cache.clear()
      }
      finally {
        cacheAccessLock.unlock()
      }
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


/** Implementation uses [Caffeine] cache under the hood */
@Suppress("unused")
@Internal
class CaffeineIndexStorageCacheProvider : MapIndexStorageCacheProvider {
  /** Offload ValueContainer persistence to Dispatchers.IO */
  private val OFFLOAD_IO = getBooleanProperty("indexes.storage-cache.offload-io", false)

  init {
    thisLogger().info("Caffeine cache will be used for indexes (offload IO: $OFFLOAD_IO)")
  }

  override fun <Key : Any, Value> createCache(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    hashingStrategy: EqualityPolicy<Key>,
    cacheSizeHint: Int,
  ): MapIndexStorageCache<Key, Value> = CaffeineCache(valueReader, evictedValuesPersister, OFFLOAD_IO, hashingStrategy, cacheSizeHint)

  //TODO RC: implement
  override fun totalReads(): Long = 0
  override fun totalReadsUncached(): Long = 0
  override fun totalEvicted(): Long = 0

  private class CaffeineCache<Key : Any, Value>(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    offloadIO: Boolean,
    private val equalityPolicy: EqualityPolicy<Key>,
    cacheSize: Int,
  ) : MapIndexStorageCache<Key, Value> {

    private val cache: LoadingCache<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>>

    init {

      val valuesLoader = CacheLoader<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>> { wrappedKey ->
        valueReader.apply(wrappedKey.key)
      }

      val onEvict = RemovalListener<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>> { wrappedKey, container, _ ->
        //key/value could be null only for weak keys/values, if an apt object is already collected.
        // It is not our configuration, but let's guard anyway:
        if (container != null && wrappedKey != null) {
          evictedValuesPersister.accept(wrappedKey.key, container)
        }
      }

      val evictionExecutor = if (offloadIO)
        Dispatchers.IO.asExecutor()
      else
        Executor { it.run() } //SameThreadExecutor is not available in this module

      //TODO RC: use maximumWeight() in terms of ValueContainer size (!= .size(), but actual memory content at the moment)
      //TODO RC: for keys there equalityPolicy is the same as Key.equals()/hashCode() we could skip creating KeyWithCustomEquality
      //         wrapper
      cache = Caffeine.newBuilder()
        .maximumSize(cacheSize.toLong())
        .executor(evictionExecutor)
        .removalListener(onEvict)
        .build(valuesLoader)
    }

    override fun read(key: Key): ChangeTrackingValueContainer<Value> = cache.get(KeyWithCustomEquality(key, equalityPolicy))

    override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? = cache.getIfPresent(KeyWithCustomEquality(key, equalityPolicy))

    override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> = cache.asMap().values

    override fun invalidateAll() = cache.invalidateAll()

    /**
     * Caffeine doesn't allow customizing equals/hashCode evaluation strategy, hence we need to create a wrapper around
     * the actual Key, and customize equals/hashCode via equalityPolicy in the wrapper.
     */
    private class KeyWithCustomEquality<K>(val key: K, private val equality: EqualityPolicy<K>) {
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        @Suppress("UNCHECKED_CAST")
        other as KeyWithCustomEquality<K>

        if (equality !== other.equality) {
          return false
        }
        return equality.isEqual(key, other.key)
      }

      override fun hashCode(): Int = equality.getHashCode(key)
    }
  }
}

/**
 * Implementation uses single (per-provider) [Caffeine] cache, shared by all individual caches created by the same
 * provider.
 * Each individual cache is assigned an `subCacheId`, and its entries in the shared cache are keyed by
 * `SharedCacheKey(originalKey, subCacheId, ...)`
 */
@Suppress("unused")
@Internal
class SharedCaffeineIndexStorageCacheProvider(totalCacheSize: Long = (256 * 1024)) : MapIndexStorageCacheProvider {

  init {
    thisLogger().info("Caffeine shared cache will be used for indexes (totalCacheSize: $totalCacheSize)")
  }

  //TODO RC: use maximumWeight() in terms of ValueContainer size (!= .size(), but actual memory content at the moment)
  private val sharedCache: LoadingCache<SharedCacheKey<Any>, ChangeTrackingValueContainer<Any?>> = Caffeine.newBuilder()
    .maximumSize(totalCacheSize)
    .executor(Executor { it.run() })
    .removalListener(RemovalListener<SharedCacheKey<Any>, ChangeTrackingValueContainer<Any?>> { sharedCacheKey, container, _ ->
      //key/value could be null only for weak keys/values, if an apt object is already collected.
      // It is not our configuration, but let's guard anyway:
      if (container != null && sharedCacheKey != null) {
        val cacheInfo = subCachesDescriptors.get()[sharedCacheKey.subCacheId]
        check(cacheInfo != null) { "Cache registration info for ${sharedCacheKey} not found" }
        cacheInfo.cacheAccessLock.withLock {
          cacheInfo.evictedValuesPersister.accept(sharedCacheKey.key, container)
        }
      }
    })
    .build(CacheLoader { sharedCacheKey ->
      val cacheInfo = subCachesDescriptors.get()[sharedCacheKey.subCacheId]
      check(cacheInfo != null) { "Cache registration info for ${sharedCacheKey} not found" }
      val valueReader = cacheInfo.valueReader
      val key = sharedCacheKey.key
      valueReader.apply(key)
    })

  /** Map[subCacheId -> SubCacheDescriptor], updated with CopyOnWrite */
  private val subCachesDescriptors: AtomicReference<Int2ObjectMap<SubCacheDescriptor<in Any, in Any?>>> = AtomicReference(Int2ObjectOpenHashMap())


  override fun <Key : Any, Value : Any?> createCache(
    valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    hashingStrategy: EqualityPolicy<Key>,
    cacheSizeHint: Int,
  ): MapIndexStorageCache<Key, Value> {
    val newDescriptor = SubCacheDescriptor(valueReader, evictedValuesPersister)
    while (true) { //CAS loop:
      val currentDescriptors = subCachesDescriptors.get()
      val updatedDescriptors = Int2ObjectOpenHashMap(currentDescriptors)
      val subCacheId = updatedDescriptors.size + 1

      check(!updatedDescriptors.containsKey(subCacheId)) { "Cache with id=$subCacheId already registered" }
      run { //wrapped in .run() to avoid overly-smart-cast:
        @Suppress("UNCHECKED_CAST") //TODO RC: sort out generics here:
        updatedDescriptors[subCacheId] = newDescriptor as SubCacheDescriptor<Any, Any?>
      }
      if (subCachesDescriptors.compareAndSet(currentDescriptors, updatedDescriptors)) {
        val caffeineSharedCache = CaffeineSharedCache(subCacheId, newDescriptor, sharedCache, hashingStrategy)
        return caffeineSharedCache
      }
    }
  }

  override fun totalReads(): Long = sharedCache.stats().requestCount()

  override fun totalReadsUncached(): Long = sharedCache.stats().loadCount()

  override fun totalEvicted(): Long = sharedCache.stats().evictionCount()

  /**
   * 1. Caffeine doesn't allow customizing equals/hashCode evaluation strategy, hence the only way to customize
   *    equals/hashCode is creating a wrapper around the actual Key, and use [equalityPolicy] in the wrapper's
   *    equals/hashCode.
   * 2. Since cache is shared, we need to differentiate keys-values from sub-caches -- this is [subCacheId]
   *    is for
   */
  private class SharedCacheKey<out K>(
    val key: K,
    val subCacheId: Int,
    private val equalityPolicy: EqualityPolicy<K>,
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass !== other?.javaClass) return false

      @Suppress("UNCHECKED_CAST")
      other as SharedCacheKey<K>

      if (subCacheId != other.subCacheId) {
        return false
      }
      return equalityPolicy.isEqual(key, other.key)
    }

    override fun hashCode(): Int = equalityPolicy.getHashCode(key) * 31 + subCacheId
  }

  //@JvmRecord
  private data class SubCacheDescriptor<Key, Value>(
    val valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
    val evictedValuesPersister: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
    val cacheAccessLock: ReentrantLock = ReentrantLock(),
  )

  private class CaffeineSharedCache<Key : Any, Value>(
    private val cacheId: Int,
    private val subCacheDescriptor: SubCacheDescriptor<Key, Value>,
    private val sharedCache: LoadingCache<SharedCacheKey<Any>, ChangeTrackingValueContainer<Any?>>,
    private val equalityPolicy: EqualityPolicy<Key>,
  ) : MapIndexStorageCache<Key, Value> {

    override fun read(key: Key): ChangeTrackingValueContainer<Value> {
      @Suppress("UNCHECKED_CAST")
      return sharedCache.get(SharedCacheKey(key, cacheId, equalityPolicy)) as ChangeTrackingValueContainer<Value>
    }

    override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? {
      @Suppress("UNCHECKED_CAST")
      return sharedCache.getIfPresent(SharedCacheKey(key, cacheId, equalityPolicy)) as ChangeTrackingValueContainer<Value>?
    }

    override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> {
      return sharedCache.asMap().entries
        .filter { it.key.subCacheId == cacheId }
        .map {
          @Suppress("UNCHECKED_CAST")
          it.value as ChangeTrackingValueContainer<Value>
        }
    }

    override fun invalidateAll() {
      sharedCache.invalidateAll(
        sharedCache.asMap().keys.asSequence()
          .filter { it.subCacheId == cacheId }
          .toList()
      )
    }
  }
}
