// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.IOUtil
import com.intellij.util.io.PersistentMapBase
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.PersistentMapImpl
import com.intellij.util.io.VersionUpdatedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark


/**
 * Wrapper for PersistentHashMap closed on [coroutineScope] cancel.
 * [coroutineScope] must end by cancellation only, because the cache keeps it from completing normally.
 * Values are forced on disk after putting into the map with a specified delay.
 *
 * One cache at a time owns the storage file of [mapBuilder].
 * A cache created while the previous owner of the file closes opens the file after that close.
 * Until then, [get] and [entries] wait up to 300 ms from the creation of the cache, and then return no data.
 * [put] and [remove] wait up to 10 s from the creation of the cache, and then do nothing.
 * A cache is active while its [coroutineScope] is not cancelled.
 * A cache created while an active cache owns the file logs an error and stores nothing.
 */
@OptIn(AwaitCancellationAndInvoke::class)
class ManagedPersistentCache<K, V>(
  private val name: String,
  private val mapBuilder: PersistentMapBuilder<K, V>,
  private val coroutineScope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
  private val closeOnAppShutdown: Boolean = true,
  private val cleanDirOnFailure: Boolean = !ApplicationManager.getApplication().isUnitTestMode, // IJPL-149672
) : ManagedCache<K, V> {
  private val storageFile: Path = mapBuilder.file.toAbsolutePath()
  private val persistentMapRef: AtomicReference<PersistentMapBase<K, V>> = AtomicReference()
  private val forceRequests: Channel<Unit> = Channel(Channel.CONFLATED)
  private val errorCounter: AtomicInteger = AtomicInteger()
  private val closed: CompletableDeferred<Unit> = CompletableDeferred()
  private val createdAt: ValueTimeMark = TimeSource.Monotonic.markNow()
  private val previousOwnerClosed: Deferred<Unit>
  private val opened: Job

  init {
    val previousOwnerClosed = takeOwnership()
    if (previousOwnerClosed != null) {
      this.previousOwnerClosed = previousOwnerClosed
      val cacheScope = coroutineScope.childScope("ManagedPersistentCache $name")
      opened = cacheScope.launch(dispatcher) {
        open()
      }
      cacheScope.launch {
        forceOnRequests()
      }
      cacheScope.awaitCancellationAndInvoke {
        close()
      }
    } else {
      this.previousOwnerClosed = CompletableDeferred(Unit)
      opened = CompletableDeferred(Unit)
      closed.complete(Unit)
    }
  }

  override suspend fun put(key: K, value: V) {
    withPersistentMap(opName="put", isRead=false) { map ->
      map.put(key, value)
    }
    requestForce()
  }

  override suspend fun get(key: K): V? {
    return withPersistentMap(opName="get", isRead=true) { map ->
      map.get(key)
    }
  }

  override suspend fun remove(key: K) {
    withPersistentMap(opName="remove", isRead=false) { map ->
      map.remove(key)
    }
    requestForce()
  }

  override suspend fun entries(): Flow<Pair<K, V>> {
    // Implementation detail: flow could not be used here since processExistingKeys is not suspendable
    return buildList {
      withPersistentMap(opName="entries", isRead=true) { map ->
        map.processExistingKeys { key ->
          add(key to map.get(key)!!)
          true
        }
      }
    }.asFlow()
  }

  private fun requestForce() {
    forceRequests.trySend(Unit)
  }

  /**
   * Forces the map on disk when no force request comes during [FORCE_ON_DISK_DELAY].
   */
  private suspend fun forceOnRequests() {
    while (true) {
      forceRequests.receive()
      do {
        delay(FORCE_ON_DISK_DELAY)
      } while (forceRequests.tryReceive().isSuccess)
      force()
    }
  }

  private suspend fun force() {
    withPersistentMap(opName="force", isRead=false) { map ->
      map.force()
    }
  }

  /**
   * Makes this cache the owner of [storageFile], unless an active cache owns it.
   *
   * @return the close signal of the previous owner, or `null` when an active cache owns the file
   */
  private fun takeOwnership(): Deferred<Unit>? {
    var previousOwner: ManagedPersistentCache<*, *>? = null
    var isOwner = false
    OWNERS.compute(storageFile) { _, currentOwner ->
      previousOwner = currentOwner
      isOwner = currentOwner == null || !currentOwner.coroutineScope.isActive
      if (isOwner) {
        this
      } else {
        currentOwner
      }
    }
    val owner = previousOwner
    if (!isOwner) {
      val exception = IllegalStateException(
        "the cache ${owner?.name} already uses [$storageFile], and its scope is not cancelled. " +
        "Two caches cannot use one file at the same time"
      )
      LOG.error("cannot create $name", exception)
      return null
    }
    return owner?.closed ?: CompletableDeferred(Unit)
  }

  private suspend fun open() {
    if (!previousOwnerClosed.isCompleted) {
      LOG.info("$name waits for the previous owner to close")
      val isPreviousOwnerClosed = withTimeoutOrNull(PREVIOUS_OWNER_WAIT) { previousOwnerClosed.await() } != null
      if (!isPreviousOwnerClosed) {
        LOG.warn("the previous owner of $name did not close in $PREVIOUS_OWNER_WAIT, $name waits for that close")
        previousOwnerClosed.await()
      }
    }
    val persistentMap = createPersistentMap()
    if (persistentMap == null) {
      release()
    } else {
      persistentMapRef.set(persistentMap)
      if (closeOnAppShutdown) {
        OPENED_CACHES.add(this)
        if (APP_SHUTDOWN_STARTED.get()) {
          closeAndRelease(delete=false)
        }
      }
    }
  }

  private suspend fun close() {
    opened.join()
    val isPreviousOwnerClosed = withTimeoutOrNull(PREVIOUS_OWNER_WAIT) { previousOwnerClosed.await() } != null
    if (!isPreviousOwnerClosed) {
      LOG.warn("the previous owner of $name did not close in $PREVIOUS_OWNER_WAIT, $name releases the file")
    }
    withContext(dispatcher) {
      closeAndRelease(delete=false)
    }
  }

  private fun closeAndRelease(delete: Boolean) {
    try {
      val persistentMap = persistentMapRef.getAndSet(null)
      if (persistentMap != null) {
        close(persistentMap, delete)
      }
    } finally {
      release()
    }
  }

  private fun close(persistentMap: PersistentMapBase<K, V>, delete: Boolean) {
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

  private fun release() {
    OPENED_CACHES.remove(this)
    OWNERS.remove(storageFile, this)
    closed.complete(Unit)
  }

  private suspend fun createPersistentMap(): PersistentMapBase<K, V>? {
    ThreadingAssertions.assertBackgroundThread()
    var map: PersistentMapBase<K, V>? = null
    var exception: Exception? = null
    for (attempt in 0 until CREATE_ATTEMPT_COUNT) {
      if (attempt > 1) {
        delay(CREATE_ATTEMPT_DELAY)
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
        // e.g., IllegalStateException when another storage still uses the file
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
    return map
  }

  /**
   * Waits for the open of the map.
   *
   * @return `false` when the operation must not wait longer for the previous owner
   */
  private suspend fun awaitOpened(isRead: Boolean): Boolean {
    if (!previousOwnerClosed.isCompleted) {
      val wait = if (isRead) {
        PREVIOUS_OWNER_READ_WAIT
      } else {
        PREVIOUS_OWNER_WAIT
      }
      val remaining = wait - createdAt.elapsedNow()
      val isPreviousOwnerClosed = withTimeoutOrNull(remaining) { previousOwnerClosed.await() } != null
      if (!isPreviousOwnerClosed) {
        return false
      }
    }
    opened.join()
    return true
  }

  private suspend fun <T> withPersistentMap(
    opName: String,
    isRead: Boolean,
    operation: (PersistentMapBase<K, V>) -> T?,
  ): T? {
    val isOpened = awaitOpened(isRead)
    if (!isOpened) {
      LOG.trace { "skipped '$opName' by $name, the previous owner is not closed yet" }
      return null
    }
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
          // IJPL-158564 release the storage file if too many io errors occurred
          closeAndRelease(delete=true)
        }
        return@withContext null
      }
    }
  }

  override fun toString(): String {
    val persistentMap = persistentMapRef.get()
    val size = persistentMap?.keysCount() ?: -1
    val isClosed = persistentMap?.isClosed ?: true
    return "ManagedCache($name, size=$size, isClosed=$isClosed)"
  }

  @TestOnly
  suspend fun size(): Int {
    return withPersistentMap(opName="size", isRead=true) { map ->
      map.keysCount()
    } ?: throw IllegalStateException("persistent map is already closed")
  }

  @TestOnly
  suspend fun isClosed(): Boolean {
    return withPersistentMap(opName="isClosed", isRead=true) { map ->
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
    private val CREATE_ATTEMPT_DELAY: Duration = 10.milliseconds
    private val FORCE_ON_DISK_DELAY: Duration = 500.milliseconds
    private val PREVIOUS_OWNER_READ_WAIT: Duration = 300.milliseconds
    private val PREVIOUS_OWNER_WAIT: Duration = 10.seconds
    private val LOG: Logger = logger<ManagedPersistentCache<*, *>>()
    private val OWNERS: ConcurrentHashMap<Path, ManagedPersistentCache<*, *>> = ConcurrentHashMap()
    private val OPENED_CACHES: MutableSet<ManagedPersistentCache<*, *>> = ConcurrentHashMap.newKeySet()
    private val APP_SHUTDOWN_STARTED: AtomicBoolean = AtomicBoolean()
    init {
      ShutDownTracker.getInstance().registerCacheShutdownTask {
        APP_SHUTDOWN_STARTED.set(true)
        for (cache in OPENED_CACHES) {
          cache.closeAndRelease(delete=false)
        }
      }
    }
  }
}
