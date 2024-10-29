// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.*
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Wrapper for PersistentHashMap closed on [coroutineScope] cancel.
 * Values are forced on disk after putting into the map with a specified delay.
 * The name should be unique across the application to support emergency closing on application shutdown.
 */
@Internal
class ManagedPersistentCache<K, V> @OptIn(ExperimentalCoroutinesApi::class) constructor(
  private val name: String,
  private val mapBuilder: PersistentMapBuilder<K, V>,
  private val coroutineScope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
  private val closeOnAppShutdown: Boolean = true,
  private val cleanDirOnFailure: Boolean = !ApplicationManager.getApplication().isUnitTestMode, // IJPL-149672
) : ManagedCache<K, V> {
  private val persistentMapRef: AtomicReference<PersistentMapBase<K, V>> = AtomicReference(createPersistentMap())
  private val scheduledForce: AtomicReference<Job> = AtomicReference<Job>()
  private val errorCounter: AtomicInteger = AtomicInteger()

  init {
    coroutineScope.awaitCancellationAndInvoke {
      close()
    }
  }

  override suspend fun put(key: K, value: V) {
    withPersistentMap(opName="put") { map ->
      map.put(key, value)
    }
    forceAsync()
  }

  override suspend fun get(key: K): V? {
    return withPersistentMap(opName="get") { map ->
      map.get(key)
    }
  }

  override suspend fun remove(key: K) {
    withPersistentMap(opName="remove") { map ->
      map.remove(key)
    }
    forceAsync()
  }

  private fun forceAsync() {
    coroutineScope.launch {
      scheduleForce()
    }
  }

  private suspend fun scheduleForce() {
    scheduledForce.getAndSet(null)?.cancel()
    coroutineScope {
      val job = launch {
        delay(FORCE_ON_DISK_DELAY_MS)
        force()
      }
      val alreadyScheduled = !scheduledForce.compareAndSet(null, job)
      if (alreadyScheduled) {
        job.cancel()
      }
    }
  }

  private suspend fun force() {
    withPersistentMap(opName="force") { map ->
      map.force()
    }
  }

  private suspend fun close() {
    withContext(dispatcher) {
      close(isAppShutdown=false)
    }
  }

  private fun close(isAppShutdown: Boolean) {
    val persistentMap = persistentMapRef.getAndSet(null)
    if (persistentMap == null) {
      return
    }
    if (!isAppShutdown) {
      TRACKED_CACHES.remove(this)
    }
    close(persistentMap)
  }

  private fun close(persistentMap: PersistentMapBase<K, V>, delete: Boolean = false) {
    ThreadingAssertions.assertBackgroundThread()
    LOG.info("${if (delete) "deleting" else "closing"} $name with ${persistentMap.keysCount()} elements")
    try {
      if (delete) {
        persistentMap.closeAndDelete()
      } else {
        persistentMap.close()
      }
    } catch (e: Exception) {
      LOG.warn("error closing $name", e)
    }
  }

  private fun createPersistentMap(): PersistentMapBase<K, V>? {
    ThreadingAssertions.assertBackgroundThread()
    var map: PersistentMapBase<K, V>? = null
    var exception: Exception? = null
    for (attempt in 0 until CREATE_ATTEMPT_COUNT) {
      if (attempt > 1) {
        Thread.sleep(CREATE_ATTEMPT_DELAY_MS)
      }
      try {
        map = PersistentMapImpl(mapBuilder)
        break
      } catch (e: VersionUpdatedException) {
        exception = e
        LOG.info("$name ${e.message}")
      } catch (e: IOException) {
        exception = e
        if (attempt != CREATE_ATTEMPT_COUNT - 1) {
          LOG.warn("error creating $name, attempt $attempt", e)
        }
      } catch (e: Exception) {
        // e.g., IllegalStateException storage is already registered
        exception = e
        break
      }
      if (cleanDirOnFailure) {
        IOUtil.deleteAllFilesStartingWith(mapBuilder.file)
      } else {
        // IJPL-149672 do not delete files in test mode
        return null
      }
    }
    if (map == null) {
      LOG.error("cannot create $name", exception)
      return null
    }
    LOG.info("created $name with size ${map.keysCount()}")
    if (closeOnAppShutdown) {
      val added = TRACKED_CACHES.add(this)
      if (!added) {
        LOG.error(
          "Probably the project was not disposed properly before reopening. " +
          "Persistent map $name has already been registered. " +
          "List of registered maps: $TRACKED_CACHES"
        )
        close(map)
        return null
      }
    }
    return map
  }

  private suspend fun <T> withPersistentMap(opName: String, operation: (PersistentMapBase<K, V>) -> T?): T? {
    return withContext(dispatcher) {
      val persistentMap = persistentMapRef.get()
      if (persistentMap == null) {
        return@withContext null
      }
      try {
        val result = operation.invoke(persistentMap)
        errorCounter.set(0)
        LOG.trace { "performed '$opName' by $name" }
        return@withContext result
      } catch (e: IOException) {
        LOG.warn("error performing '$opName' by $name", e)
        val count = errorCounter.incrementAndGet()
        if (count > IO_ERRORS_THRESHOLD) {
          LOG.warn("error count exceeds the threshold, closing $name")
          if (persistentMapRef.compareAndSet(persistentMap, null)) {
            // IJPL-158564 unregister tracked cache if too many io errors occurred
            TRACKED_CACHES.remove(this@ManagedPersistentCache)
            close(persistentMap, delete=true)
          }
        }
        return@withContext null
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ManagedPersistentCache<*, *>) return false
    if (name != other.name) return false
    return true
  }

  override fun hashCode(): Int {
    return name.hashCode()
  }

  override fun toString(): String {
    val persistentMap = persistentMapRef.get()
    val size = persistentMap?.keysCount() ?: -1
    val isClosed = persistentMap?.isClosed ?: true
    return "ManagedCache($name, size=$size, isClosed=$isClosed)"
  }

  @TestOnly
  suspend fun size(): Int {
    return withPersistentMap("isClosed") { map ->
      map.keysCount()
    } ?: throw IllegalStateException("persistent map is already closed")
  }

  @TestOnly
  suspend fun isClosed(): Boolean {
    return withPersistentMap("isClosed") { map ->
      map.isClosed
    } ?: true
  }

  @TestOnly
  suspend fun close0() {
    close()
  }

  companion object {
    private const val IO_ERRORS_THRESHOLD: Int = 50
    private const val CREATE_ATTEMPT_COUNT: Int = 5
    private const val CREATE_ATTEMPT_DELAY_MS: Long = 10
    private const val FORCE_ON_DISK_DELAY_MS: Long = 500
    private val LOG: Logger = logger<ManagedPersistentCache<*, *>>()
    private val TRACKED_CACHES: MutableSet<ManagedPersistentCache<*, *>> = ConcurrentCollectionFactory.createConcurrentSet()
    init {
      ShutDownTracker.getInstance().registerCacheShutdownTask {
        TRACKED_CACHES.forEach { cache -> cache.close(isAppShutdown=true) }
        TRACKED_CACHES.clear()
      }
    }
  }
}
