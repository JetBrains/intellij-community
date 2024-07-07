// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SystemProperties.getBooleanProperty
import com.intellij.util.containers.SLRUCache
import com.intellij.util.containers.hash.EqualityPolicy
import com.intellij.util.io.IOCancellationCallbackHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.concurrent.withLock
import kotlin.math.ceil

@Internal
interface MapIndexStorageCache<Key, Value> {
  fun read(key: Key): ChangeTrackingValueContainer<Value>

  fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>?

  fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>>

  fun invalidateAll()
}

@Internal
interface MapIndexStorageCacheProvider {
  fun <Key, Value> createCache(keyReader: Function<Key, ChangeTrackingValueContainer<Value>>,
                               evictionListener: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
                               hashingStrategy: EqualityPolicy<Key>,
                               cacheSize: Int): MapIndexStorageCache<Key, Value>

  companion object {
    val actualProvider: MapIndexStorageCacheProvider by lazy {
      ServiceLoader.load(MapIndexStorageCacheProvider::class.java).firstOrNull()
      ?: DefaultMapIndexStorageCacheProvider
    }
  }
}

@Internal
object DefaultMapIndexStorageCacheProvider : MapIndexStorageCacheProvider {
  private val USE_SLRU = System.getProperty("idea.indexes.cache.type", "slru").equals("slru")
  private val USE_CAFFEINE = System.getProperty("idea.indexes.cache.type", "slru").equals("caffeine")

  /** Offload ValueContainer safe to Dispatchers.IO */
  private val CAFFEINE_OFFLOAD_IO = getBooleanProperty("idea.indexes.cache.offload-io", false)

  init {
    val logger = logger<MapIndexStorageCacheProvider>()
    if (USE_SLRU) {
      logger.info("SLRU-cache will be used for indexes")
    }
    else if (USE_CAFFEINE) {
      logger.info("Caffeine-cache will be used for indexes")
    }
    else {
      logger.warn("Unrecognized cache impl is configured for indexes! ('slru' and 'caffeine' are the supported impls)")
    }
  }

  override fun <Key, Value> createCache(keyReader: Function<Key, ChangeTrackingValueContainer<Value>>,
                                        evictionListener: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
                                        hashingStrategy: EqualityPolicy<Key>,
                                        cacheSize: Int): MapIndexStorageCache<Key, Value> {
    return if (USE_SLRU) {
      MapIndexStorageSlruCache(keyReader, evictionListener, hashingStrategy, cacheSize)
    }
    else if (USE_CAFFEINE) {
      MapIndexStorageCaffeineCache(keyReader, evictionListener, CAFFEINE_OFFLOAD_IO, hashingStrategy, cacheSize)
    }
    else {
      throw AssertionError("'slru'/'caffeine' are the only cache implementations available now")
    }
  }
}

@Internal
class MapIndexStorageSlruCache<Key, Value>(val valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
                                                   val evictionListener: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
                                                   hashingStrategy: EqualityPolicy<Key>,
                                                   cacheSize: Int) : MapIndexStorageCache<Key, Value> {
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
      evictionListener.accept(key, valueContainer)
    }
  }

  private val cacheAccessLock = ReentrantLock()


  companion object {
    //cache efficacy statistics:
    //TODO RC: implement statistics with wrapper around MapIndexStorageCache impl?
    private val totalReads: AtomicLong = AtomicLong()
    private val totalUncachedReads: AtomicLong = AtomicLong()

    fun totalReads(): Long = totalReads.get()

    fun totalReadsUncached(): Long = totalUncachedReads.get()
  }


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

private class MapIndexStorageCaffeineCache<Key, Value>(
  valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
  evictionListener: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
  offloadIO: Boolean,
  private val equalityPolicy: EqualityPolicy<Key>,
  cacheSize: Int,
) : MapIndexStorageCache<Key, Value> {

  private val cache: LoadingCache<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>>

  init {

    val valuesLoader = CacheLoader<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>> { wrappedKey ->
      valueReader.apply(wrappedKey.key)
    }

    val onEvict = RemovalListener<KeyWithCustomEquality<Key>, ChangeTrackingValueContainer<Value>> { wrappedKey, container, cause ->
      //key/value could be null only for weak keys/values, if apt object is already collected.
      // It is not our configuration, but lets guard anyway:
      if (container != null && wrappedKey != null) {
        evictionListener.accept(wrappedKey.key, container)
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

      if(equality !== other.equality){
        return false
      }
      return equality.isEqual(key, other.key)
    }

    override fun hashCode(): Int = equality.getHashCode(key)
  }
}