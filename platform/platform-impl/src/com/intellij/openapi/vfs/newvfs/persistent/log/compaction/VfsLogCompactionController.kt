// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.compaction

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.VFileEventBasedIterator.PartialVFileEventException
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.VFileEventBasedIterator.ReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.skipNext
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.PayloadSource
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.CompactedVfsModel.CompactedVfsState
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.CompactedVfsModel.CompactionPosition
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AtomicDurableRecord
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AtomicDurableRecord.Companion.RecordBuilder
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.OpenMode
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.createParentDirectories
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists

@OptIn(ExperimentalPathApi::class)
class VfsLogCompactionController(
  private val storagePath: Path,
  private val readOnly: Boolean,
  private val getCompactionContext: () -> VfsLogCompactionContext?,
  private val compactionDelayMs: Long,
  private val compactionIntervalMs: Long,
  defaultOperationMode: OperationMode,
  modeOverride: Boolean,
) : AutoCloseable {
  init {
    if (!storagePath.exists()) {
      storagePath.createParentDirectories()
    }
  }

  private val compactionModelDir get() = storagePath / "model"
  private val atomicState: AtomicDurableRecord<ControllerState> = AtomicDurableRecord.open(storagePath / "state",
                                                                                           if (readOnly) OpenMode.Read else OpenMode.ReadWrite,
                                                                                           stateBuilder(defaultOperationMode))

  @Volatile
  private var scheduledCompaction: ScheduledFuture<*>? = null

  @Volatile
  private var compactionModel: CompactedVfsModel? = null

  @Volatile
  private var compactionState: CompactedVfsState? = null

  private fun closeModel() {
    scheduledCompaction?.cancel(true)
    scheduledCompaction = null
    compactionState = null
    compactionModel?.close()
    compactionModel = null
  }

  private fun clearModel() {
    if (compactionModelDir.exists()) {
      compactionModelDir.deleteRecursively()
      LOG.info("compaction storage is cleared")
    }
  }

  private fun openModelIfCompactionEnabled() {
    check(compactionModel == null && scheduledCompaction == null)
    if (atomicState.get().operationMode == OperationMode.CompactData) {
      compactionModel = CompactedVfsModel(compactionModelDir)
      LOG.info("excess data will be compacted")
    }
    else {
      LOG.info("current mode is ${atomicState.get().operationMode}, excess data will be lost")
    }
    if (!readOnly) {
      scheduledCompaction = scheduleCompactionJob()
    }
  }

  private fun changeOperationMode(newMode: OperationMode) {
    LOG.info("changing operation mode to $newMode")
    closeModel()
    clearModel()
    atomicState.update {
      operationMode = newMode
    }
  }

  init {
    if (!readOnly && modeOverride && atomicState.get().operationMode != defaultOperationMode) {
      changeOperationMode(defaultOperationMode)
    }
    openModelIfCompactionEnabled()
  }

  fun getInitializedCompactedVfsState(): CompactedVfsState =
    compactionState
    ?: throw AssertionError("compaction state was expected to be initialized already") // are we really inside a VfsLogContext?

  private fun VfsLogQueryContext.getOrLoadCompactedVfsState(): CompactedVfsState? = synchronized(this) {
    compactionState?.let { return it }
    compactionModel?.let {
      compactionState = it.loadOrInitState { CompactionPosition(0L, 0L) }
      return compactionState
    }
    return null
  }

  fun VfsLogQueryContext.getCompactionPosition(): CompactionPosition? = getOrLoadCompactedVfsState()?.position

  fun VfsLogQueryContext.getCompactedSnapshot(
    getNameByNameId: (Int) -> State.DefinedState<String>,
    getAttributeEnumerator: () -> SimpleStringPersistentEnumerator
  ): CompactedVfsSnapshot? = getOrLoadCompactedVfsState()?.let {
    check(begin().getPosition() == it.position.operationLogPosition) {
      "compacted vfs model and operations log are out of sync: " +
      "compaction state=${it.position}, operations log start position=${begin().getPosition()}"
    }
    CompactedVfsSnapshot(this, it, getNameByNameId, getAttributeEnumerator)
  }

  private fun Long.toMiB(): String = String.format("%.2f MiB", this.toDouble() / 1024 / 1024)
  private fun VfsLogCompactionContext.calculateOperationsLogSize(): Long = end().getPosition() - begin().getPosition()
  private fun VfsLogCompactionContext.calculatePayloadStorageSize(): Long = getPayloadStorageAdvancePosition() - getPayloadStorageStartOffset()
  private fun VfsLogCompactionContext.calculateTotalStorageSize(): Long = calculateOperationsLogSize() + calculatePayloadStorageSize()
  private fun VfsLogCompactionContext.isStorageTooLarge(): Boolean = calculateTotalStorageSize() > targetLogSize

  private fun VfsLogCompactionContext.runCompaction() = synchronized(this) {
    try {
      if (isStorageTooLarge()) {
        LOG.info("Running VfsLog compaction...")
      }
      while (isStorageTooLarge()) {
        if (cancellationWasRequested()) throw ProcessCanceledException(RuntimeException("compaction was cancelled by request"))

        val positionToCompactTo = findPositionToCompactTo()
        val initialPosition = getOrLoadCompactedVfsState()!!.position
        if (positionToCompactTo == null) {
          LOG.warn("Couldn't find new position for compaction: logBegin=${begin().getPosition()}, logEnd = ${end().getPosition()}, currentState=${initialPosition}")
          break
        }
        val logSizeAfterCompaction = calculateTotalStorageSize() -
                                     (positionToCompactTo.operationLogPosition - initialPosition.operationLogPosition) -
                                     (positionToCompactTo.payloadStoragePosition - initialPosition.payloadStoragePosition)
        LOG.info("Compacting: current log total size: ${calculateTotalStorageSize().toMiB()} " +
                 "(operations ${calculateOperationsLogSize().toMiB()} + auxiliary data ${calculatePayloadStorageSize().toMiB()}), " +
                 "target size: ${targetLogSize.toMiB()}, total log size after compaction: ${logSizeAfterCompaction.toMiB()}, " +
                 "current operation log offset=$initialPosition, new offsets=$positionToCompactTo")
        if (cancellationWasRequested()) throw ProcessCanceledException(RuntimeException("compaction was cancelled by request"))

        when (atomicState.get().operationMode) {
          OperationMode.DropData, OperationMode.Corrupted -> {
            // no op
          }
          OperationMode.CompactData -> {
            val currentState = getOrLoadCompactedVfsState() ?: throw IllegalStateException("compacted vfs state is not available")
            val newState = compactionModel!!.compactUpTo(this, currentState, positionToCompactTo)
            compactionState = newState
          }
        }

        applyNewStartOffsetsToLogStorages(positionToCompactTo)
      }
    }
    catch (pce: ProcessCanceledException) {
      LOG.info("compaction was cancelled", pce)
      throw pce
    }
    catch (e: Throwable) {
      if (cancellationWasRequested()) {
        LOG.info("failed to finish compaction, probably VfsLog got disposed", e)
        throw ProcessCanceledException(RuntimeException("failed to finish compaction", e))
      }
      LOG.error("Failed to compact vfs log. Compaction will be switched to a corrupted mode", e)
      changeOperationMode(OperationMode.Corrupted)
    }
  }

  private fun makeCompactionJob(): Runnable = Runnable {
    getCompactionContext()?.use {
      it.runCompaction()
    }
  }

  private fun scheduleCompactionJob(): ScheduledFuture<*> =
    AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
      makeCompactionJob(), compactionDelayMs, compactionIntervalMs, TimeUnit.MILLISECONDS
    )

  private fun VfsLogCompactionContext.applyNewStartOffsetsToLogStorages(positionToCompactTo: CompactionPosition) {
    clearOperationLogStorageUpTo(positionToCompactTo.operationLogPosition)
    clearPayloadStorageUpTo(positionToCompactTo.payloadStoragePosition)
  }

  @TestOnly
  fun VfsLogCompactionContext.forceCompactionUpTo(positionToCompactTo: CompactionPosition) {
    val currentState = getOrLoadCompactedVfsState() ?: throw IllegalStateException("compacted vfs state is not available")
    val newState = compactionModel!!.compactUpTo(this, currentState, positionToCompactTo)
    compactionState = newState
    applyNewStartOffsetsToLogStorages(positionToCompactTo)
  }

  /**
   * Can be false-positive, but chances should be very low ([maxOperationsToCheck] operations must _all_ finish later than some following one),
   * and we can afford reading here (+ reading is fast enough).
   * @param estimate return first seen payloadRef offset from PayloadStorage
   */
  private fun VfsLogCompactionContext.getPayloadStorageStartOffsetFor(
    operationsLogStartOffset: Long,
    maxOperationsToCheck: Int = 500_000,
    estimate: Boolean = false
  ): Long? {
    val iterator = constrainedIterator(operationsLogStartOffset, begin().getPosition(), end().getPosition())
    var resultPayloadStartOffset: Long? = null
    var opsChecked = 0
    while (++opsChecked <= maxOperationsToCheck && iterator.hasNext()) {
      when (val read = iterator.nextFiltered(VfsOperationTagsMask.PayloadContainingOperations)) {
        is OperationReadResult.Invalid -> throw read.cause
        is OperationReadResult.Incomplete -> { /* skip */
        }
        is OperationReadResult.Complete -> {
          val payloadRef = (read.operation as VfsOperation.PayloadContainingOperation).dataRef
          if (payloadRef.source == PayloadSource.PayloadStorage &&
              (resultPayloadStartOffset == null || payloadRef.offset < resultPayloadStartOffset)) {
            if (estimate) return payloadRef.offset
            resultPayloadStartOffset = payloadRef.offset
          }
        }
      }
    }
    return resultPayloadStartOffset
  }

  /**
   * @param checkSkipStep will run testCandidate only on every [checkSkipStep] candidate found
   */
  private fun VfsLogCompactionContext.findNextSuitableCompactionPosition(
    initialPosition: CompactionPosition,
    checkSkipStep: Int = 100,
    testCandidate: (CompactionPosition) -> Boolean
  ): CompactionPosition? {
    require(checkSkipStep > 0)
    val logBeginPosition = begin().getPosition()
    val logEndPosition = end().getPosition()
    var iterator = IteratorUtils.VFileEventBasedIterator(
      constrainedIterator(initialPosition.operationLogPosition, logBeginPosition, logEndPosition)
    )

    fun makeCandidateIfSuitable(operationLogPosition: Long): CompactionPosition? {
      val payloadStoragePosition = getPayloadStorageStartOffsetFor(operationLogPosition, maxOperationsToCheck = checkSkipStep, estimate = true)
                                   ?: initialPosition.payloadStoragePosition
      val newCompactionCandidate = CompactionPosition(operationLogPosition, payloadStoragePosition)
      if (testCandidate(newCompactionCandidate)) {
        val precisePayloadStoragePosition = getPayloadStorageStartOffsetFor(operationLogPosition)
                                            ?: initialPosition.payloadStoragePosition
        val preciseCandidate = CompactionPosition(operationLogPosition, precisePayloadStoragePosition)
        if (testCandidate(preciseCandidate))
          return preciseCandidate
      }
      return null
    }

    var operationsTraversed: Long = 0
    while (iterator.hasNext()) {
      operationsTraversed++
      when (val item = iterator.next()) {
        is ReadResult.Invalid -> {
          if (item.cause is PartialVFileEventException) {
            // skip, see com.intellij.openapi.vfs.newvfs.persistent.log.ApplicationVFileEventsLogTracker
            // TODO log?
            val newIter = item.cause.iterator()
            if (!newIter.hasNext()) return null
            newIter.skipNext()
            iterator = IteratorUtils.VFileEventBasedIterator(
              constrainedIterator(newIter.getPosition(), logBeginPosition, logEndPosition)
            )
            continue
          }
          throw item.cause
        }
        is ReadResult.SingleOperation -> {
          val operationLogPosition = item.iterator().getPosition()
          if (operationsTraversed % checkSkipStep == 0L) {
            makeCandidateIfSuitable(operationLogPosition)?.let { return it }
          }
        }
        is ReadResult.VFileEventRange -> {
          val beginPosition = item.begin().getPosition()
          if (operationsTraversed % checkSkipStep == 0L) {
            makeCandidateIfSuitable(beginPosition)?.let { return it }
          }
        }
      }
    }
    return null
  }

  @TestOnly
  fun VfsLogCompactionContext.findPositionForCompaction(targetLogPositionAtLeast: Long): CompactionPosition {
    return findNextSuitableCompactionPosition(getOrLoadCompactedVfsState()!!.position, 1) {
      it.operationLogPosition >= targetLogPositionAtLeast
    } ?: throw IllegalStateException("Couldn't find new position for compaction: " +
                                     "logBegin=${begin().getPosition()}, logEnd = ${end().getPosition()}, " +
                                     "targetPosition=$targetLogPositionAtLeast, currentState=${getOrLoadCompactedVfsState()!!.position}")
  }

  private fun VfsLogCompactionContext.findPositionToCompactTo(): CompactionPosition? {
    val payloadStartOffset = getPayloadStorageStartOffset()
    val operationStorageStartOffset = begin().getPosition()
    return findNextSuitableCompactionPosition(getOrLoadCompactedVfsState()!!.position) {
      val dataDropSize = it.operationLogPosition - operationStorageStartOffset + it.payloadStoragePosition - payloadStartOffset
      val logSizeAfterDrop = calculateTotalStorageSize() - dataDropSize
      val compactionTargetSize = (targetLogSize - COMPACTION_CHUNK_SIZE).coerceAtLeast(MIN_LOG_SIZE)
      dataDropSize >= COMPACTION_CHUNK_SIZE || logSizeAfterDrop < compactionTargetSize
    }
  }

  override fun close() {
    closeModel()
    atomicState.close()
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLogCompactionController::class.java)
    private val COMPACTION_CHUNK_SIZE: Long = SystemProperties.getLongProperty(
      "idea.vfs.log-vfs-operations.compaction-chunk-size",
      64L * 1024 * 1024
    )
    private val MIN_LOG_SIZE: Long = COMPACTION_CHUNK_SIZE / 4 // 16 MiB default

    enum class OperationMode {
      /** just drop excess data */
      DropData,

      /** try to preserve data in a separate storage with obsolete value clean up */
      CompactData,

      /** was CompactData, but an error happened, operationally equal to DropData */
      Corrupted;

      internal companion object {
        val VALUES = OperationMode.values()
      }
    }

    private interface ControllerState {
      var operationMode: OperationMode
    }

    private fun stateBuilder(defaultOperationMode: OperationMode): RecordBuilder<ControllerState>.() -> ControllerState = {
      object : ControllerState { // 32 bytes
        override var operationMode: OperationMode by custom(4, defaultOperationMode,
                                                            serialize = { it.putInt(this.ordinal) },
                                                            deserialize = { OperationMode.VALUES[it.getInt()] })
        private val reserved_ by bytearray(28)
      }
    }
  }
}