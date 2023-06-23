// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.io.ChunkMMappedFileIO
import com.intellij.openapi.vfs.newvfs.persistent.log.io.StorageIO
import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.util.SkipListAdvancingPositionTracker
import com.intellij.util.SystemProperties
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.ResilientFileChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.io.path.div

class OperationLogStorageImpl(
  storagePath: Path,
  private val stringEnumerator: DataEnumerator<String>,
  private val scope: CoroutineScope,
  writerJobsCount: Int
) : OperationLogStorage {
  private val storageIO: StorageIO
  private var persistentSize by PersistentVar.long(storagePath / "size")
  private val positionTracker: AdvancingPositionTracker
  private val writeQueue = Channel<() -> Unit>(
    capacity = SystemProperties.getIntProperty("idea.vfs.log-vfs-operations.buffer-capacity", 10_000),
    BufferOverflow.SUSPEND
  )

  @Volatile
  private var isClosed = false

  init {
    FileUtil.ensureExists(storagePath.toFile())

    val fileChannel = ResilientFileChannel(storagePath / "descriptors", READ, WRITE, CREATE)
    storageIO = ChunkMMappedFileIO(fileChannel, FileChannel.MapMode.READ_WRITE)

    positionTracker = SkipListAdvancingPositionTracker(persistentSize ?: 0L)

    repeat(writerJobsCount) { scope.launch { writeWorker() } }
  }

  override fun bytesForOperationDescriptor(tag: VfsOperationTag): Int =
    tag.operationSerializer.valueSizeBytes + VfsOperationTag.SIZE_BYTES * 2

  private fun sizeOfValueInDescriptor(size: Int) = size - VfsOperationTag.SIZE_BYTES * 2

  private suspend fun writeWorker() {
    for (job in writeQueue) {
      performWriteJob(job)
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

  override fun enqueueOperationWrite(tag: VfsOperationTag, compute: () -> VfsOperation<*>) {
    val descrSize = bytesForOperationDescriptor(tag)
    val descrPos = positionTracker.beginAdvance(descrSize.toLong())
    val job = { writeJobImpl(compute, tag, descrPos, descrSize) }
    val submitted = writeQueue.trySend(job)
    if (submitted.isSuccess) return
    performWriteJob(job)
  }

  private fun writeJobImpl(
    compute: () -> VfsOperation<*>,
    tag: VfsOperationTag,
    descrPos: Long,
    descrSize: Int
  ) {
    try {
      if (isClosed) throw CancellationException("OperationLogStorage is disposed")
      val operation = compute()
      if (tag != operation.tag) {
        throw IllegalStateException("expected $tag, got ${operation.tag}")
      }
      writeOperation(descrPos, operation)
    }
    catch (e: Throwable) {
      try { // try to write error bounding tags
        storageIO.write(descrPos, byteArrayOf((-tag.ordinal).toByte()))
        storageIO.write(descrPos + descrSize - VfsOperationTag.SIZE_BYTES, byteArrayOf(tag.ordinal.toByte()))
      }
      catch (e2: Throwable) {
        e.addSuppressed(IOException("failed to set error operation bounds", e2))
      }
      throw e
    }
    finally {
      positionTracker.finishAdvance(descrPos)
    }
  }

  override fun writeOperation(position: Long, op: VfsOperation<*>) {
    val descriptorSize = bytesForOperationDescriptor(op.tag)
    storageIO.offsetOutputStream(position).use {
      it.write(op.tag.ordinal)
      op.serializer.serializeOperation(op, stringEnumerator, it)
      it.write(op.tag.ordinal)
      it.validateWrittenBytesCount(descriptorSize.toLong())
    }
  }

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
      storageIO.read(position + descrSize - VfsOperationTag.SIZE_BYTES, buf)
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
      storageIO.read(position - bytesForOperationDescriptor(tag), buf)
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
    storageIO.read(position, buf)
    if (buf[0] < 0.toByte()) {
      return recoverOperationTag(position, buf)
    }
    if (buf[0] == 0.toByte() || buf[0] >= VfsOperationTag.values().size) {
      return OperationReadResult.Invalid(IllegalStateException("read tag value is ${buf[0]}"))
    }
    val tag = VfsOperationTag.values()[buf[0].toInt()]
    return cont(position, tag)
  }

  private inline fun readLastTag(position: Long,
                                 cont: (actualDescriptorPosition: Long, tag: VfsOperationTag) -> OperationReadResult): OperationReadResult {
    val buf = ByteArray(VfsOperationTag.SIZE_BYTES)
    storageIO.read(position - 1, buf)
    if (buf[0] !in 1 until VfsOperationTag.values().size) {
      return OperationReadResult.Invalid(IllegalStateException("read last tag value is ${buf[0]}"))
    }
    val tag = VfsOperationTag.values()[buf[0].toInt()]
    val descrSize = bytesForOperationDescriptor(tag)
    return cont(position - descrSize, tag)
  }

  private fun readWholeDescriptor(position: Long, tag: VfsOperationTag): OperationReadResult {
    val descrSize = bytesForOperationDescriptor(tag)
    val descrData = ByteArray(descrSize)
    storageIO.read(position, descrData)
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
    if (probableTagByte >= VfsOperationTag.values().size) {
      return OperationReadResult.Invalid(IllegalStateException("read tag value is ${buf}"))
    }
    val probableTag = VfsOperationTag.values()[probableTagByte]
    val descriptorSize = bytesForOperationDescriptor(probableTag)
    storageIO.read(position + descriptorSize - VfsOperationTag.SIZE_BYTES, buf)
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

  override fun size(): Long = positionTracker.getReadyPosition()
  override fun emergingSize(): Long = positionTracker.getCurrentAdvancePosition()
  override fun persistentSize(): Long = persistentSize ?: 0L

  override fun begin(): OperationLogStorage.Iterator = UnconstrainedIterator(0L)
  override fun end(): OperationLogStorage.Iterator = UnconstrainedIterator(size())

  /**
   * @return begin and end iterators that are constrained to currently available range, i.e. copies of these
   *  iterators won't traverse past the bounds of that range. Note that [end] may change even if [VfsLogQueryContext] is held.
   */
  fun currentlyAvailableRangeIterators(): Pair<OperationLogStorage.Iterator, OperationLogStorage.Iterator> {
    val allowedRangeEnd = size()
    val allowedRangeBegin = 0L
    return ConstrainedIterator(allowedRangeBegin, allowedRangeBegin, allowedRangeEnd) to
      ConstrainedIterator(allowedRangeEnd, allowedRangeBegin, allowedRangeEnd)
  }

  override fun flush() {
    val safePos = positionTracker.getReadyPosition()
    if (safePos != persistentSize) {
      storageIO.force()
      persistentSize = safePos
    }
  }

  override fun dispose() {
    isClosed = true
    writeQueue.close()
    flush()
    storageIO.close()
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
      return pos > 0L && !invalidationFlag
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
    private val LOG = Logger.getInstance(OperationLogStorageImpl::class.java)
  }
}
