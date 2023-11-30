// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.openapi.vfs.newvfs.persistent.log.ApplicationVFileEventsTracker.VFileEventTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.PayloadSource.Companion.isInline
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadStorageIO.Companion.fillData
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.VfsLogCompactionController
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.VfsLogCompactionController.Companion.OperationMode
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AtomicDurableRecord
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AtomicDurableRecord.Companion.RecordBuilder
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.IncompatibleLayoutException
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.OpenMode
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.OpenMode.*
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.ExtendedVfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.util.SystemProperties
import com.intellij.util.childScope
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
class VfsLogImpl private constructor(
  private val storagePath: Path,
  private val readOnly: Boolean = false,
  // TODO telemetry and logging toggle
) : VfsLogEx {
  private val atomicState: AtomicDurableRecord<VfsLogState> = AtomicDurableRecord.open(storagePath / "state",
                                                                                       if (readOnly) Read else ReadWrite,
                                                                                       stateBuilder)
  val wasProperlyClosedLastSession: Boolean

  init {
    val state = atomicState.get()
    if (state.version != VERSION) {
      LOG.warn("VfsLog storage version differs from the implementation version: log ${state.version} vs implementation $VERSION")
    }
    wasProperlyClosedLastSession = state.closedProperly
    if (!readOnly) {
      atomicState.update {
        closedProperly = false
      }
    }
  }

  @ApiStatus.Internal
  inner class ContextImpl internal constructor() : VfsLogBaseContext {
    @OptIn(ExperimentalCoroutinesApi::class)
    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))

    // todo: probably need to propagate readOnly to storages to ensure safety
    override val stringEnumerator = SimpleStringPersistentEnumerator(storagePath / "stringsEnum")
    val operationLogStorage = OperationLogStorageImpl(storagePath / "operations", stringEnumerator)
    val payloadStorage = PayloadStorageImpl(storagePath / "data")
    val compactionController = VfsLogCompactionController(
      storagePath / "compacted",
      readOnly,
      { tryAcquireCompactionContext() },
      coroutineScope.childScope(CoroutineName("VfsLog compaction")),
      COMPACTION_DELAY_MS,
      COMPACTION_INTERVAL_MS,
      if (COMPACTION_MODE == -1) DEFAULT_COMPACTION_MODE else OperationMode.entries[COMPACTION_MODE],
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

    private val recoveryPointsCollector = RecoveryPointsCollector.open(
      storagePath / "recovery-points",
      if (!readOnly) ReadWrite else Read,
      this@VfsLogImpl
    )

    fun getRecoveryPoints(): List<VfsRecoveryUtils.RecoveryPoint> = recoveryPointsCollector.getRecoveryPoints()

    val isDisposing: AtomicBoolean = AtomicBoolean(false)

    fun flush() {
      payloadStorage.flush()
      operationLogStorage.flush()
      recoveryPointsCollector.updatePersistentState()
    }

    fun dispose() {
      if (!isDisposing.compareAndSet(false, true)) {
        LOG.warn("VfsLog dispose is already completed/in progress")
      }
      val startTime = System.currentTimeMillis()
      operationLogStorage.closeWriteQueue()

      compactionController.close()

      awaitPendingWrites(
        timeout = Duration.INFINITE // we _must_ write down every pending operation, otherwise VfsLog and VFS will be out of sync.
      )
      check(operationLogStorage.size() == operationLogStorage.emergingSize()) {
        "VfsLog operation storage runtime pointers didn't converge: " +
        "size=${operationLogStorage.size()}, emergingSize=${operationLogStorage.emergingSize()}"
      }

      coroutineScope.cancel("dispose")
      flush()
      check(operationLogStorage.persistentSize() == operationLogStorage.emergingSize()) {
        "VfsLog operations storage persistent pointers didn't converge: " +
        "persistentSize=${operationLogStorage.persistentSize()}, emergingSize=${operationLogStorage.emergingSize()}"
      }

      operationLogStorage.dispose()
      payloadStorage.dispose()
      if (!readOnly) {
        atomicState.update {
          closedProperly = true
        }
      }
      atomicState.close()
      LOG.info("VfsLog dispose completed in ${System.currentTimeMillis() - startTime} ms")
    }

    val payloadReader: PayloadReader get() {
      if (!isCompactionRunning) {
        val compactionPayloadReader = compactionController.payloadReader
        return reader@{ payloadRef ->
          if (payloadRef.source.isInline) {
            return@reader InlinedPayloadStorage.readPayload(payloadRef)
          }
          if (payloadRef.source == PayloadRef.PayloadSource.PayloadStorage) {
            return@reader payloadStorage.readPayload(payloadRef)
          }
          if (payloadRef.source == PayloadRef.PayloadSource.CompactedVfsAttributes) {
            return@reader compactionPayloadReader(payloadRef)
          }
          State.NotAvailable("no storage is responsible for $payloadRef")
        }
      } else {
        return reader@{ payloadRef ->
          if (payloadRef.source.isInline) {
            return@reader InlinedPayloadStorage.readPayload(payloadRef)
          }
          if (payloadRef.source == PayloadRef.PayloadSource.PayloadStorage) {
            return@reader payloadStorage.readPayload(payloadRef)
          }
          State.NotAvailable("no storage is responsible for $payloadRef")
        }
      }
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

    suspend fun recoveryPointsPoller() {
      recoveryPointsCollector.poller()
    }
  }

  private val context = ContextImpl()

  @TestOnly
  fun getContextImpl() = context

  init {
    if (!readOnly) {
      context.coroutineScope.launch(CoroutineName("VfsLog flush")) {
        context.flusher()
      }
      context.coroutineScope.launch(CoroutineName("VFS recovery points polling")) {
        context.recoveryPointsPoller()
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

  override fun query(): VfsLogQueryContextEx {
    if (context.tryAcquireQuery()) {
      return makeQueryContext(false)
    }
    context.compactionCancellationRequests.incrementAndGet()
    context.acquireQuery()
    return makeQueryContext(true)
  }

  override fun tryQuery(): VfsLogQueryContextEx? {
    if (!context.tryAcquireQuery()) return null
    return makeQueryContext(false)
  }

  private fun makeQueryContext(cancellationWasRequested: Boolean): VfsLogQueryContextEx = object : VfsLogQueryContextEx {
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

    override fun operationLogEmergingSize(): Long = context.operationLogStorage.emergingSize()

    override fun operationLogIterator(position: Long): OperationLogStorage.Iterator {
      return context.operationLogStorage.iterator(position) // TODO maybe make it constrained
    }

    override fun getRecoveryPoints(): List<VfsRecoveryUtils.RecoveryPoint> {
      return context.getRecoveryPoints()
    }

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

  private fun ensureLogAndCompactionAreSynced() {
    // it can theoretically happen that compaction will update its state, and will not manage to update start offsets
    // for log storages before cancellation/interrupt/whatever
    val compactedPosition = context.compactionController.getCompactionPosition() ?: return
    check(context.operationLogStorage.startOffset() <= compactedPosition.operationLogPosition) {
      "operation log start offset=${context.operationLogStorage.startOffset()} > compacted position=${compactedPosition}"
    }
    check(context.payloadStorage.startOffset() <= compactedPosition.payloadStoragePosition){
      "payload storage start offset=${context.payloadStorage.startOffset()} > compacted position=${compactedPosition}"
    }
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

    override val payloadReader: PayloadReader = context.payloadReader

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

  override fun getRecoveryPoints(): List<VfsRecoveryUtils.RecoveryPoint> {
    return context.getRecoveryPoints()
  }

  override fun awaitPendingWrites(timeout: Duration) {
    val startTime = System.currentTimeMillis()
    while (context.operationLogStorage.size() < context.operationLogStorage.emergingSize() &&
           (System.currentTimeMillis() - startTime).milliseconds < timeout) {
      Thread.sleep(1)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLogImpl::class.java)

    const val VERSION = 7

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
    private val DEFAULT_COMPACTION_MODE = OperationMode.CompactData
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

    private interface VfsLogState {
      var version: Int
      var closedProperly: Boolean
    }

    private val stateBuilder: RecordBuilder<VfsLogState>.() -> VfsLogState = {
      object : VfsLogState { // 64 bytes
        override var version by int(VERSION)
        private val reserved_ by bytearray(59)
        override var closedProperly by boolean(true)
      }
    }

    @JvmStatic
    @JvmOverloads
    fun open(
      storagePath: Path,
      readOnly: Boolean = false,
      throwOnVersionMismatch: Boolean = false,
      deleteOnVersionMismatch: Boolean = !readOnly
      // TODO telemetry and logging toggle
    ): VfsLogImpl {
      checkVersion(storagePath, throwOnVersionMismatch, deleteOnVersionMismatch)
      return VfsLogImpl(storagePath, readOnly)
    }

    private fun checkVersion(storagePath: Path, throwOnMismatch: Boolean, deleteOnMismatch: Boolean) {
      val state = AtomicDurableRecord.open(storagePath / "state", ReadWrite, stateBuilder)
      val version = state.get().version
      state.close()
      if (version != VERSION) {
        if (throwOnMismatch) throw IllegalStateException("VfsLog version mismatch: impl=$VERSION vs stored=$version")
        else if (deleteOnMismatch) {
          LOG.info("Clearing storage")
          try {
            if (clearStorage(storagePath)) LOG.info("VfsLog storage was cleared")
          }
          catch (e: IOException) {
            LOG.error("failed to clear VfsLog storage", e)
          }
        }
        else LOG.warn("VfsLog version mismatch: impl=$VERSION vs stored=$version")
      }
    }

    /**
     * deletes the vfslog storage directory completely
     */
    @JvmStatic
    @Throws(IOException::class)
    fun clearStorage(storagePath: Path): Boolean {
      require(storagePath.name == "vfslog")
      if (storagePath.exists()) {
        storagePath.delete(true)
        return true
      }
      return false
    }

    @JvmStatic
    fun filterOutRecoveryPoints(storagePath: Path, validRange: LongRange) {
      val recoveryPointsPath = storagePath / "recovery-points"
      if (recoveryPointsPath.exists()) {
        RecoveryPointsCollector.filterOutRecoveryPoints(recoveryPointsPath, validRange)
      }
    }
  }
}

private class RecoveryPointsCollector private constructor(
  private val stateHolder: AtomicDurableRecord<RecoveryPoints>,
  private val vfsLogEx: VfsLogEx
) : AutoCloseable {
  private val currentPoints: Array<RecoveryPointData>
  private var modificationCounter: Int = 0
  private var persistentModificationCounter: Int = 0

  init {
    synchronized(this) {
      currentPoints = Array(MAX_RECOVERY_POINTS) { NULL_POINT }
      stateHolder.get().points.forEachIndexed { index, recoveryPointData ->
        currentPoints[index] = recoveryPointData
      }
    }
  }

  suspend fun poller() {
    while (true) {
      delay(RECOVERY_POINTS_POLL_INTERVAL)
      val recoveryPointPosition = writeAction {
        vfsLogEx.tryQuery()?.use { query ->
          query.operationLogEmergingSize()
        }
      }
      if (recoveryPointPosition != null) {
        val timestamp = System.currentTimeMillis()
        synchronized(this) {
          if (currentPoints[0].logPosition == recoveryPointPosition) return@synchronized // nothing changed since last recovery point
          // shift points to the right, last point will be dropped
          for (i in (MAX_RECOVERY_POINTS - 1) downTo 1) currentPoints[i] = currentPoints[i - 1]
          // add new point
          currentPoints[0] = RecoveryPointData(timestamp, recoveryPointPosition)
          modificationCounter++
        }
      }
    }
  }

  fun updatePersistentState() {
    val newState = synchronized(this) {
      val data =
        if (persistentModificationCounter != modificationCounter) currentPoints.copyOf()
        else null
      persistentModificationCounter = modificationCounter
      data
    }
    if (newState != null) {
      stateHolder.update {
        points = newState
      }
    }
  }

  fun getRecoveryPoints(): List<VfsRecoveryUtils.RecoveryPoint> {
    val result = ArrayList<VfsRecoveryUtils.RecoveryPoint>(MAX_RECOVERY_POINTS)
    vfsLogEx.query().use { query ->
      synchronized(this) {
        for (point in currentPoints) {
          if (point == NULL_POINT) continue
          if (query.begin().getPosition() <= point.logPosition && point.logPosition < query.end().getPosition()) {
            result.add(VfsRecoveryUtils.RecoveryPoint(point.timestamp, query.operationLogIterator(point.logPosition)))
          }
        }
      }
    }
    return result
  }

  override fun close() {
    stateHolder.close()
  }

  companion object {
    fun open(path: Path, mode: OpenMode, vfsLogEx: VfsLogEx): RecoveryPointsCollector {
      try {
        val stateHolder = AtomicDurableRecord.open(path, mode, stateBuilder)
        return RecoveryPointsCollector(stateHolder, vfsLogEx)
      }
      catch (e: IncompatibleLayoutException) {
        if (path.exists() && mode == ReadWrite) {
          logger<RecoveryPointsCollector>().warn("deleting old recovery points", e)
          path.deleteExisting()
        }
        val newStateHolder = AtomicDurableRecord.open(path, mode, stateBuilder)
        return RecoveryPointsCollector(newStateHolder, vfsLogEx)
      }
    }

    fun filterOutRecoveryPoints(path: Path, validRange: LongRange) {
      AtomicDurableRecord.open(path, ReadWrite, stateBuilder).use { stateHolder ->
        stateHolder.update {
          val newState = points.copyOf()
          for (i in 0 until MAX_RECOVERY_POINTS) {
            if (newState[i].logPosition !in validRange) newState[i] = NULL_POINT
          }
          points = newState
        }
      }
    }

    private val RECOVERY_POINTS_POLL_INTERVAL: Long = SystemProperties.getLongProperty(
      "idea.vfs.log-vfs-operations.recovery-points-poll-interval",
      30_000L
    )

    private const val MAX_RECOVERY_POINTS = 120

    internal data class RecoveryPointData(val timestamp: Long, val logPosition: Long) {
      companion object {
        const val SIZE_BYTES = 2 * Long.SIZE_BYTES
      }
    }

    private val NULL_POINT = RecoveryPointData(-1, -1)

    private interface RecoveryPoints {
      // array is immutable
      var points: Array<RecoveryPointData>
    }

    private val stateBuilder: RecordBuilder<RecoveryPoints>.() -> RecoveryPoints = {
      object : RecoveryPoints {
        override var points: Array<RecoveryPointData> by custom(
          MAX_RECOVERY_POINTS * RecoveryPointData.SIZE_BYTES,
          Array(MAX_RECOVERY_POINTS) { NULL_POINT },
          serialize = {
            for (point in this) {
              it.putLong(point.timestamp)
              it.putLong(point.logPosition)
            }
          },
          deserialize = {
            val points = Array(MAX_RECOVERY_POINTS) { NULL_POINT }
            for (i in 0 until MAX_RECOVERY_POINTS) {
              val timestamp = it.getLong()
              val logPosition = it.getLong()
              points[i] = RecoveryPointData(timestamp, logPosition)
            }
            points
          }
        )
      }
    }
  }
}