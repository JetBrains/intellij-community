// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.Source.Companion.isInline
import com.intellij.util.SystemProperties
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.delete
import com.intellij.util.io.isDirectory
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.OutputStream
import java.nio.file.Path
import java.util.concurrent.Semaphore
import kotlin.io.path.div
import kotlin.io.path.forEachDirectoryEntry
import kotlin.math.max

/**
 * @param readOnly if true, won't modify storages and register [connectionInterceptors] thus VfsLog won't track [PersistentFS] changes
 */
@ApiStatus.Experimental
class VfsLogImpl(
  private val storagePath: Path,
  private val readOnly: Boolean = false
) : VfsLogEx {
  var version by PersistentVar.integer(storagePath / "version")
    private set

  init {
    updateVersionIfNeeded()
  }

  @ApiStatus.Internal
  inner class ContextImpl: VfsLogBaseContext {
    @OptIn(ExperimentalCoroutinesApi::class)
    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(WORKER_THREADS_COUNT))

    // todo: probably need to propagate readOnly to storages to ensure safety
    override val stringEnumerator = SimpleStringPersistentEnumerator(storagePath / "stringsEnum")
    val operationLogStorage = OperationLogStorageImpl(storagePath / "operations", stringEnumerator,
                                                      coroutineScope, WORKER_THREADS_COUNT)
    val payloadStorage = PayloadStorageImpl(storagePath / "data")

    // TODO: a read write lock with read priority would be better here, ReentrantReadWriteLock can't be used,
    //  because it is bound to a thread
    val compactionLock: Semaphore = Semaphore(1)

    @Volatile
    var isCompactionRunning: Boolean = false

    @Volatile
    var isDisposing: Boolean = false // TODO cancel compaction

    fun flush() {
      payloadStorage.flush()
      operationLogStorage.flush()
    }

    fun dispose() {
      assert(!isDisposing)
      isDisposing = true
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

    val payloadReader: PayloadReader = reader@{ payloadRef ->
      if (payloadRef.source.isInline) {
        return@reader InlinedPayloadStorage.readPayload(payloadRef)
      }
      for (storage in listOf(payloadStorage)) {
        if (storage.sourcesDeclaration.contains(payloadRef.source)) {
          return@reader storage.readPayload(payloadRef)
        }
      }
      throw IllegalStateException("no storage is responsible for $payloadRef")
    }

    val payloadWriter: PayloadWriter = writer@{ sizeBytes: Long, body: OutputStream.() -> Unit ->
      if (InlinedPayloadStorage.isSuitableForInlining(sizeBytes)) {
        return@writer InlinedPayloadStorage.writePayload(sizeBytes, body)
      }
      return@writer payloadStorage.writePayload(sizeBytes, body)
    }

    suspend fun flusher() {
      while (true) {
        delay(5000)
        flush()
      }
    }
  }

  private val context = ContextImpl()

  @TestOnly
  fun getContextImpl() = context

  init {
    if (!readOnly) {
      context.coroutineScope.launch {
        context.flusher()
      }
    }
  }

  override fun dispose() {
    LOG.debug("VfsLog disposing")
    context.dispose()
    LOG.debug("VfsLog disposed")
  }

  override fun flush() {
    context.flush()
  }

  override val connectionInterceptors: List<ConnectionInterceptor> = if (readOnly) {
    emptyList()
  }
  else {
    listOf(
      ContentsLogInterceptor(getOperationWriteContext()),
      AttributesLogInterceptor(getOperationWriteContext()),
      RecordsLogInterceptor(getOperationWriteContext())
    )
  }

  override val vFileEventApplicationListener: VFileEventApplicationListener = if (readOnly) {
    object : VFileEventApplicationListener {} // no op
  }
  else {
    VFileEventApplicationLogListener(getOperationWriteContext())
  }

  override fun getOperationWriteContext(): VfsLogOperationWriteContext = object : VfsLogOperationWriteContext {
    override val stringEnumerator: DataEnumerator<String> get() = context.stringEnumerator
    override val payloadWriter: PayloadWriter get() = context.payloadWriter

    override fun enqueueOperationWrite(tag: VfsOperationTag, compute: VfsLogOperationWriteContext.() -> VfsOperation<*>) {
      context.operationLogStorage.enqueueOperationWrite(tag) { compute() }
    }
  }.also {
    if (readOnly) {
      LOG.warn("access to getOperationWriteContext() with readOnly=true VfsLog", Exception())
    }
  }

  override fun query(): VfsLogQueryContext {
    context.compactionLock.acquireUninterruptibly()
    return makeQueryContext()
  }

  override fun tryQuery(): VfsLogQueryContext? {
    if (!context.compactionLock.tryAcquire()) return null
    return makeQueryContext()
  }

  private fun makeQueryContext(): VfsLogQueryContext = object : VfsLogQueryContext {
    override val payloadReader: PayloadReader = context.payloadReader
    override val stringEnumerator: DataEnumerator<String> = context.stringEnumerator

    private val stableRangeIterators = context.operationLogStorage.currentlyAvailableRangeIterators()

    override fun begin(): OperationLogStorage.Iterator = stableRangeIterators.first.copy()

    override fun end(): OperationLogStorage.Iterator = stableRangeIterators.second.copy()

    override fun close() {
      context.compactionLock.release()
    }
  }

  override fun isCompactionRunning(): Boolean = context.isCompactionRunning

  override fun acquireCompactionContext(): VfsLogCompactionContext {
    context.compactionLock.acquireUninterruptibly()
    return makeCompactionContext()
  }

  override fun tryAcquireCompactionContext(): VfsLogCompactionContext? {
    if (!context.compactionLock.tryAcquire()) return null
    return makeCompactionContext()
  }

  private fun makeCompactionContext(): VfsLogCompactionContext = object : VfsLogCompactionContext {
    init {
      check(!context.isCompactionRunning)
      context.isCompactionRunning = true
    }

    override val stringEnumerator: DataEnumerator<String> get() = context.stringEnumerator

    override fun close() {
      context.isCompactionRunning = false
      context.compactionLock.release()
    }
  }


  fun awaitPendingWrites() {
    while (context.operationLogStorage.size() < context.operationLogStorage.emergingSize()) {
      Thread.sleep(1)
    }
  }

  private fun updateVersionIfNeeded() {
    version.let {
      if (it != VERSION) {
        if (it != null) {
          LOG.info("VfsLog storage version differs from the implementation version: log $it vs implementation $VERSION")
        }
        if (!readOnly) {
          if (it != null) {
            LOG.info("Upgrading storage, old data will be lost")
            clearStorage(storagePath)
          }
          version = VERSION
        }
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLogImpl::class.java)

    const val VERSION = 2 // TODO bump once compaction is implemented

    private val WORKER_THREADS_COUNT = SystemProperties.getIntProperty(
      "idea.vfs.log-vfs-operations.workers",
      max(4, Runtime.getRuntime().availableProcessors() / 2)
    )
    // TODO: compaction & its options

    fun clearStorage(storagePath: Path) {
      if (storagePath.isDirectory()) {
        storagePath.forEachDirectoryEntry { child ->
          if (child != storagePath / "version") {
            child.delete(true)
          }
        }
      }
    }
  }
}