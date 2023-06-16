// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.util.SystemProperties
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.forEachDirectoryEntry
import kotlin.math.max

/**
 * VfsLog tracks every modification operation done to files of PersistentFS and persists them in a separate storage,
 * allows to query the resulting operations log.
 * @param readOnly if true, won't modify storages and register [connectionInterceptors] thus VfsLog won't track [PersistentFS] changes
 */
@ApiStatus.Experimental
class VfsLog(
  private val storagePath: Path,
  private val readOnly: Boolean = false
) {
  var version by PersistentVar.integer(storagePath / "version")
    private set

  init {
    version.let {
      if (it != VERSION) {
        if (it != null) {
          LOG.info("VfsLog storage version differs from the implementation version: log $it vs implementation $VERSION")
        }
        if (!readOnly) {
          if (it != null) {
            LOG.info("Upgrading storage, old data will be lost")
            clear()
          }
          version = VERSION
        }
      }
    }
  }

  private inner class ContextImpl : VfsLogContext {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(WORKER_THREADS_COUNT))

    // todo: probably need to propagate readOnly to storages to ensure safety
    override val stringEnumerator = SimpleStringPersistentEnumerator(storagePath / "stringsEnum")
    override val operationLogStorage = OperationLogStorageImpl(storagePath / "operations", stringEnumerator,
                                                               coroutineScope, WORKER_THREADS_COUNT)
    override val payloadStorage = PayloadStorageImpl(storagePath / "data")

    fun flush() {
      payloadStorage.flush()
      operationLogStorage.flush()
    }

    fun dispose() {
      // cancellation of writing coroutines will lead to incomplete descriptors being written instead of the actual data
      if (operationLogStorage.size() != operationLogStorage.emergingSize()) {
        LOG.warn("VfsLog didn't manage to write all data before disposal. Some data about the last operations will be lost: " +
                 "size=${operationLogStorage.size()}, emergingSize=${operationLogStorage.emergingSize()}")
      }
      coroutineScope.cancel("dispose")
      flush()
      if (operationLogStorage.persistentSize() != operationLogStorage.emergingSize()) {
        // If it happens, then there are active writers at disposal (VFS is still working and interceptors enqueue operations)
        LOG.error("after cancellation: " +
                  "persistentSize=${operationLogStorage.persistentSize()}, emergingSize=${operationLogStorage.emergingSize()}")
      }
      operationLogStorage.dispose()
      payloadStorage.dispose()
    }

    suspend fun flusher() {
      while (true) {
        delay(5000)
        flush()
      }
    }
  }

  private val _context = ContextImpl()
  val context: VfsLogContext
    get() = _context

  init {
    if (!readOnly) {
      _context.coroutineScope.launch {
        _context.flusher()
      }
    }
  }

  fun dispose() {
    LOG.debug("VfsLog disposing")
    _context.dispose()
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

  val connectionInterceptors: List<ConnectionInterceptor> = if (readOnly) {
    emptyList()
  }
  else {
    listOf(
      ContentsLogInterceptor(context),
      AttributesLogInterceptor(context),
      RecordsLogInterceptor(context)
    )
  }

  val vFileEventApplicationListener: VFileEventApplicationListener = if (readOnly) {
    object : VFileEventApplicationListener {} // no op
  }
  else {
    VFileEventApplicationLogListener(context)
  }

  fun awaitPendingWrites() {
    while (_context.operationLogStorage.size() < _context.operationLogStorage.emergingSize()) {
      Thread.sleep(1)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLog::class.java)

    const val VERSION = 2

    @JvmField
    val LOG_VFS_OPERATIONS_ENABLED: Boolean = SystemProperties.getBooleanProperty("idea.vfs.log-vfs-operations.enabled", false)
    private val WORKER_THREADS_COUNT = SystemProperties.getIntProperty(
      "idea.vfs.log-vfs-operations.workers",
      max(4, Runtime.getRuntime().availableProcessors() / 2)
    )
    // TODO: compaction & its options
  }
}