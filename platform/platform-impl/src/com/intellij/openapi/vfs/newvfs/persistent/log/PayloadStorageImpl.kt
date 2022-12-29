// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.UnInterruptibleFileChannel
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong

class PayloadStorageImpl(
  private val storagePath: Path,
) : PayloadStorage {
  private val fileChannel = UnInterruptibleFileChannel(storagePath,
                                                       StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
  private val position = AtomicLong(fileChannel.size())

  override suspend fun writePayload(sizeBytes: Long, body: suspend OutputStream.() -> Unit): PayloadRef {
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
    val payloadPos = position.getAndAdd(fullSize)

    withContext(Dispatchers.IO) {
      fileChannel.write(ByteBuffer.wrap(buf.internalBuffer, 0, out.writtenBytesCount), payloadPos)
      FileChannelOffsetOutputStream(fileChannel, payloadPos + out.writtenBytesCount).run {
        body();
        validateWrittenBytesCount(sizeBytes)
      }
    }
    return PayloadRef(payloadPos)
  }

  override suspend fun readAt(ref: PayloadRef): ByteArray? {
    if (ref == PayloadRef.ZERO_SIZE) return ByteArray(0)
    // TODO: revisit unexpected value cases
    return withContext(Dispatchers.IO) {
      val buf = ByteBuffer.allocate(10) // 1 + (64 - 6) / 7 < 10
      if (fileChannel.read(buf, ref.offset) < 1) {
        return@withContext null
      }
      val inp = ByteArrayInputStream(buf.toByteArray())
      val sizeBytes = try {
        DataInputOutputUtil.readLONG(DataInputStream(inp))
      }
      catch (e: EOFException) {
        return@withContext null
      }
      // dirty hack: inp.available() = count - pos, so pos = count - inp.available() = buf.position() - inp.available()
      val dataOffset = buf.position() - inp.available()
      if (sizeBytes < 0) {
        return@withContext null
      }
      val data = ByteArray(sizeBytes.toInt())
      if (fileChannel.read(ByteBuffer.wrap(data), ref.offset + dataOffset) != sizeBytes.toInt()) {
        return@withContext null
      }
      data
    }
  }

  override fun size(): Long = fileChannel.size()

  override fun flush() {
    fileChannel.force(false)
  }

  override fun dispose() {
    fileChannel.close()
  }
}