// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

@ApiStatus.Internal
class ManagedHeapPersistentCache<K, V>(
  name: String,
  mapBuilder: PersistentMapBuilder<K, V>,
  inMemoryCount: Int = 10,
  closeAppOnShutdown: Boolean = true,
) : ManagedPersistentCache<K, V>(name, mapBuilder, closeAppOnShutdown) {
  private val inMemoryMap: LinkedHashMap<K, InMemoryValue<V>> = createInMemoryMap(inMemoryCount)
  private val rwLock: ReadWriteLock = ReentrantReadWriteLock()

  companion object {
    private val logger = Logger.getInstance(ManagedHeapPersistentCache::class.java)

    private data class InMemoryValue<V>(val value: V?, val isDirty: AtomicBoolean)
  }

  override fun get(key: K): V? {
    ThreadingAssertions.assertBackgroundThread()
    rwLock.readLock().withLock {
      val inMemoryValue = inMemoryMap[key]
      if (inMemoryValue != null) {
        return inMemoryValue.value
      }
    }
    rwLock.writeLock().withLock {
      val inMemoryValue = inMemoryMap[key]
      if (inMemoryValue != null) {
        return inMemoryValue.value
      }
      val value = super.get(key)
      if (value != null) {
        inMemoryMap[key] = InMemoryValue(value, isDirty=AtomicBoolean(false))
        return value
      }
      return null
    }
  }

  override fun set(key: K, value: V) {
    ThreadingAssertions.assertBackgroundThread()
    rwLock.writeLock().withLock {
      inMemoryMap[key] = InMemoryValue(value, isDirty=AtomicBoolean(true))
    }
  }

  override fun remove(key: K) {
    ThreadingAssertions.assertBackgroundThread()
    rwLock.writeLock().withLock {
      inMemoryMap[key] = InMemoryValue(null, isDirty=AtomicBoolean(true))
    }
  }

  override fun force() {
    ThreadingAssertions.assertBackgroundThread()
    var persistedCount = 0
    rwLock.readLock().withLock {
      for (entry in inMemoryMap.entries) {
        val persisted = persistIfDirty(entry)
        if (persisted) {
          persistedCount++
        }
      }
    }
    if (persistedCount > 0) {
      logger.debug { "flushing $persistedCount folding states" }
    }
    super.force()
  }

  override fun close() {
    ThreadingAssertions.assertBackgroundThread()
    force()
    rwLock.writeLock().withLock {
      inMemoryMap.clear()
    }
    super.close()
  }

  private fun createInMemoryMap(inMemoryCount: Int) = object : LinkedHashMap<K, InMemoryValue<V>>() {
    override fun removeEldestEntry(eldestEntry: MutableMap.MutableEntry<K, InMemoryValue<V>>): Boolean {
      val shouldEvict = size > inMemoryCount
      if (shouldEvict) {
        persistIfDirty(eldestEntry)
      }
      return shouldEvict
    }
  }

  private fun persistIfDirty(entry: MutableMap.MutableEntry<K, InMemoryValue<V>>): Boolean {
    val (key, inMemoryValue) = entry
    if (inMemoryValue.isDirty.compareAndSet(true, false)) {
      persist(key, inMemoryValue)
      return true
    }
    return false
  }

  private fun persist(key: K, inMemoryValue: InMemoryValue<V>) {
    if (inMemoryValue.value != null) {
      super.set(key, inMemoryValue.value)
    } else {
      super.remove(key)
    }
  }
}
