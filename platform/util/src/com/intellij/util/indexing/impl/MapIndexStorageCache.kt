// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl

import com.intellij.util.SystemProperties
import com.intellij.util.containers.SLRUCache
import com.intellij.util.containers.hash.EqualityPolicy
import com.intellij.util.io.IOCancellationCallbackHolder
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.concurrent.TimeUnit
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
      ?: MapIndexStorageCacheSlruProvider
    }
  }
}

@Internal
object MapIndexStorageCacheSlruProvider: MapIndexStorageCacheProvider {
  private val USE_SLRU = SystemProperties.getBooleanProperty("idea.use.slru.for.file.based.index", true)

  override fun <Key, Value> createCache(keyReader: Function<Key, ChangeTrackingValueContainer<Value>>,
                                        evictionListener: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
                                        hashingStrategy: EqualityPolicy<Key>,
                                        cacheSize: Int): MapIndexStorageCache<Key, Value> {
    return if (USE_SLRU) {
      MapIndexStorageSlruCache(keyReader, evictionListener, hashingStrategy, cacheSize)
    }
    else {
      MapIndexStoragePassThroughCache(keyReader, evictionListener, hashingStrategy, cacheSize)
    }
  }
}

private class MapIndexStoragePassThroughCache<Key, Value>(val valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
                                                          val evictionListener: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
                                                          hashingStrategy: EqualityPolicy<Key>,
                                                          cacheSize: Int) : MapIndexStorageCache<Key, Value> {
  override fun read(key: Key): ChangeTrackingValueContainer<Value> = valueReader.apply(key)

  override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? = null

  override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> = emptyList()

  override fun invalidateAll() = Unit
}

private class MapIndexStorageSlruCache<Key, Value>(val valueReader: Function<Key, ChangeTrackingValueContainer<Value>>,
                                                   val evictionListener: BiConsumer<Key, ChangeTrackingValueContainer<Value>>,
                                                   hashingStrategy: EqualityPolicy<Key>,
                                                   cacheSize: Int): MapIndexStorageCache<Key, Value> {
  private val cache = object : SLRUCache<Key, ChangeTrackingValueContainer<Value>>(
    cacheSize, ceil(cacheSize * 0.25).toInt(), hashingStrategy) {
    override fun createValue(key: Key): ChangeTrackingValueContainer<Value> = valueReader.apply(key)

    override fun onDropFromCache(key: Key, valueContainer: ChangeTrackingValueContainer<Value>) {
      assert(cacheAccessLock.isHeldByCurrentThread)
      evictionListener.accept(key, valueContainer)
    }
  }
  private val cacheAccessLock = ReentrantLock()

  override fun read(key: Key): ChangeTrackingValueContainer<Value> = cacheAccessLock.withLock { cache.get(key) }

  override fun readIfCached(key: Key): ChangeTrackingValueContainer<Value>? = cacheAccessLock.withLock { cache.getIfCached(key) }

  override fun getCachedValues(): Collection<ChangeTrackingValueContainer<Value>> = cacheAccessLock.withLock { cache.values() }

  override fun invalidateAll() {
    while (!cacheAccessLock.tryLock(10, TimeUnit.MILLISECONDS)) {
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