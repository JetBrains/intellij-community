// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.UnInterruptibleFileChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.div

class DescriptorStorageImpl(
  storagePath: Path,
  private val stringEnumerator: SuspendDataEnumerator<String>
) : DescriptorStorage {
  private val mmapIO: MappedFileIOUtil
  private var lastSafeSize by PersistentVar.long(storagePath / "size")
  private val position: AdvancingPositionTracker

  init {
    FileUtil.ensureExists(storagePath.toFile())

    val fileChannel = UnInterruptibleFileChannel(storagePath / "descriptors",
                                                 StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    mmapIO = MappedFileIOUtil(fileChannel, FileChannel.MapMode.READ_WRITE)

    lastSafeSize.let {
      if (it != null) {
        position = AdvancingPositionTracker(it)
      }
      else {
        position = AdvancingPositionTracker(0L)
      }
    }
  }

  override fun bytesForDescriptor(tag: VfsOperationTag): Int =
    when (tag) {
      VfsOperationTag.NULL -> throw IllegalArgumentException("NULL descriptors are not expected in storage")

      VfsOperationTag.REC_ALLOC -> VfsOperation.RecordsOperation.AllocateRecord.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_ATTR_REC_ID -> VfsOperation.RecordsOperation.SetAttributeRecordId.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_CONTENT_RECORD_ID -> VfsOperation.RecordsOperation.SetContentRecordId.VALUE_SIZE_BYTES
      VfsOperationTag.REC_SET_PARENT -> TODO()
      VfsOperationTag.REC_SET_NAME_ID -> TODO()
      VfsOperationTag.REC_SET_FLAGS -> TODO()
      VfsOperationTag.REC_PUT_LENGTH -> TODO()
      VfsOperationTag.REC_PUT_TIMESTAMP -> TODO()
      VfsOperationTag.REC_MARK_RECORD_AS_MODIFIED -> TODO()
      VfsOperationTag.REC_FILL_RECORD -> TODO()
      VfsOperationTag.REC_CLEAN_RECORD -> TODO()
      VfsOperationTag.REC_SET_VERSION -> TODO()

      VfsOperationTag.ATTR_WRITE_ATTR -> VfsOperation.AttributesOperation.WriteAttribute.VALUE_SIZE_BYTES
      VfsOperationTag.ATTR_DELETE_ATTRS -> TODO()
      VfsOperationTag.ATTR_SET_VERSION -> TODO()

      VfsOperationTag.CONTENT_WRITE_BYTES -> VfsOperation.ContentsOperation.WriteBytes.VALUE_SIZE_BYTES
      VfsOperationTag.CONTENT_WRITE_STREAM -> TODO()
      VfsOperationTag.CONTENT_WRITE_STREAM_2 -> TODO()
      VfsOperationTag.CONTENT_APPEND_STREAM -> TODO()
      VfsOperationTag.CONTENT_REPLACE_BYTES -> TODO()
      VfsOperationTag.CONTENT_ACQUIRE_NEW_RECORD -> TODO()
      VfsOperationTag.CONTENT_RELEASE_RECORD -> TODO()
      VfsOperationTag.CONTENT_SET_VERSION -> TODO()
    } + VfsOperationTag.SIZE_BYTES * 2

  private fun sizeOfValueInDescriptor(size: Int) = size - VfsOperationTag.SIZE_BYTES * 2

  /**
   * How an operation gets written down:
   * 1. we allocate space for the descriptor and write (-tag value) at the first byte and (tag value) at the last byte
   * 2. we do all the necessary additional logic that the operation needs (e.g. write the payload in PayloadStorage before finalizing the descriptor)
   * 3. we rewrite the first byte as (tag value), designating that the descriptor is (hopefully) was written completely and correctly
   */
  override suspend fun writeDescriptor(tag: VfsOperationTag, compute: suspend () -> VfsOperation<*>) {
    val descrSize = bytesForDescriptor(tag)
    position.track(descrSize.toLong()) { descrPos ->
      mmapIO.write(descrPos, byteArrayOf((-tag.ordinal).toByte()))
      mmapIO.write(descrPos + descrSize - VfsOperationTag.SIZE_BYTES, byteArrayOf(tag.ordinal.toByte()))
      val op = compute()
      assert(tag == op.tag)
      val data = serialize(op)
      assert(data.size == sizeOfValueInDescriptor(descrSize))
      mmapIO.write(descrPos + VfsOperationTag.SIZE_BYTES, data)
      mmapIO.write(descrPos, byteArrayOf(tag.ordinal.toByte()))
    }
  }

  override suspend fun readAt(position: Long, action: suspend (VfsOperation<*>?) -> Unit) {
    val buf = ByteArray(VfsOperationTag.SIZE_BYTES)
    mmapIO.read(position, buf)
    validateTagByte(buf[0]) {
      action(null)
      return
    }
    val tag = VfsOperationTag.values()[buf[0].toInt()]
    // check right bound
    val descrSize = bytesForDescriptor(tag)
    mmapIO.read(position + descrSize - VfsOperationTag.SIZE_BYTES, buf)
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
    mmapIO.read(position + VfsOperationTag.SIZE_BYTES, data)
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

  override suspend fun readAll(action: suspend (VfsOperation<*>) -> Boolean) {
    var pos = 0L
    var cont = true
    coroutineScope {
      val size = size()
      while (cont && pos < size) {
        readAt(pos) {
          if (it == null) {
            cont = false
            return@readAt
          }
          launch {
            if (!action(it)) {
              cont = false
            }
          }
          pos += bytesForDescriptor(it.tag)
        }
      }
    }
  }

  override suspend fun serialize(operation: VfsOperation<*>): ByteArray = operation.serializeValue(stringEnumerator)
  override suspend fun <T : VfsOperation<*>> deserialize(tag: VfsOperationTag, data: ByteArray): T
    = VfsOperation.deserialize(tag, data, stringEnumerator)

  override fun size(): Long = lastSafeSize ?: 0L

  override fun flush() {
    val safePos = position.getMinInflightPosition()
    mmapIO.flush()
    lastSafeSize = safePos
  }

  override fun dispose() {
    flush()
    mmapIO.close()
  }
}