// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.util.SystemProperties
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import kotlin.io.path.div
import kotlin.io.path.forEachDirectoryEntry

/**
 * VfsLog tracks every modification operation done to files of PersistentFS and persists them in a separate storage,
 * allows to query the resulting operations log.
 * @param readOnly if true, won't modify storages and register [interceptors] thus VfsLog won't track [PersistentFS] changes
 */
@ApiStatus.Experimental
class VfsLog(
  private val storagePath: Path,
  val readOnly: Boolean = false
) {
  private var version by PersistentVar.integer(storagePath / "version")

  init {
    version.let {
      if (it != VERSION) {
        LOG.warn("VFS Log version differs from the implementation version: log $it vs implementation $VERSION")
        if (!readOnly) {
          LOG.warn("Upgrading storage, old data will be lost")
          if (it != null) {
            clear()
          }
          version = VERSION
        }
      }
    }
  }
  private val coroutineDispatcher =
    if (readOnly) { Dispatchers.IO } else { Executors.newScheduledThreadPool(WORKER_THREADS_COUNT).asCoroutineDispatcher() }
  private val exceptionsHandler = CoroutineExceptionHandler { _, throwable ->
    LOG.error("Uncaught exception", throwable)
  }
  private val coroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher + exceptionsHandler)

  private val context = object : Context {
    // todo: probably need to propagate readOnly to storages to ensure safety
    override val stringEnumerator = SimpleStringPersistentEnumerator(storagePath / "stringsEnum")
    override val descriptorStorage = DescriptorStorageImpl(storagePath / "events", stringEnumerator)
    override val payloadStorage = PayloadStorageImpl(storagePath / "data")

    fun flush() {
      payloadStorage.flush()
      descriptorStorage.flush()
    }

    fun dispose() {
      flush()
      descriptorStorage.dispose()
      payloadStorage.dispose()
    }

    suspend fun flusher() {
      if (readOnly) {
        return
      }
      while (true) {
        delay(5000)
        flush()

        val jobsQueued = ((coroutineDispatcher as ExecutorCoroutineDispatcher).executor as ScheduledThreadPoolExecutor).queue.size
        if (jobsQueued > 20) {
          LOG.warn("VFS log # queued jobs: $jobsQueued")
        }
      }
    }
  }

  private val processor = object : OperationProcessor {
    override fun enqueue(action: suspend Context.() -> Unit) =
      coroutineScope.launch {
        context.action()
      }
  }

  val interceptors = if (readOnly) { emptyList() } else {
    listOf<ConnectionInterceptor>(
      ContentsLogInterceptor(processor),
      AttributesLogInterceptor(processor),
      RecordsLogInterceptor(processor)
    )
  }

  suspend fun <R> query(body: suspend Context.() -> R): R = context.body()

  init {
    if (!readOnly) {
      coroutineScope.launch {
        context.flusher()
      }
    }
  }

  fun dispose() {
    LOG.debug("VfsLog disposing")
    coroutineScope.cancel("dispose")
    context.dispose()
    if (!readOnly) {
      (coroutineDispatcher as ExecutorCoroutineDispatcher).close()
    }
    LOG.debug("VfsLog disposed")
  }

  fun clear() {
    assert(!readOnly)
    storagePath.forEachDirectoryEntry { child ->
      if (child != storagePath / "version") {
        child.delete(true)
      }
    }
  }

  interface Context {
    val descriptorStorage: DescriptorStorage
    val payloadStorage: PayloadStorage
    val stringEnumerator: DataEnumerator<String>
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLog::class.java)

    const val VERSION = -43

    @JvmField
    val LOG_VFS_OPERATIONS_ENABLED = SystemProperties.getBooleanProperty("idea.vfs.log-vfs-operations.enabled", false)
    private val WORKER_THREADS_COUNT = SystemProperties.getIntProperty("idea.vfs.log-vfs-operations.workers", 4)
    // TODO: compaction & its options
  }
}