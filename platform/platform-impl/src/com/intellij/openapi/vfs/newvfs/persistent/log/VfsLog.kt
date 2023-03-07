// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.persistent.VFileEventApplicationListener
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.util.SystemProperties
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.div
import kotlin.io.path.forEachDirectoryEntry

/**
 * VfsLog tracks every modification operation done to files of PersistentFS and persists them in a separate storage,
 * allows to query the resulting operations log.
 * @param readOnly if true, won't modify storages and register [connectionInterceptors] thus VfsLog won't track [PersistentFS] changes
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
        if (it != null) {
          LOG.info("VFS Log version differs from the implementation version: log $it vs implementation $VERSION")
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

  private val context = object : VfsLogContext {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO.limitedParallelism(WORKER_THREADS_COUNT)

    // todo: probably need to propagate readOnly to storages to ensure safety
    override val stringEnumerator = SimpleStringPersistentEnumerator(storagePath / "stringsEnum")
    override val descriptorStorage = DescriptorStorageImpl(storagePath / "events", stringEnumerator)
    override val payloadStorage = PayloadStorageImpl(storagePath / "data")

    fun flush() {
      payloadStorage.flush()
      descriptorStorage.flush()
    }

    fun dispose() {
      cancel("dispose")
      flush()
      descriptorStorage.dispose()
      payloadStorage.dispose()
    }

    suspend fun flusher() {
      while (true) {
        delay(5000)
        flush()
      }
    }
  }

  init {
    if (!readOnly) {
      context.launch {
        context.flusher()
      }
    }
  }

  suspend fun <R> query(body: suspend VfsLogContext.() -> R): R = context.body()

  fun dispose() {
    LOG.debug("VfsLog disposing")
    context.dispose()
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

  val connectionInterceptors : List<ConnectionInterceptor> = if (readOnly) { emptyList() } else {
    listOf(
      ContentsLogInterceptor(context),
      AttributesLogInterceptor(context),
      RecordsLogInterceptor(context)
    )
  }

  val vFileEventApplicationListener = if (readOnly) {
    object : VFileEventApplicationListener {} // noop
  } else {
    VFileEventApplicationLogListener(context)
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLog::class.java)

    const val VERSION = -44

    @JvmField
    val LOG_VFS_OPERATIONS_ENABLED = SystemProperties.getBooleanProperty("idea.vfs.log-vfs-operations.enabled", false)
    private val WORKER_THREADS_COUNT = SystemProperties.getIntProperty("idea.vfs.log-vfs-operations.workers", 4)
    // TODO: compaction & its options
  }
}