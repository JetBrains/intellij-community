// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.forEachDirectoryEntry

/**
 * @param readOnly used for diagnostic purposes
 */
class VfsLog(
  private val storagePath: Path,
  val readOnly: Boolean = false
) {
  private var version by PersistentVar.integer(storagePath / "version")

  init {
    version.let {
      if (it != VERSION) {
        if (readOnly) {
          LOG.warn("VFS Log version differs from implementation version: log $it vs implementation $VERSION")
        } else {
          if (it != null) {
            clear()
          }
          version = VERSION
        }
      }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  //private val coroutineDispatcher = newSingleThreadContext("VFS WAL dispatcher")
  private val coroutineDispatcher = newFixedThreadPoolContext(8, "VFS WAL dispatcher")
  private val exceptionsHandler = CoroutineExceptionHandler { _, throwable ->
    LOG.error("Uncaught exception", throwable)
  }
  private val coroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher + exceptionsHandler)

  private val context = object : Context {
    override val stringEnumerator = object : SuspendDataEnumerator<String> {
      private val base = SimpleStringPersistentEnumerator(storagePath / "stringsEnum")

      override suspend fun enumerate(value: String?): Int = base.enumerate(value)

      override suspend fun valueOf(ordinal: Int): String? = base.valueOf(ordinal)
    }
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
      while (true) {
        delay(5000)
        flush()
        LOG.warn("#jobs: ${coroutineScope.coroutineContext.job.children.count { it.isActive }}")
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
    val stringEnumerator: SuspendDataEnumerator<String>
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLog::class.java)

    const val VERSION = -41
  }
}