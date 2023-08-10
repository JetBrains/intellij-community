// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.openapi.vfs.newvfs.persistent.log.ApplicationVFileEventsTracker.VFileEventTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.PayloadSource.Companion.isInline
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadStorageIO.Companion.fillData
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.VfsLogCompactionController
import com.intellij.openapi.vfs.newvfs.persistent.log.io.PersistentVar
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.util.SystemProperties
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.*
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * @param readOnly if true, won't modify storages and register [connectionInterceptors] thus VfsLog won't track [PersistentFS] changes
 *
 * Warning:
 *  VfsLog tries its best not to impact performance of the VFS too much, e.g. it does not use any kind of locking while collecting
 *  information about modification operations. This comes at a cost. It is possible for VFS to have concurrent writes at the same location,
 *  so it is possible that those writes occur in different order than their operation descriptors will end up in the log. This means
 *  that a VFS state, restored by means of this log, may differ from what it was actually like. However, most of such concurrent writes
 *  are probably benign.
 *  It seems there is no good way to ensure ordering here without introducing locks and rewriting the whole VFS to use alternative
 *  data structures (prove me wrong).
 *  What is guaranteed:
 *      * "happens-before" of operations in VFS implies "happens-before" of corresponding descriptors in log.
 *  What is not guaranteed:
 *      * global operation ordering is not guaranteed, i.e. concurrently happening operations can have different
 *        order in the log than how they were actually executed (not to mention the probable lack of atomicity);
 *      * Operation-Payload order consistency is not guaranteed: if there is an operation1 that comes before operation2 in the log, it is
 *        possible that operation2 contains a payloadRef that points to a location in PayloadStorage that comes before payloadRef of
 *        operation1. This shouldn't bother you, unless you're changing how VfsLog compaction works.
 */
