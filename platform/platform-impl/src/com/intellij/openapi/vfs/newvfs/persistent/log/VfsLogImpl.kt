// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.PayloadSource.Companion.isInline
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.VfsLogCompactionController
import com.intellij.openapi.vfs.newvfs.persistent.log.io.PersistentVar
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.div
import kotlin.io.path.forEachDirectoryEntry
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * @param readOnly if true, won't modify storages and register [connectionInterceptors] thus VfsLog won't track [PersistentFS] changes
 */
@ApiStatus.Experimental
class VfsLogImpl(
  private val storagePath: Path,
  private val readOnly: Boolean = false,
) : VfsLogEx {
  var version by PersistentVar.integer(storagePath / "version")
    private set

  init {
    updateVersionIfNeeded()
  }

  @ApiStatus.Internal
  inner class ContextImpl internal constructor() : VfsLogBaseContext {
    @OptIn(ExperimentalCoroutinesApi::class)
    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(WORKER_THREADS_COUNT))

    // todo: probably need to propagate readOnly to storages to ensure safety
    override val stringEnumerator = SimpleStringPersistentEnumerator(storagePath / "stringsEnum")
    val operationLogStorage = OperationLogStorageImpl(storagePath / "operations", stringEnumerator,
                                                      coroutineScope, WORKER_THREADS_COUNT)
    val payloadStorage = PayloadStorageImpl(storagePath / "data")
    val compactionController = VfsLogCompactionController(
      storagePath / "compacted",
      readOnly,
      { tryAcquireCompactionContext() },
      COMPACTION_DELAY_MS,
      COMPACTION_INTERVAL_MS,
      if (COMPACTION_MODE == -1) DEFAULT_COMPACTION_MODE else VfsLogCompactionController.OperationMode.values()[COMPACTION_MODE],
      COMPACTION_MODE != -1
    )

    private val compactionLock: Semaphore = Semaphore(MAX_READERS)

    fun tryAcquireQuery(): Boolean = compactionLock.tryAcquire()
    fun releaseQuery() = compactionLock.release()
    fun acquireQuery() = compactionLock.acquireUninterruptibly()

    fun tryAcquireCompaction(): Boolean = compactionLock.tryAcquire(MAX_READERS)
    fun acquireCompaction() = compactionLock.acquireUninterruptibly(MAX_READERS)
    fun releaseCompaction() = compactionLock.release(MAX_READERS)

    @Volatile
    var isCompactionRunning: Boolean = false

    val compactionCancellationRequests: AtomicInteger = AtomicInteger(0)

    @Volatile
    var isDisposing: Boolean = false

    fun flush() {
      payloadStorage.flush()
      operationLogStorage.flush()
    }

    fun dispose() {
      val startTime = System.currentTimeMillis()
      assert(!isDisposing)
      isDisposing = true
      compactionController.close()
      awaitPendingWrites(timeout = 50.milliseconds) // give pending writes a chance to finish
      // cancellation of writing coroutines will lead to incomplete descriptors being written instead of the actual data
      operationLogStorage.closeWriteQueue()
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
      LOG.info("VfsLog dispose completed in ${System.currentTimeMillis() - startTime} ms")
    }

    val payloadReader: PayloadReader = reader@{ payloadRef ->
      if (payloadRef.source.isInline) {
        return@reader InlinedPayloadStorage.readPayload(payloadRef)
      }
      if (payloadRef.source == PayloadRef.PayloadSource.PayloadStorage) {
        return@reader payloadStorage.readPayload(payloadRef)
      }
      if (payloadRef.source == PayloadRef.PayloadSource.CompactedVfsAttributes) {
        return@reader compactionController.getInitializedCompactedVfsState().payloadReader(payloadRef)
      }
      State.NotAvailable("no storage is responsible for $payloadRef")
    }

    val payloadAppender: PayloadAppender = writer@{ sizeBytes: Long ->
      if (InlinedPayloadStorage.isSuitableForInlining(sizeBytes)) {
        return@writer InlinedPayloadStorage.appendPayload(sizeBytes)
      }
      return@writer payloadStorage.appendPayload(sizeBytes)
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

  override fun getOperationWriteContext(): VfsLogOperationWriteContext =
    object : VfsLogOperationWriteContext {
      override val stringEnumerator: DataEnumerator<String> get() = context.stringEnumerator

      override fun enqueueOperationWrite(tag: VfsOperationTag, compute: VfsLogOperationWriteContext.() -> VfsOperation<*>) {
        val computeOperation = compute // name collision
        context.operationLogStorage.enqueueOperationWrite(tag, object : CloseableComputable<VfsOperation<*>> {
          override fun compute(): VfsOperation<*> = computeOperation()
          override fun close() {}
        })
      }

      override fun enqueueOperationWithPayloadWrite(tag: VfsOperationTag,
                                                    payloadSize: Long,
                                                    writePayload: OutputStream.() -> Unit,
                                                    compute: VfsLogOperationWriteContext.(payloadRef: PayloadRef) -> VfsOperation<*>) {
        val payloadWriter = context.payloadAppender(payloadSize)
        context.operationLogStorage.enqueueOperationWrite(tag, object : CloseableComputable<VfsOperation<*>> {
          override fun compute(): VfsOperation<*> {
            val ref = payloadWriter.fillData(writePayload)
            return compute(ref)
          }

          override fun close() {
            payloadWriter.close()
          }
        })
      }
    }.also {
      if (readOnly) {
        LOG.warn("access to getOperationWriteContext() with readOnly=true VfsLog", Exception())
      }
    }

  override fun query(): VfsLogQueryContext {
    if (context.tryAcquireQuery()) {
      return makeQueryContext(false)
    }
    context.compactionCancellationRequests.incrementAndGet()
    context.acquireQuery()
    return makeQueryContext(true)
  }

  override fun tryQuery(): VfsLogQueryContext? {
    if (!context.tryAcquireQuery()) return null
    return makeQueryContext(false)
  }

  private fun makeQueryContext(cancellationWasRequested: Boolean): VfsLogQueryContext = object : VfsLogQueryContext {
    private val isClosed = AtomicBoolean(false)

    override val payloadReader: PayloadReader = context.payloadReader

    override fun getBaseSnapshot(
      getNameByNameId: (Int) -> State.DefinedState<String>,
      getAttributeEnumerator: () -> SimpleStringPersistentEnumerator
    ): ExtendedVfsSnapshot? =
      with(context.compactionController) {
        getCompactedSnapshot(getNameByNameId, getAttributeEnumerator)
      }

    override val stringEnumerator: DataEnumerator<String> = context.stringEnumerator

    private val stableRangeIterators = context.operationLogStorage.currentlyAvailableRangeIterators()

    override fun begin(): OperationLogStorage.Iterator = stableRangeIterators.first.copy()

    override fun end(): OperationLogStorage.Iterator = stableRangeIterators.second.copy()

    override fun transferLock(): VfsLogQueryContext {
      check(isClosed.compareAndSet(false, true)) { "context is already closed" }
      return makeQueryContext(cancellationWasRequested)
    }

    override fun close() {
      if (isClosed.compareAndSet(false, true)) {
        if (cancellationWasRequested) {
          context.compactionCancellationRequests.decrementAndGet()
        }
        context.releaseQuery()
      }
    }
  }

  override fun isCompactionRunning(): Boolean = context.isCompactionRunning

  override fun acquireCompactionContext(): VfsLogCompactionContext {
    context.acquireCompaction()
    return makeCompactionContext()
  }

  override fun tryAcquireCompactionContext(): VfsLogCompactionContext? {
    if (!context.tryAcquireCompaction()) return null
    return makeCompactionContext()
  }

  private fun makeCompactionContext(): VfsLogCompactionContext = object : VfsLogCompactionContext {
    init {
      check(!context.isCompactionRunning)
      context.isCompactionRunning = true
    }

    override fun constrainedIterator(position: Long, allowedRangeBegin: Long, allowedRangeEnd: Long): OperationLogStorage.Iterator =
      context.operationLogStorage.constrainedIterator(position, allowedRangeBegin, allowedRangeEnd)

    override val payloadReader: PayloadReader
      get() = context.payloadReader

    // unconstrained
    override fun begin(): OperationLogStorage.Iterator = context.operationLogStorage.begin()
    override fun end(): OperationLogStorage.Iterator = context.operationLogStorage.end()

    override fun cancellationWasRequested(): Boolean {
      return context.compactionCancellationRequests.get() != 0 || context.isDisposing
    }

    override fun clearOperationLogStorageUpTo(position: Long) {
      context.operationLogStorage.dropOperationsUpTo(position)
    }

    override fun clearPayloadStorageUpTo(position: Long) {
      context.payloadStorage.dropPayloadsUpTo(position)
    }

    override val targetOperationLogSize: Long = OPERATION_LOG_MAX_SIZE

    override val stringEnumerator: DataEnumerator<String> get() = context.stringEnumerator

    override fun close() {
      context.isCompactionRunning = false
      context.releaseCompaction()
    }
  }


  fun awaitPendingWrites(timeout: Duration = Duration.INFINITE) {
    val startTime = System.currentTimeMillis()
    while (context.operationLogStorage.size() < context.operationLogStorage.emergingSize() &&
           (System.currentTimeMillis() - startTime).milliseconds < timeout) {
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

    const val VERSION = 3

    private val WORKER_THREADS_COUNT = SystemProperties.getIntProperty(
      "idea.vfs.log-vfs-operations.workers",
      max(4, Runtime.getRuntime().availableProcessors() / 2)
    )

    // compaction options

    /**
     * -1 -- keep current mode
     * 0 -- no compaction, just drop excessive data
     * 1 -- do compaction
     * @see com.intellij.openapi.vfs.newvfs.persistent.log.compaction.VfsLogCompactionController.OperationMode
     */
    private val COMPACTION_MODE: Int = SystemProperties.getIntProperty(
      "idea.vfs.log-vfs-operations.compaction-mode",
      -1
    )
    private val DEFAULT_COMPACTION_MODE = VfsLogCompactionController.OperationMode.CompactData
    private val OPERATION_LOG_MAX_SIZE: Long = SystemProperties.getLongProperty(
      "idea.vfs.log-vfs-operations.max-log-size",
      750L * 1024 * 1024 // 750 MiB, does not include payload storage size
    )
    private val COMPACTION_DELAY_MS: Long = SystemProperties.getLongProperty(
      "idea.vfs.log-vfs-operations.compaction-delay-ms",
      3 * 60_000L
    )
    private val COMPACTION_INTERVAL_MS: Long = SystemProperties.getLongProperty(
      "idea.vfs.log-vfs-operations.compaction-interval-ms",
      60_000L
    )

    private const val MAX_READERS = 16

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