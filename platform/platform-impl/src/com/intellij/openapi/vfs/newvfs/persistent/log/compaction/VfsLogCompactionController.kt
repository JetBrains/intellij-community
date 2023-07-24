// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.compaction

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.VFileEventBasedIterator.ReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.compaction.CompactedVfsModel.CompactedVfsState
import com.intellij.openapi.vfs.newvfs.persistent.log.io.PersistentVar
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsChronicle
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.SimpleStringPersistentEnumerator
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
  private val defaultOperationMode: OperationMode,
  modeOverride: Boolean,
) : AutoCloseable {
  private val compactionModelDir get() = storagePath / "model"
  private val persistedOperationModeHandler = PersistentVar.integer(storagePath / "operationMode")
  private var persistedOperationMode by persistedOperationModeHandler
  val operationMode: OperationMode get() = OperationMode.VALUES[persistedOperationMode ?: defaultOperationMode.ordinal]

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
    if (operationMode == OperationMode.CompactData) {
      compactionModel = CompactedVfsModel(compactionModelDir)
      LOG.info("excess data will be compacted")
    } else {
      LOG.info("current mode is $operationMode, excess data will be lost")
    }
    if (!readOnly) {
      scheduledCompaction = scheduleCompactionJob()
    }
  }

  private fun changeOperationMode(newMode: OperationMode) {
    LOG.info("changing operation mode to $newMode")
    closeModel()
    clearModel()
    persistedOperationMode = newMode.ordinal
  }

  init {
    if (!readOnly && (persistedOperationMode == null || (modeOverride && operationMode != defaultOperationMode))) {
      changeOperationMode(defaultOperationMode)
    }
    openModelIfCompactionEnabled()
  }

  fun getInitializedCompactedVfsState(): CompactedVfsState =
    compactionState
    ?: throw AssertionError("compaction state was expected to be initialized already") // are we really inside a VfsLogContext?

  private fun VfsLogQueryContext.getCompactedVfsState(): CompactedVfsState? = synchronized(this) {
    compactionState?.let { return it }
    compactionModel?.let {
      compactionState = it.getCurrentState(0L)
      return compactionState
    }
    return null
  }

  fun VfsLogQueryContext.getCompactedSnapshot(
    getNameByNameId: (Int) -> State.DefinedState<String>,
    getAttributeEnumerator: () -> SimpleStringPersistentEnumerator
  ): CompactedVfsSnapshot? = getCompactedVfsState()?.let {
    if (begin().getPosition() > it.operationLogPosition) return null // out of sync and some data is lost
    CompactedVfsSnapshot(this, it, getNameByNameId, getAttributeEnumerator)
  }

  private fun Long.toMiB(): String = String.format("%.2f MiB", this.toDouble() / 1024 / 1024)
  private fun VfsLogCompactionContext.isStorageTooLarge(): Boolean = end().getPosition() - begin().getPosition() > targetOperationLogSize

  private fun VfsLogCompactionContext.runCompaction() = synchronized(this) {
    try {
      if (isStorageTooLarge()) {
        LOG.info("Running VfsLog compaction...")
      }
      while (isStorageTooLarge()) {
        if (cancellationWasRequested()) throw ProcessCanceledException(RuntimeException("compaction was cancelled by request"))

        val initialPosition = begin().getPosition()
        val positionToCompactTo = findPositionToCompactTo()
        val newStoragesPosition = calculateNewStoragesPositions(positionToCompactTo)
        LOG.info("operations log size: ${(end().getPosition() - initialPosition).toMiB()}, target size: ${targetOperationLogSize.toMiB()}, " +
                 "moving from position $initialPosition to $positionToCompactTo")
        if (cancellationWasRequested()) throw ProcessCanceledException(RuntimeException("compaction was cancelled by request"))

        when (operationMode) {
          OperationMode.DropData, OperationMode.Corrupted -> {
            // no op
          }
          OperationMode.CompactData -> {
            val currentState = getCompactedVfsState() ?: throw IllegalStateException("compacted vfs state is not available")
            val newState = compactionModel!!.compactUpTo(this, currentState, newStoragesPosition.operationLogPosition)
            compactionState = newState
          }
        }

        clearOperationLogStorageUpTo(newStoragesPosition.operationLogPosition)
        newStoragesPosition.payloadStoragePosition?.let { clearPayloadStorageUpTo(it) }
      }
    }
    catch (pce: ProcessCanceledException) {
      LOG.info("compaction was cancelled")
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

  @TestOnly
  fun VfsLogCompactionContext.forceCompactionUpTo(positionToCompactTo: Long) {
    val currentState = getCompactedVfsState() ?: throw IllegalStateException("compacted vfs state is not available")
    val newState = compactionModel!!.compactUpTo(this, currentState, positionToCompactTo)
    compactionState = newState
    clearOperationLogStorageUpTo(positionToCompactTo)
  }

  private data class CompactionPosition(
    val operationLogPosition: Long,
    val payloadStoragePosition: Long?
  )

  private fun VfsLogCompactionContext.calculateNewStoragesPositions(positionToCompactTo: Long): CompactionPosition {
    val iterator = constrainedIterator(positionToCompactTo, begin().getPosition(), positionToCompactTo)
    VfsChronicle.traverseOperationsLog(iterator, OperationLogStorage.TraverseDirection.REWIND, VfsOperationTagsMask.ALL) {
      val ref = it.payloadStorageRef
      if (ref != null) {
        return CompactionPosition(positionToCompactTo, ref.offset)
      }
    }
    return CompactionPosition(positionToCompactTo, null)
  }

  private val VfsOperation<*>.payloadStorageRef: PayloadRef?
    get() = when (this) {
      is VfsOperation.AttributesOperation.WriteAttribute -> attrDataPayloadRef
      is VfsOperation.ContentsOperation.AppendStream -> dataPayloadRef
      is VfsOperation.ContentsOperation.ReplaceBytes -> dataPayloadRef
      is VfsOperation.ContentsOperation.WriteBytes -> dataPayloadRef
      is VfsOperation.ContentsOperation.WriteStream -> dataPayloadRef
      is VfsOperation.ContentsOperation.WriteStream2 -> dataPayloadRef
      else -> null
    }.takeIf { it?.source == PayloadRef.PayloadSource.PayloadStorage }

  @TestOnly
  fun VfsLogCompactionContext.findPositionForCompactionNearTo(targetPosition: Long): Long {
    val logBeginPosition = begin().getPosition()
    val logEndPosition = end().getPosition()
    val initialPosition = logBeginPosition.coerceAtLeast(getCompactedVfsState()?.operationLogPosition ?: 0L)
    val iterator = IteratorUtils.VFileEventBasedIterator(constrainedIterator(initialPosition, logBeginPosition, logEndPosition))

    while (iterator.hasNext()) {
      when (val item = iterator.next()) {
        is ReadResult.Invalid -> throw item.cause
        is ReadResult.SingleOperation -> {
          val position = item.iterator().getPosition()
          if (position >= targetPosition) return position
        }
        is ReadResult.VFileEventRange -> {
          val beginPosition = item.begin().getPosition()
          if (beginPosition >= targetPosition) return beginPosition
        }
      }
    }
    throw IllegalStateException("Couldn't find new position for compaction: " +
                                "logBegin=$logBeginPosition, logEnd = $logEndPosition, targetPosition=$targetPosition")
  }

  private fun VfsLogCompactionContext.findPositionToCompactTo(): Long {
    val logBeginPosition = begin().getPosition()
    val logEndPosition = end().getPosition()
    val initialPosition = logBeginPosition.coerceAtLeast(getCompactedVfsState()?.operationLogPosition ?: 0L)
    val iterator = IteratorUtils.VFileEventBasedIterator(constrainedIterator(initialPosition, logBeginPosition, logEndPosition))
    var positionToCompactTo: Long? = null

    fun testCandidate(position: Long): Long? {
      if (position - logBeginPosition > MAX_COMPACTION_CHUNK_SIZE) {
        return position
      }
      if (logEndPosition - position < (targetOperationLogSize - MAX_COMPACTION_CHUNK_SIZE).coerceAtLeast(512 * 1024L)) {
        return position
      }
      return null
    }

    while (iterator.hasNext() && positionToCompactTo == null) {
      when (val item = iterator.next()) {
        is ReadResult.Invalid -> throw item.cause
        is ReadResult.SingleOperation -> {
          val position = item.iterator().getPosition()
          positionToCompactTo = testCandidate(position)
        }
        is ReadResult.VFileEventRange -> {
          val beginPosition = item.begin().getPosition()
          positionToCompactTo = testCandidate(beginPosition)
        }
      }
    }
    if (positionToCompactTo == null) {
      throw IllegalStateException("Couldn't find new position for compaction: " +
                                  "logBegin=$logBeginPosition, logEnd = $logEndPosition, targetSize=$targetOperationLogSize")
    }
    return positionToCompactTo
  }

  override fun close() {
    closeModel()
    persistedOperationModeHandler.close()
  }

  enum class OperationMode {
    /** just drop excess data */
    DropData,

    /** try to preserve data in a separate storage with obsolete value clean up */
    CompactData,

    /** was CompactData but an error happened, operationally equal to DropData */
    Corrupted;

    companion object {
      internal val VALUES = OperationMode.values()
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VfsLogCompactionController::class.java)
    private val MAX_COMPACTION_CHUNK_SIZE: Long = SystemProperties.getLongProperty(
      "idea.vfs.log-vfs-operations.compaction-chunk-size",
      64L * 1024 * 1024
    )
  }
}