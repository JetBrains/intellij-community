// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.io.ChunkMMappedFileIO
import com.intellij.openapi.vfs.newvfs.persistent.log.io.StorageIO
import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.util.SkipListAdvancingPositionTracker
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.ResilientFileChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.io.path.div

class OperationLogStorageImpl(
  storagePath: Path,
  private val stringEnumerator: DataEnumerator<String>,
) : OperationLogStorage {
  private val storageIO: StorageIO
  private var persistentSize by PersistentVar.long(storagePath / "size")
  private val position: AdvancingPositionTracker

  init {
    FileUtil.ensureExists(storagePath.toFile())

    val fileChannel = ResilientFileChannel(storagePath / "descriptors", READ, WRITE, CREATE)
    storageIO = ChunkMMappedFileIO(fileChannel, FileChannel.MapMode.READ_WRITE)

    position = SkipListAdvancingPositionTracker(persistentSize ?: 0L)
  }

  override fun bytesForOperationDescriptor(tag: VfsOperationTag): Int =
    when (tag) {
      VfsOperationTag.NULL -> throw IllegalArgumentException("NULL operations are not expected in storage")

      VfsOperationTag.REC_ALLOC -> VfsOperation.RecordsOperation.AllocateRecord.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_ATTR_REC_ID -> VfsOperation.RecordsOperation.SetAttributeRecordId.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_CONTENT_RECORD_ID -> VfsOperation.RecordsOperation.SetContentRecordId.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_PARENT -> VfsOperation.RecordsOperation.SetParent.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_NAME_ID -> VfsOperation.RecordsOperation.SetNameId.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_FLAGS -> VfsOperation.RecordsOperation.SetFlags.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_LENGTH -> VfsOperation.RecordsOperation.SetLength.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_TIMESTAMP -> VfsOperation.RecordsOperation.SetTimestamp.VALUE_SIZE_BYTES
      VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED -> VfsOperation.RecordsOperation.MarkRecordAsModified.VALUE_SIZE_BYTES
      VfsOperationTag.REC_FILL_RECORD -> VfsOperation.RecordsOperation.FillRecord.VALUE_SIZE_BYTES
      VfsOperationTag.REC_CLEAN_RECORD -> VfsOperation.RecordsOperation.CleanRecord.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_VERSION -> VfsOperation.RecordsOperation.SetVersion.VALUE_SIZE_BYTES

      VfsOperationTag.ATTR_WRITE_ATTR -> VfsOperation.AttributesOperation.WriteAttribute.VALUE_SIZE_BYTES
      VfsOperationTag.ATTR_DELETE_ATTRS -> VfsOperation.AttributesOperation.DeleteAttributes.VALUE_SIZE_BYTES
      VfsOperationTag.ATTR_SET_VERSION -> VfsOperation.AttributesOperation.SetVersion.VALUE_SIZE_BYTES

      VfsOperationTag.CONTENT_WRITE_BYTES -> VfsOperation.ContentsOperation.WriteBytes.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_WRITE_STREAM -> VfsOperation.ContentsOperation.WriteStream.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_WRITE_STREAM_2 -> VfsOperation.ContentsOperation.WriteStream2.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_APPEND_STREAM -> VfsOperation.ContentsOperation.AppendStream.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_REPLACE_BYTES -> VfsOperation.ContentsOperation.ReplaceBytes.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD -> VfsOperation.ContentsOperation.AcquireNewRecord.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_ACQUIRE_RECORD -> VfsOperation.ContentsOperation.AcquireRecord.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_RELEASE_RECORD -> VfsOperation.ContentsOperation.ReleaseRecord.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_SET_VERSION -> VfsOperation.ContentsOperation.SetVersion.VALUE_SIZE_BYTES

      VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE -> VfsOperation.VFileEventOperation.EventStart.ContentChange.VALUE_SIZE_BYTES
      VfsOperationTag.VFILE_EVENT_COPY -> VfsOperation.VFileEventOperation.EventStart.Copy.VALUE_SIZE_BYTES
      VfsOperationTag.VFILE_EVENT_CREATE -> VfsOperation.VFileEventOperation.EventStart.Create.VALUE_SIZE_BYTES
      VfsOperationTag.VFILE_EVENT_DELETE -> VfsOperation.VFileEventOperation.EventStart.Delete.VALUE_SIZE_BYTES
      VfsOperationTag.VFILE_EVENT_MOVE -> VfsOperation.VFileEventOperation.EventStart.Move.VALUE_SIZE_BYTES
      VfsOperationTag.VFILE_EVENT_PROPERTY_CHANGED -> VfsOperation.VFileEventOperation.EventStart.PropertyChange.VALUE_SIZE_BYTES
      VfsOperationTag.VFILE_EVENT_END -> VfsOperation.VFileEventOperation.EventEnd.VALUE_SIZE_BYTES
    } + VfsOperationTag.SIZE_BYTES * 2

  private fun sizeOfValueInDescriptor(size: Int) = size - VfsOperationTag.SIZE_BYTES * 2

  override fun enqueueOperationWrite(scope: CoroutineScope, tag: VfsOperationTag, compute: () -> VfsOperation<*>) {
    val descrSize = bytesForOperationDescriptor(tag)
    val descrPos = position.beginAdvance(descrSize.toLong())
    scope.launch {
      val operation = compute()
      if (tag != operation.tag) {
        throw IllegalStateException("expected $tag, got ${operation.tag}")
      }
      writeOperation(descrPos, operation)
    }.invokeOnCompletion {
      if (it != null) { // TODO: probably need to check specific classes (also, what to do on coroutine cancellation?)
        try { // try to write error bounding tags
          storageIO.write(descrPos, byteArrayOf((-tag.ordinal).toByte()))
          storageIO.write(descrPos + descrSize - VfsOperationTag.SIZE_BYTES, byteArrayOf(tag.ordinal.toByte()))
        }
        catch (e: Throwable) {
          it.addSuppressed(IOException("failed to set error operation bounds", e))
        }
      }
      position.finishAdvance(descrPos)
    }
  }

  override fun writeOperation(position: Long, op: VfsOperation<*>) {
    val descriptorSize = bytesForOperationDescriptor(op.tag)
    val data = serialize(op)
    if (data.size != sizeOfValueInDescriptor(descriptorSize)) {
      throw IllegalStateException("for ${op.tag} expected value of size ${sizeOfValueInDescriptor(descriptorSize)}, got ${data.size}")
    }
    storageIO.offsetOutputStream(position).use {
      it.write(op.tag.ordinal)
      it.write(data)
      it.write(op.tag.ordinal)
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
    val op = deserialize<VfsOperation<*>>(tag, descrData.copyOfRange(1, descrData.size - 1))
    return OperationReadResult.Valid(op)
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
        IllegalStateException("failed to recover incomplete operation, bounding bytes: ${-probableTagByte} ${buf[0]}"))
    }
    return OperationReadResult.Incomplete(probableTag)
  }

  override fun readAll(action: (OperationReadResult) -> Boolean) {
    val iter = begin()
    while (iter.hasNext()) {
      if (!action(iter.next())) break
    }
  }

  override fun serialize(operation: VfsOperation<*>): ByteArray = operation.serializeValue(stringEnumerator)
  override fun <T : VfsOperation<*>> deserialize(tag: VfsOperationTag, data: ByteArray): T =
    VfsOperation.deserialize(tag, data, stringEnumerator)

  override fun size(): Long = position.getReadyPosition()
  override fun emergingSize(): Long = position.getCurrentAdvancePosition()
  override fun persistentSize() = persistentSize ?: 0L

  override fun begin(): OperationLogStorage.Iterator = IteratorImpl(0L)
  override fun end(): OperationLogStorage.Iterator = IteratorImpl(size())

  override fun flush() {
    val safePos = position.getReadyPosition()
    if (safePos != persistentSize) {
      storageIO.force()
      persistentSize = safePos
    }
  }

  override fun dispose() {
    flush()
    storageIO.close()
  }

  private inner class IteratorImpl(var iterPos: Long) : OperationLogStorage.Iterator {
    // [tag, previous operation, tag]  [tag, next operation, tag]
    //                      iterPos --^

    var invalidationFlag = false

    override fun hasNext(): Boolean {
      return iterPos < size() && !invalidationFlag
    }

    override fun hasPrevious(): Boolean {
      return iterPos > 0 && !invalidationFlag
    }

    override fun next(): OperationReadResult = readAt(iterPos).also {
      when (it) {
        is OperationReadResult.Valid -> iterPos += bytesForOperationDescriptor(it.operation.tag)
        is OperationReadResult.Incomplete -> iterPos += bytesForOperationDescriptor(it.tag)
        is OperationReadResult.Invalid -> invalidationFlag = true
      }
    }

    override fun previous(): OperationReadResult = readPreceding(iterPos).also {
      when (it) {
        is OperationReadResult.Valid -> iterPos -= bytesForOperationDescriptor(it.operation.tag)
        is OperationReadResult.Incomplete -> iterPos -= bytesForOperationDescriptor(it.tag)
        is OperationReadResult.Invalid -> invalidationFlag = true
      }
    }
  }
}