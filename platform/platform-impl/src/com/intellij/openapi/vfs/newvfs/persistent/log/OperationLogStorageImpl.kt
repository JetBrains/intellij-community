// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AppendLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AppendLogStorage.AppendContext
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AppendLogStorage.Companion.Mode
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getMeter
import com.intellij.platform.diagnostic.telemetry.VFS
import com.intellij.util.SystemProperties
import com.intellij.util.io.DataEnumerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class OperationLogStorageImpl(
  storagePath: Path,
  private val stringEnumerator: DataEnumerator<String>,
  private val scope: CoroutineScope,
  private val maxWorkers: Int,
  trackJobStatistics: Boolean = true
) : OperationLogStorage {
  private val appendLogStorage: AppendLogStorage = AppendLogStorage(storagePath, Mode.ReadWrite, PAGE_SIZE)
  private val writeQueue = Channel<() -> Unit>(
    capacity = SystemProperties.getIntProperty("idea.vfs.log-vfs-operations.buffer-capacity", 5_000),
    BufferOverflow.SUSPEND
  )

  private val telemetry = object : AutoCloseable {
    val jobsOffloadedToWorkers: AtomicLong = AtomicLong(0)
    val jobsPerformedOnMainThread: AtomicLong = AtomicLong(0)
    val workersLaunched: AtomicInteger = AtomicInteger(0)

    @Volatile
    private var batchCallback: AutoCloseable? = null

    fun setupTelemetry() {
      val meter = getMeter(VFS)

      val jobsOffloadedToWorkersCounter = meter.counterBuilder("VfsLog.OperationsLogStorage.jobsOffloadedToWorkers").buildObserver()
      val jobsPerformedOnMainThreadCounter = meter.counterBuilder("VfsLog.OperationsLogStorage.jobsPerformedOnMainThread").buildObserver()
      val workersLaunchedGauge = meter.gaugeBuilder("VfsLog.OperationsLogStorage.workersLaunched").ofLongs().buildObserver()

      batchCallback = meter.batchCallback(
        {
          jobsOffloadedToWorkersCounter.record(jobsOffloadedToWorkers.get())
          jobsPerformedOnMainThreadCounter.record(jobsPerformedOnMainThread.get())
          workersLaunchedGauge.record(workersLaunched.get().toLong())
        },
        jobsOffloadedToWorkersCounter, jobsPerformedOnMainThreadCounter, workersLaunchedGauge
      )
    }

    override fun close() {
      batchCallback?.close()
    }
  }

  private fun launchWorker(workersBefore: Int): Boolean { // can be invoked concurrently
    if (telemetry.workersLaunched.compareAndSet(workersBefore, workersBefore + 1)) {
      scope.launch { writeWorker() }
      return true
    }
    return false
  }

  init {
    check(launchWorker(0))
    if (maxWorkers > 1) check(launchWorker(1))

    if (trackJobStatistics) telemetry.setupTelemetry()
  }

  override fun bytesForOperationDescriptor(tag: VfsOperationTag): Int =
    tag.operationSerializer.valueSizeBytes + VfsOperationTag.SIZE_BYTES * 2

  private fun sizeOfValueInDescriptor(size: Int) = size - VfsOperationTag.SIZE_BYTES * 2

  private suspend fun writeWorker() {
    withContext(NonCancellable) {
      for (job in writeQueue) {
        performWriteJob(job)
      }
    }
  }

  private fun performWriteJob(job: () -> Unit) {
    try {
      job()
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  private fun enqueueWriteJob(job: () -> Unit) {
    val submitted = writeQueue.trySend(job)
    if (submitted.isSuccess) {
      telemetry.jobsOffloadedToWorkers.incrementAndGet()
    }
    else {
      val missedJobCount = telemetry.jobsPerformedOnMainThread.incrementAndGet()
      performWriteJob(job)
      launchMoreWorkers(missedJobCount)
    }
  }

  private fun launchMoreWorkers(missedJobCount: Long) {
    val currentWorkers = telemetry.workersLaunched.get()
    if (missedJobCount <= 0 || currentWorkers >= maxWorkers) return
    // large number of workers increase contention on the job channel, so let's tolerate missed jobs if there aren't many of them
    val shouldLaunch: Boolean = when (currentWorkers) {
      1 -> missedJobCount >= 1000
      2 -> missedJobCount >= 5000
      3 -> missedJobCount >= 10000
      else -> missedJobCount >= currentWorkers * 10000L
    }
    if (shouldLaunch) launchWorker(currentWorkers)
  }

  private inner class TrackContext(val tag: VfsOperationTag, val appendLogEntry: AppendContext): OperationTracker {
    override fun completeTracking(trackingCompletedCallback: (() -> Unit)?, composeOperation: () -> VfsOperation<*>) {
      if (trackingCompletedCallback == null) {
        enqueueWriteJob {
          writeJobImpl(tag, composeOperation, appendLogEntry)
        }
      } else {
        enqueueWriteJob {
          try {
            writeJobImpl(tag, composeOperation, appendLogEntry)
          } finally {
            trackingCompletedCallback()
          }
        }
      }
    }
  }

  override fun trackOperation(tag: VfsOperationTag): OperationTracker {
    val appendEntry = appendLogStorage.appendEntry(bytesForOperationDescriptor(tag).toLong())
    return TrackContext(tag, appendEntry)
  }

  private fun writeJobImpl(
    tag: VfsOperationTag,
    composeOperation: () -> VfsOperation<*>,
    appendEntry: AppendContext
  ) {
    appendEntry.use {
      try {
        val operation = composeOperation()
        check(tag == operation.tag) { "expected $tag, got ${operation.tag}" }
        it.fillEntry {
          write(operation.tag.ordinal)
          operation.serializer.serializeOperation(operation, stringEnumerator, this)
          write(operation.tag.ordinal)
        }
      }
      catch (e: Throwable) {
        try { // try to write error bounding tags
          val descriptorSize = bytesForOperationDescriptor(tag)
          appendEntry.write(0, byteArrayOf((-tag.ordinal).toByte()))
          appendEntry.write(descriptorSize.toLong() - VfsOperationTag.SIZE_BYTES, byteArrayOf(tag.ordinal.toByte()))
        }
        catch (e2: Throwable) {
          e.addSuppressed(IOException("failed to set error operation bounds", e2))
        }
        throw e
      }
    }
  }

  fun dropOperationsUpTo(position: Long): Unit = appendLogStorage.clearUpTo(position)

  override fun readAt(position: Long): OperationReadResult = try {
    readFirstTag(position, ::readWholeDescriptor)
  }
  catch (e: Throwable) {
    OperationReadResult.Invalid(e)
  }

  override fun readPreceding(position: Long): OperationReadResult = try {
    readLastTag(position) { actualPos, _ -> readAt(actualPos) }
  }
  catch (e: Throwable) {
    OperationReadResult.Invalid(e)
  }

  override fun readAtFiltered(position: Long, toReadMask: VfsOperationTagsMask): OperationReadResult = try {
    readFirstTag(position) { _, tag ->
      if (toReadMask.contains(tag)) return readWholeDescriptor(position, tag)
      // validate right tag
      val descrSize = bytesForOperationDescriptor(tag)
      val buf = ByteArray(VfsOperationTag.SIZE_BYTES)
      appendLogStorage.read(position + descrSize - VfsOperationTag.SIZE_BYTES, buf)
      if (tag.ordinal.toByte() != buf[0]) {
        return OperationReadResult.Invalid(IllegalStateException("bounding tags do not match: ${tag.ordinal} ${buf[0]}"))
      }
      return OperationReadResult.Incomplete(tag)
    }
  }
  catch (e: Throwable) {
    OperationReadResult.Invalid(e)
  }

  override fun readPrecedingFiltered(position: Long, toReadMask: VfsOperationTagsMask): OperationReadResult = try {
    readLastTag(position) { actualPos, tag ->
      if (toReadMask.contains(tag)) return readWholeDescriptor(actualPos, tag)
      // validate left tag
      val buf = ByteArray(VfsOperationTag.SIZE_BYTES)
      appendLogStorage.read(position - bytesForOperationDescriptor(tag), buf)
      if (tag.ordinal.toByte() != buf[0]) {
        return OperationReadResult.Invalid(IllegalStateException("bounding tags do not match: ${buf[0]} ${tag.ordinal}"))
      }
      return OperationReadResult.Incomplete(tag)
    }
  }
  catch (e: Throwable) {
    OperationReadResult.Invalid(e)
  }

  private inline fun readFirstTag(position: Long,
                                  cont: (position: Long, tag: VfsOperationTag) -> OperationReadResult): OperationReadResult {
    val buf = ByteArray(VfsOperationTag.SIZE_BYTES)
    appendLogStorage.read(position, buf)
    if (buf[0] < 0.toByte()) {
      return recoverOperationTag(position, buf)
    }
    if (buf[0] == 0.toByte() || buf[0] >= VfsOperationTag.entries.size) {
      return OperationReadResult.Invalid(IllegalStateException("read tag value is ${buf[0]}"))
    }
    val tag = VfsOperationTag.entries[buf[0].toInt()]
    return cont(position, tag)
  }

  private inline fun readLastTag(position: Long,
                                 cont: (actualDescriptorPosition: Long, tag: VfsOperationTag) -> OperationReadResult): OperationReadResult {
    val buf = ByteArray(VfsOperationTag.SIZE_BYTES)
    appendLogStorage.read(position - 1, buf)
    if (buf[0] !in 1 until VfsOperationTag.entries.size) {
      return OperationReadResult.Invalid(IllegalStateException("read last tag value is ${buf[0]}"))
    }
    val tag = VfsOperationTag.entries[buf[0].toInt()]
    val descrSize = bytesForOperationDescriptor(tag)
    return cont(position - descrSize, tag)
  }

  private fun readWholeDescriptor(position: Long, tag: VfsOperationTag): OperationReadResult {
    val descrSize = bytesForOperationDescriptor(tag)
    val descrData = ByteArray(descrSize)
    appendLogStorage.read(position, descrData)
    if (descrData.last() != descrData.first()) {
      return OperationReadResult.Invalid(IllegalStateException("bounding tags do not match: ${descrData.first()} ${descrData.last()}"))
    }
    val op = tag.operationSerializer.deserializeOperation(
      descrData, VfsOperationTag.SIZE_BYTES, sizeOfValueInDescriptor(descrSize),
      // TODO: both variants seem to give nearly the same performance, but that needs to be checked in the future
      //descrData.copyOfRange(VfsOperationTag.SIZE_BYTES, descrSize - VfsOperationTag.SIZE_BYTES),
      stringEnumerator
    )
    return OperationReadResult.Complete(op)
  }

  private fun recoverOperationTag(position: Long, buf: ByteArray): OperationReadResult {
    val probableTagByte = -buf[0]
    if (probableTagByte >= VfsOperationTag.entries.size) {
      return OperationReadResult.Invalid(IllegalStateException("read tag value is ${buf}"))
    }
    val probableTag = VfsOperationTag.entries[probableTagByte]
    val descriptorSize = bytesForOperationDescriptor(probableTag)
    appendLogStorage.read(position + descriptorSize - VfsOperationTag.SIZE_BYTES, buf)
    if (probableTagByte != buf[0].toInt()) {
      return OperationReadResult.Invalid(
        IllegalStateException("failed to recover incomplete operation, bounding bytes: ${-probableTagByte} ${buf[0]}")
      )
    }
    return OperationReadResult.Incomplete(probableTag)
  }

  override fun readAll(action: (OperationReadResult) -> Boolean) {
    val iter = begin()
    while (iter.hasNext()) {
      if (!action(iter.next())) break
    }
  }

  override fun size(): Long = appendLogStorage.size()
  override fun emergingSize(): Long = appendLogStorage.emergingSize()
  override fun persistentSize(): Long = appendLogStorage.persistentSize()
  override fun startOffset(): Long = appendLogStorage.startOffset()

  override fun begin(): OperationLogStorage.Iterator = UnconstrainedIterator(startOffset())
  override fun end(): OperationLogStorage.Iterator = UnconstrainedIterator(size())
  override fun iterator(position: Long): OperationLogStorage.Iterator = UnconstrainedIterator(position)

  fun constrainedIterator(position: Long, allowedRangeBegin: Long, allowedRangeEnd: Long): OperationLogStorage.Iterator =
    ConstrainedIterator(position, allowedRangeBegin, allowedRangeEnd)

  /**
   * @return begin and end iterators that are constrained to currently available range, i.e. copies of these
   *  iterators won't traverse past the bounds of that range. Note that [end] may change even if [VfsLogQueryContext] is held.
   */
  fun currentlyAvailableRangeIterators(): Pair<OperationLogStorage.Iterator, OperationLogStorage.Iterator> {
    val allowedRangeEnd = size()
    val allowedRangeBegin = startOffset()
    return ConstrainedIterator(allowedRangeBegin, allowedRangeBegin, allowedRangeEnd) to
      ConstrainedIterator(allowedRangeEnd, allowedRangeBegin, allowedRangeEnd)
  }

  override fun flush() {
    appendLogStorage.flush()
  }

  fun closeWriteQueue() {
    appendLogStorage.forbidNewAppends()
    writeQueue.close()
  }

  override fun dispose() {
    flush()
    // FIXME: safe close()
    telemetry.close()
    appendLogStorage.close()
  }

  abstract inner class IteratorBase(
    protected var pos: Long,
    protected var invalidationFlag: Boolean = false,
  ) : OperationLogStorage.Iterator {
    // [tag, previous operation, tag]  [tag, next operation, tag]
    //                      position --^
    override fun getPosition(): Long = pos

    override fun next(): OperationReadResult = readAt(pos).alsoAdvance()
    override fun nextFiltered(mask: VfsOperationTagsMask): OperationReadResult = readAtFiltered(pos, mask).alsoAdvance()

    override fun previous(): OperationReadResult = readPreceding(pos).alsoRetreat()
    override fun previousFiltered(mask: VfsOperationTagsMask): OperationReadResult = readPrecedingFiltered(pos, mask).alsoRetreat()

    private fun OperationReadResult.alsoAdvance() = this.also {
      when (this) {
        is OperationReadResult.Complete -> pos += bytesForOperationDescriptor(operation.tag)
        is OperationReadResult.Incomplete -> pos += bytesForOperationDescriptor(tag)
        is OperationReadResult.Invalid -> invalidationFlag = true
      }
    }

    private fun OperationReadResult.alsoRetreat() = this.also {
      when (this) {
        is OperationReadResult.Complete -> pos -= bytesForOperationDescriptor(operation.tag)
        is OperationReadResult.Incomplete -> pos -= bytesForOperationDescriptor(tag)
        is OperationReadResult.Invalid -> invalidationFlag = true
      }
    }

    override fun equals(other: Any?): Boolean {
      if (other !is IteratorBase) return false
      return pos == other.pos
    }

    override fun hashCode(): Int {
      return pos.hashCode()
    }

    override fun toString(): String {
      return "Iterator(position=$pos)"
    }
  }

  private inner class UnconstrainedIterator(position: Long, invalidationFlag: Boolean = false)
    : IteratorBase(position, invalidationFlag) {
    override fun hasNext(): Boolean {
      return pos < size() && !invalidationFlag
    }

    override fun hasPrevious(): Boolean {
      return pos > startOffset() && !invalidationFlag
    }

    override fun copy(): UnconstrainedIterator = UnconstrainedIterator(pos, invalidationFlag)
  }

  private inner class ConstrainedIterator(
    position: Long,
    val allowedRangeBegin: Long, // inclusive
    val allowedRangeEnd: Long, // inclusive
    invalidationFlag: Boolean = false
  ) : IteratorBase(position, invalidationFlag) {
    override fun hasNext(): Boolean {
      return pos < allowedRangeEnd && !invalidationFlag
    }

    override fun hasPrevious(): Boolean {
      return pos > allowedRangeBegin && !invalidationFlag
    }

    override fun copy(): ConstrainedIterator = ConstrainedIterator(pos, allowedRangeBegin, allowedRangeEnd, invalidationFlag)
  }

  companion object {
    private const val MiB = 1024 * 1024
    private const val PAGE_SIZE = 64 * MiB

    private val LOG = Logger.getInstance(OperationLogStorageImpl::class.java)
  }
}
