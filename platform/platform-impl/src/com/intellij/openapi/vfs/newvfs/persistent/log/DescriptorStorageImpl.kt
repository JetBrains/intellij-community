// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.log.io.ChunkMMappedFileIO
import com.intellij.openapi.vfs.newvfs.persistent.log.io.StorageIO
import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.ResilientFileChannel
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.io.path.div

class DescriptorStorageImpl(
  storagePath: Path,
  private val stringEnumerator: DataEnumerator<String>
) : DescriptorStorage {
  private val storageIO: StorageIO
  private var lastSafeSize by PersistentVar.long(storagePath / "size")
  private val position: AdvancingPositionTracker

  init {
    FileUtil.ensureExists(storagePath.toFile())

    val fileChannel = ResilientFileChannel(storagePath / "descriptors", READ, WRITE, CREATE)
    storageIO = ChunkMMappedFileIO(fileChannel, FileChannel.MapMode.READ_WRITE)

    position = AdvancingPositionTracker(lastSafeSize ?: 0L)
  }

  override fun bytesForDescriptor(tag: VfsOperationTag): Int =
    when (tag) {
      VfsOperationTag.NULL -> throw IllegalArgumentException("NULL descriptors are not expected in storage")

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

  /**
   * How an operation gets written down:
   * 1. we allocate space for the descriptor and write (-tag value) at the first byte and (tag value) at the last byte
   * 2. we do all the necessary additional logic that the operation needs (e.g. write the payload in PayloadStorage before finalizing the descriptor)
   * 3. we rewrite the first byte as (tag value), designating that the descriptor is (hopefully) was written completely and correctly
   */
  override fun writeDescriptor(tag: VfsOperationTag, compute: () -> VfsOperation<*>) {
    val descrSize = bytesForDescriptor(tag)
    position.track(descrSize.toLong()) { descrPos ->
      // XXX: in case of mmap-based storage, compiler can reorder operations here so that tag.ordinal.toByte() will be written right away,
      // but it is unlikely, because there is too much code in between
      storageIO.write(descrPos, byteArrayOf((-tag.ordinal).toByte()))
      storageIO.write(descrPos + descrSize - VfsOperationTag.SIZE_BYTES, byteArrayOf(tag.ordinal.toByte()))
      val op = compute()
      if (tag != op.tag) {
        throw IllegalStateException("expected $tag, got ${op.tag}")
      }
      val data = serialize(op)
      if (data.size != sizeOfValueInDescriptor(descrSize)) {
        throw IllegalStateException("for $tag expected value of size ${sizeOfValueInDescriptor(descrSize)}, got ${data.size}")
      }
      storageIO.write(descrPos + VfsOperationTag.SIZE_BYTES, data)
      storageIO.write(descrPos, byteArrayOf(tag.ordinal.toByte()))
    }
  }

  override fun readAt(position: Long, action: (VfsOperation<*>?) -> Unit) {
    val buf = ByteArray(VfsOperationTag.SIZE_BYTES)
    storageIO.read(position, buf)
    validateTagByte(buf[0]) {
      action(null)
      return
    }
    val tag = VfsOperationTag.values()[buf[0].toInt()]
    // check right bound
    val descrSize = bytesForDescriptor(tag)
    storageIO.read(position + descrSize - VfsOperationTag.SIZE_BYTES, buf)
    validateTagByte(buf[0]) {
      action(null)
      return
    }
    if (tag.ordinal.toByte() != buf[0]) {
      // not matching bounding tags
      action(null)
      return
    }
    val bytesToRead = sizeOfValueInDescriptor(bytesForDescriptor(tag))
    val data = ByteArray(bytesToRead)
    storageIO.read(position + VfsOperationTag.SIZE_BYTES, data)
    val descr = deserialize<VfsOperation<*>>(tag, data)
    action(descr)
  }

  private inline fun validateTagByte(tagByte: Byte,
                                     onInvalid: () -> Unit) {
    // TODO: revisit unexpected value cases, throw an exception?
    if (tagByte == 0.toByte()) {
      onInvalid()
      return
    }
    if (tagByte < 0) {
      // partial write
      onInvalid()
      return
    }
    // tagByte > 0
    if (tagByte >= VfsOperationTag.values().size) {
      // incorrect value
      onInvalid()
      return
    }
  }

  override fun readAll(action: (VfsOperation<*>) -> Boolean) {
    var pos = 0L
    val size = size()
    while (pos < size) {
      readAt(pos) {
        if (it == null) {
          return@readAt
        }
        if (!action(it)) {
          return@readAt
        }
        pos += bytesForDescriptor(it.tag)
      }
    }
  }

  override fun serialize(operation: VfsOperation<*>): ByteArray = operation.serializeValue(stringEnumerator)
  override fun <T : VfsOperation<*>> deserialize(tag: VfsOperationTag, data: ByteArray): T = VfsOperation.deserialize(tag, data,
                                                                                                                      stringEnumerator)

  override fun size(): Long = lastSafeSize ?: 0L

  override fun flush() {
    val safePos = position.getMinInflightPosition()
    storageIO.force()
    lastSafeSize = safePos
  }

  override fun dispose() {
    flush()
    storageIO.close()
  }
}