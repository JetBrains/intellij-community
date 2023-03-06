// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.log.io.ByteCountingOutputStream
import com.intellij.openapi.vfs.newvfs.persistent.log.io.ChunkMMappedFileIO
import com.intellij.openapi.vfs.newvfs.persistent.log.io.StorageIO
import com.intellij.openapi.vfs.newvfs.persistent.log.util.AdvancingPositionTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.util.SkipListAdvancingPositionTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.util.trackAdvance
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.ResilientFileChannel
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.io.path.div

class PayloadStorageImpl(
  storagePath: Path,
) : PayloadStorage {
  private val storageIO: StorageIO
  private var lastSafeSize by PersistentVar.long(storagePath / "size")
  private val position: AdvancingPositionTracker

  init {
    FileUtil.ensureExists(storagePath.toFile())

    val fileChannel = ResilientFileChannel(storagePath / "payload", READ, WRITE, CREATE)
    storageIO = ChunkMMappedFileIO(fileChannel, FileChannel.MapMode.READ_WRITE)

    position = SkipListAdvancingPositionTracker(lastSafeSize ?: 0L)
  }

  override fun writePayload(sizeBytes: Long, body: OutputStream.() -> Unit): PayloadRef {
    assert(sizeBytes >= 0)
    if (sizeBytes == 0L) {
      ByteCountingOutputStream().run {
        body()
        validateWrittenBytesCount(0L)
      }
      return PayloadRef.ZERO_SIZE
    }
    val buf = BufferExposingByteArrayOutputStream(10) // 1 + (64 - 6) / 7 < 10
    val out = DataOutputStream(buf)
    DataInputOutputUtil.writeLONG(out, sizeBytes)

    val fullSize = out.writtenBytesCount.toLong() + sizeBytes
    return position.trackAdvance(fullSize) { payloadPos ->
      storageIO.write(payloadPos, buf.internalBuffer, 0, out.writtenBytesCount)
      storageIO.offsetOutputStream(payloadPos + out.writtenBytesCount).run {
        body()
        validateWrittenBytesCount(sizeBytes)
      }
      PayloadRef(payloadPos)
    }
  }

  override fun readAt(ref: PayloadRef): ByteArray? {
    if (ref == PayloadRef.ZERO_SIZE) return ByteArray(0)
    // TODO: revisit unexpected value cases
    val buf = ByteArray(10) // 1 + (64 - 6) / 7 < 10
    storageIO.read(ref.offset, buf)
    val inp = ByteArrayInputStream(buf)
    val sizeBytes = try {
      DataInputOutputUtil.readLONG(DataInputStream(inp))
    }
    catch (e: EOFException) {
      return null
    }
    // dirty hack: inp.available() = count - pos, so pos = count - inp.available() = buf.size - inp.available()
    val dataOffset = buf.size - inp.available()
    if (sizeBytes < 0) {
      return null
    }
    val data = ByteArray(sizeBytes.toInt())
    storageIO.read(ref.offset + dataOffset, data)
    return data
  }

  override fun size(): Long = lastSafeSize ?: 0L

  override fun flush() {
    val safeSize = position.getReadyPosition()
    storageIO.force()
    lastSafeSize = safeSize
  }

  override fun dispose() {
    flush()
    storageIO.close()
  }
}