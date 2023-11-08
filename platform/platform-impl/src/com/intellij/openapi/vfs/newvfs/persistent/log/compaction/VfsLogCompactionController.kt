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
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.CompactedVfsModel.CompactionPosition
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AtomicDurableRecord
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AtomicDurableRecord.Companion.RecordBuilder
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.OpenMode
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.util.SystemProperties
import com.intellij.util.io.SimpleStringPersistentEnumerator
import com.intellij.util.io.createParentDirectories
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists

@OptIn(ExperimentalPathApi::class)
class VfsLogCompactionController(
  private val storagePath: Path,
  readOnly: Boolean,
  private val acquireCompactionContext: () -> VfsLogCompactionContext?,
  compactionScope: CoroutineScope,
  compactionDelayMs: Long,
  compactionIntervalMs: Long,
  defaultOperationMode: OperationMode,
  modeOverride: Boolean,
) : AutoCloseable {
  init {
    if (!storagePath.exists()) {
      storagePath.createParentDirectories()
    }
  }

  private val compactionModelDir get() = storagePath / "model"
  private val persistedState = AtomicDurableRecord.open(storagePath / "state",
                                                        if (readOnly) OpenMode.Read else OpenMode.ReadWrite,
                                                        stateBuilder(defaultOperationMode))

  // accesses are synchronized on `this`
  private var state: ControllerState = synchronized(this) { ControllerState.Unloaded }

  private val compactionJob: Job? = if (!readOnly) {
    compactionScope.launch {
      compactionInvoker(compactionDelayMs, compactionIntervalMs)
    }
  }
  else null

  init {
    if (!readOnly && modeOverride && persistedState.get().operationMode != defaultOperationMode) {
      changeOperationMode(defaultOperationMode)
    }
    loadModelIfCompactionEnabled()
  }

  val payloadReader: PayloadReader get() {
    val state = synchronized(this) {
      when (val loaded = state) {
        is ControllerState.Compacting -> throw IllegalStateException("access to payloadReader while compaction is in progress")
        ControllerState.Unloaded -> throw IllegalStateException("access to payloadReader while compaction is disabled")
        is ControllerState.Loaded -> loaded.model.loadState()
      }
    }
    return state.payloadReader
  }

  private suspend fun compactionInvoker(compactionDelayMs: Long, compactionIntervalMs: Long) {
    delay(compactionDelayMs)
    while (true) {
      acquireCompactionContext()?.use { ctx ->
        // acquire state
        val compacting =
          synchronized(this) {
            when (val s = state) {
              is ControllerState.Loaded -> {
                val compacting = ControllerState.Compacting(s.model, ctx)
                state = compacting
                compacting
              }
              is ControllerState.Compacting -> {
                throw IllegalStateException("Trying to compact VfsLog when compaction is already running")
              }
              else -> {
                null
              }
            }
          } ?: return@use

        try {
          compacting.runCompaction()
        }
        catch (pce: ProcessCanceledException) {
          LOG.info("compaction was cancelled", pce)
        }
        catch (ce: CancellationException) {
          LOG.info("compaction was cancelled", ce)
        }
        catch (e: Throwable) {
          if (ctx.cancellationWasRequested()) {
            LOG.info("failed to finish compaction, probably VfsLog got disposed", e)
          }
          else {
            LOG.error("Failed to compact vfs log. Compaction will be switched to a corrupted mode", e)
            synchronized(this) {
              compacting.model.close()
              state = ControllerState.Unloaded
              changeOperationMode(OperationMode.Corrupted)
            }
          }
        }

        // release state
        synchronized(this) {
          if (state === compacting) {
            state = ControllerState.Loaded(compacting.model)
          }
        }
      }
      delay(compactionIntervalMs)
    }
  }

  private fun loadModelIfCompactionEnabled() {
    val operationMode = persistedState.get().operationMode
    if (operationMode == OperationMode.CompactData) {
      synchronized(this) {
        check(state is ControllerState.Unloaded)
        val model = CompactedVfsModel(compactionModelDir)
        state = ControllerState.Loaded(model)
      }
    }
  }

  private fun changeOperationMode(newMode: OperationMode) {
    synchronized(this) {
      check(state is ControllerState.Unloaded)
      clearModel()
      persistedState.update {
        operationMode = newMode
      }
    }
  }

  fun VfsLogQueryContext.getCompactedSnapshot(
    getNameByNameId: (Int) -> State.DefinedState<String>,
    getAttributeEnumerator: () -> SimpleStringPersistentEnumerator
  ): CompactedVfsSnapshot? = synchronized(this) {
    when (val s = state) {
      is ControllerState.Compacting -> throw IllegalStateException("Compaction is in progress")
      is ControllerState.Loaded -> {
        val compactedState = s.model.loadState()
        check(begin().getPosition() == compactedState.position.operationLogPosition) {
          "compacted vfs model and operations log are out of sync: " +
          "compaction state=${compactedState}, operations log start position=${begin().getPosition()}"
        }
        CompactedVfsSnapshot(this, compactedState, getNameByNameId, getAttributeEnumerator)
      }
      ControllerState.Unloaded -> null
    }
  }

  private fun Long.toMiB(): String = String.format("%.2f MiB", this.toDouble() / 1024 / 1024)
  private fun ControllerState.Compacting.calculateOperationLogSize(): Long = logCtx.end().getPosition() - logCtx.begin().getPosition()
  private fun ControllerState.Compacting.calculatePayloadStorageSize(): Long = logCtx.getPayloadStorageAdvancePosition() - logCtx.getPayloadStorageStartOffset()
  private fun ControllerState.Compacting.calculateTotalStorageSize(): Long = calculateOperationLogSize() + calculatePayloadStorageSize()
  private fun ControllerState.Compacting.isStorageTooLarge(): Boolean = calculateTotalStorageSize() > logCtx.targetLogSize

  private fun ControllerState.Compacting.runCompaction() {
    if (isStorageTooLarge()) {
      LOG.info("Running VfsLog compaction...")
    }
    var compactedState = model.loadState()
    while (isStorageTooLarge()) {
      if (logCtx.cancellationWasRequested()) throw ProcessCanceledException(RuntimeException("compaction was cancelled by request"))

      val positionToCompactTo = findPositionToCompactTo(compactedState)
      val initialPosition = compactedState.position
      if (positionToCompactTo == null) {
        LOG.warn("Couldn't find new position for compaction: " +
                 "logBegin=${logCtx.begin().getPosition()}, logEnd = ${logCtx.end().getPosition()}, currentState=${initialPosition}")
        break
      }
      val logSizeAfterCompaction = calculateTotalStorageSize() -
                                   (positionToCompactTo.operationLogPosition - initialPosition.operationLogPosition) -
                                   (positionToCompactTo.payloadStoragePosition - initialPosition.payloadStoragePosition)
      LOG.info("Compacting: current log total size: ${calculateTotalStorageSize().toMiB()} " +
               "(operations ${calculateOperationLogSize().toMiB()} + auxiliary data ${calculatePayloadStorageSize().toMiB()}), " +
               "target size: ${logCtx.targetLogSize.toMiB()}, total log size after compaction: ${logSizeAfterCompaction.toMiB()}, " +
               "current operation log offset=$initialPosition, new offsets=$positionToCompactTo")

      if (logCtx.cancellationWasRequested()) throw ProcessCanceledException(RuntimeException("compaction was cancelled by request"))
      when (persistedState.get().operationMode) {
        OperationMode.DropData, OperationMode.Corrupted -> {
          // no op
        }
        OperationMode.CompactData -> {
          val newState = model.compactUpTo(logCtx, compactedState, positionToCompactTo)
          compactedState = newState
        }
      }
      logCtx.applyNewStartOffsetsToLogStorages(positionToCompactTo)
    }
  }

  private fun VfsLogCompactionContext.applyNewStartOffsetsToLogStorages(positionToCompactTo: CompactionPosition) {
    clearOperationLogStorageUpTo(positionToCompactTo.operationLogPosition)
    clearPayloadStorageUpTo(positionToCompactTo.payloadStoragePosition)
  }

  @TestOnly
  fun forceCompactionUpTo(ctx: VfsLogCompactionContext, positionToCompactTo: CompactionPosition) {
    synchronized(this) {
      val loaded = state as ControllerState.Loaded
      val compactedState = loaded.model.loadState()
      val newState = loaded.model.compactUpTo(ctx, compactedState, positionToCompactTo)
      ctx.applyNewStartOffsetsToLogStorages(newState.position)
    }
  }

  /**
   * Can be false-positive, but chances should be very low ([maxOperationsToCheck] operations must _all_ finish later than some following one),
   * and we can afford reading here (+ reading is fast enough).
   * @param estimate return first seen payloadRef offset from PayloadStorage
   */
  private fun VfsLogCompactionContext.getPayloadStorageStartOffsetFor(
    operationLogStartOffset: Long,
    maxOperationsToCheck: Int = 150_000,
    estimate: Boolean = false
  ): Long? {
    val iterator = constrainedIterator(operationLogStartOffset, begin().getPosition(), end().getPosition())
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
      val payloadStoragePosition =
        getPayloadStorageStartOffsetFor(operationLogPosition, maxOperationsToCheck = checkSkipStep, estimate = true)
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
  fun findPositionForCompaction(ctx: VfsLogCompactionContext, targetLogPositionAtLeast: Long): CompactionPosition {
    val compactedState = (state as ControllerState.Loaded).model.loadState()
    return ctx.findNextSuitableCompactionPosition(compactedState.position, 1) {
      it.operationLogPosition >= targetLogPositionAtLeast
    } ?: throw IllegalStateException("Couldn't find new position for compaction: " +
                                     "logBegin=${ctx.begin().getPosition()}, logEnd = ${ctx.end().getPosition()}, " +
                                     "targetPosition=$targetLogPositionAtLeast, currentState=${compactedState.position}")
  }

  private fun ControllerState.Compacting.findPositionToCompactTo(compactedState: CompactedVfsModel.CompactedVfsState): CompactionPosition? {
    val payloadStartOffset = logCtx.getPayloadStorageStartOffset()
    val operationStorageStartOffset = logCtx.begin().getPosition()
    return logCtx.findNextSuitableCompactionPosition(compactedState.position) {
      val dataDropSize = it.operationLogPosition - operationStorageStartOffset + it.payloadStoragePosition - payloadStartOffset
      val logSizeAfterDrop = calculateTotalStorageSize() - dataDropSize
      val compactionTargetSize = (logCtx.targetLogSize - COMPACTION_CHUNK_SIZE).coerceAtLeast(MIN_LOG_SIZE)
      dataDropSize >= COMPACTION_CHUNK_SIZE || logSizeAfterDrop < compactionTargetSize
    }
  }

  override fun close() {
    @Suppress("RAW_RUN_BLOCKING") // should be okay here
    runBlocking {
      compactionJob?.cancelAndJoin()
    }
    persistedState.close()
  }

  private fun clearModel() {
    if (compactionModelDir.exists()) {
      compactionModelDir.deleteRecursively()
      LOG.info("compaction storage was cleared")
    }
  }

  fun getCompactionPosition(): CompactionPosition? {
    synchronized(this) {
      when (val s = state) {
        is ControllerState.Loaded -> return s.model.loadState().position
        is ControllerState.Compacting -> throw IllegalStateException("state is Compacting")
        ControllerState.Unloaded -> return null
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLogCompactionController::class.java)
    private val COMPACTION_CHUNK_SIZE: Long = SystemProperties.getLongProperty(
      "idea.vfs.log-vfs-operations.compaction-chunk-size",
      96L * 1024 * 1024
    )
    private val MIN_LOG_SIZE: Long = COMPACTION_CHUNK_SIZE / 6 // 16 MiB default

    private sealed interface ControllerState {
      data object Unloaded : ControllerState // corresponds to OperationMode.DropData or Corrupted

      // corresponds to OperationMode.CompactData
      // Loaded <-> Unloaded
      class Loaded(
        val model: CompactedVfsModel,
      ) : ControllerState

      // Loaded <-> Compacting
      class Compacting(
        val model: CompactedVfsModel,
        val logCtx: VfsLogCompactionContext
      ) : ControllerState
    }

    enum class OperationMode {
      /** just drop excess data */
      DropData,

      /** try to preserve data in a separate storage with obsolete value clean up */
      CompactData,

      /** was CompactData, but an error happened, operationally equal to DropData */
      Corrupted
    }

    private interface ControllerMode {
      var operationMode: OperationMode
    }

    private fun stateBuilder(defaultOperationMode: OperationMode): RecordBuilder<ControllerMode>.() -> ControllerMode = {
      object : ControllerMode { // 32 bytes
        override var operationMode: OperationMode by custom(4, defaultOperationMode,
                                                            serialize = { it.putInt(this.ordinal) },
                                                            deserialize = { OperationMode.entries[it.getInt()] })
        private val reserved_ by bytearray(28)
      }
    }
  }
}