@ApiStatus.Experimental
class VfsLogImpl(
  private val storagePath: Path,
  private val readOnly: Boolean = false,
  // TODO telemetry and logging toggle
) : VfsLogEx {
  private val versionHandler = PersistentVar.integer(storagePath / "version")
  var version by versionHandler
    private set
  private val properlyClosedMarkerHandler = PersistentVar.integer(storagePath / "closeMarker")
  private var properlyClosedMarker by properlyClosedMarkerHandler
  val wasProperlyClosedLastSession: Boolean

  init {
    updateVersionIfNeeded()
    wasProperlyClosedLastSession = (properlyClosedMarker ?: CLOSED_PROPERLY) == CLOSED_PROPERLY
    if (!readOnly) {
      properlyClosedMarker = NOT_CLOSED_PROPERLY
    }
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

    val isDisposing: AtomicBoolean = AtomicBoolean(false)

    fun flush() {
      payloadStorage.flush()
      operationLogStorage.flush()
    }

    fun dispose() {
      if (!isDisposing.compareAndSet(false, true)) {
        LOG.warn("VfsLog dispose is already completed/in progress")
      }
      val startTime = System.currentTimeMillis()

      compactionController.close()

      operationLogStorage.closeWriteQueue()
      // Warning: there is a tiny window for a race condition here: a thread may pass the "write queue is not closed" check and be preempted,
      // thus appending an entry after it's awakened and when write queue is already closed and when we've already awaited all pending writes.
      // But in an ideal world there are no writes that happen concurrently with disposal, so such events, if they tend to happen,
      // should be noticeable even with this race (and fixed?).
      // What this race can break: the modification will be applied to VFS, but won't be written in log (meaning that after restart we won't be
      // able to find it in log), or we won't save it at flush. We'll probably notice it with the following checks, but in case we won't,
      // we'll say that VfsLog was correctly disposed (false-positive CLOSED_PROPERLY).
      // We don't want to pay for synchronization regardless.
      awaitPendingWrites(
        timeout = Duration.INFINITE // we _must_ write down every pending operation, otherwise VfsLog and VFS will be out of sync.
      )

      var lostAnything = false
      if (operationLogStorage.size() != operationLogStorage.emergingSize()) {
        LOG.error("VfsLog didn't manage to write all data before disposal. Some data about the last operations will be lost: " +
                  "size=${operationLogStorage.size()}, emergingSize=${operationLogStorage.emergingSize()}")
        lostAnything = true
      }
      coroutineScope.cancel("dispose")
      flush()
      if (operationLogStorage.persistentSize() != operationLogStorage.emergingSize()) {
        // If it happens, then there are active writers at disposal (VFS is still working and interceptors enqueue operations)
        LOG.error("after cancellation: " +
                  "persistentSize=${operationLogStorage.persistentSize()}, emergingSize=${operationLogStorage.emergingSize()}")
        lostAnything = true
      }
      operationLogStorage.dispose()
      payloadStorage.dispose()
      if (!readOnly && !lostAnything) {
        properlyClosedMarker = CLOSED_PROPERLY
      }
      versionHandler.close()
      properlyClosedMarkerHandler.close()
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

    val payloadWriter: PayloadWriter = { data: ByteArray ->
      val size = data.size.toLong()
      if (InlinedPayloadStorage.isSuitableForInlining(size)) {
        InlinedPayloadStorage.appendPayload(size)
      }
      else {
        payloadStorage.appendPayload(size)
      }.use {
        it.fillData(data)
      }
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

  override val applicationVFileEventsTracker: ApplicationVFileEventsTracker = if (readOnly) {
    ApplicationVFileEventsTracker { VFileEventTracker { /* no op */ } }
  }
  else {
    ApplicationVFileEventsLogTracker(getOperationWriteContext())
  }

  override fun getOperationWriteContext(): VfsLogOperationTrackingContext =
    object : VfsLogOperationTrackingContext {
      override val stringEnumerator: DataEnumerator<String> get() = context.stringEnumerator
      override val payloadWriter: PayloadWriter get() = context.payloadWriter

      override fun trackOperation(tag: VfsOperationTag): OperationTracker {
        return context.operationLogStorage.trackOperation(tag)
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
    init {
      ensureLogAndCompactionAreSynced()
    }

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

  private fun VfsLogQueryContext.ensureLogAndCompactionAreSynced() {
    // it can theoretically happen that compaction will update its state, and will not manage to update start offsets
    // for log storages before cancellation/interrupt/whatever
    with(context.compactionController) {
      val compactedPosition = getCompactionPosition() ?: return
      check(context.operationLogStorage.startOffset() <= compactedPosition.operationLogPosition)
      check(context.payloadStorage.startOffset() <= compactedPosition.payloadStoragePosition)
      if (context.operationLogStorage.startOffset() != compactedPosition.operationLogPosition) {
        LOG.warn("Operation log storage was out of sync with compaction. " +
                 "Updating offsets ${context.operationLogStorage.startOffset()} -> ${compactedPosition.operationLogPosition}")
        context.operationLogStorage.dropOperationsUpTo(compactedPosition.operationLogPosition)
      }
      if (context.payloadStorage.startOffset() != compactedPosition.payloadStoragePosition) {
        LOG.warn("Payload storage was out of sync with compaction. " +
                 "Updating offsets ${context.payloadStorage.startOffset()} -> ${compactedPosition.payloadStoragePosition}")
        context.payloadStorage.dropPayloadsUpTo(compactedPosition.payloadStoragePosition)
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

      ensureLogAndCompactionAreSynced()
    }

    override fun constrainedIterator(position: Long, allowedRangeBegin: Long, allowedRangeEnd: Long): OperationLogStorage.Iterator =
      context.operationLogStorage.constrainedIterator(position, allowedRangeBegin, allowedRangeEnd).also {
        require(position in allowedRangeBegin..allowedRangeEnd) {
          "provided position (=$position) is outside the allowed range (=$allowedRangeBegin..$allowedRangeEnd)"
        }
      }

    override val payloadReader: PayloadReader
      get() = context.payloadReader

    // unconstrained
    override fun begin(): OperationLogStorage.Iterator = context.operationLogStorage.begin()
    override fun end(): OperationLogStorage.Iterator = context.operationLogStorage.end()

    override fun cancellationWasRequested(): Boolean {
      return context.compactionCancellationRequests.get() != 0 || context.isDisposing.get()
    }

    override fun clearOperationLogStorageUpTo(position: Long) {
      context.operationLogStorage.dropOperationsUpTo(position)
    }

    override fun clearPayloadStorageUpTo(position: Long) {
      context.payloadStorage.dropPayloadsUpTo(position)
    }

    override val targetLogSize: Long = LOG_MAX_SIZE

    override fun getPayloadStorageAdvancePosition(): Long = context.payloadStorage.advancePosition()

    override fun getPayloadStorageStartOffset(): Long = context.payloadStorage.startOffset()

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
            LOG.info("Upgrading storage")
            versionHandler.close()
            try {
              if (clearStorage(storagePath)) LOG.info("VfsLog storage was cleared")
            }
            catch (e: IOException) {
              LOG.error("failed to clear VfsLog storage", e)
            }
            versionHandler.reopen()
          }
          version = VERSION
        }
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLogImpl::class.java)

    const val VERSION = 4

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
    private val LOG_MAX_SIZE: Long = SystemProperties.getLongProperty(
      "idea.vfs.log-vfs-operations.max-log-size",
      750L * 1024 * 1024 // 750 MiB, includes payload storage size
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

    private const val NOT_CLOSED_PROPERLY: Int = 0xBADC105
    private const val CLOSED_PROPERLY: Int = 0xC105ED

    @JvmStatic
    @Throws(IOException::class)
    fun clearStorage(storagePath: Path): Boolean {
      if (storagePath.exists()) {
        storagePath.delete(true)
        return true
      }
      return false
    }
  }
}