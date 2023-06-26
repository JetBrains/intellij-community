// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.log.PayloadRef.Source
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AppendLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.io.AppendLogStorage.Companion.Mode
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.DataOutputStream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import java.io.*
import java.nio.file.Path

class PayloadStorageImpl(
  storagePath: Path,
) : PayloadStorage {
  override val sourcesDeclaration: PersistentSet<Source> = persistentSetOf(Source.PayloadStorage)

  private val appendLogStorage: AppendLogStorage

  init {
    FileUtil.ensureExists(storagePath.toFile())

    appendLogStorage = AppendLogStorage(storagePath, Mode.ReadWrite, CHUNK_SIZE)
  }

  override fun writePayload(sizeBytes: Long, body: OutputStream.() -> Unit): PayloadRef {
    require(sizeBytes >= 0)
    val serializedSize = ByteArrayOutputStream(10) // 1 + (64 - 6) / 7 < 10
    DataInputOutputUtil.writeLONG(DataOutputStream(serializedSize), sizeBytes)

    val fullSize = serializedSize.size().toLong() + sizeBytes
    return appendLogStorage.appendEntry(fullSize).use {
      it.fillEntry {
        write(serializedSize.toByteArray())
        body()
      }
      PayloadRef(it.position, Source.PayloadStorage)
    }
  }

  override fun readPayload(payloadRef: PayloadRef): State.DefinedState<ByteArray> {
    if (payloadRef.source != Source.PayloadStorage) throw IllegalArgumentException("PayloadStorageImpl cannot read $payloadRef")
    if (payloadRef.offset >= appendLogStorage.size()) {
      return State.NotAvailable("failed to read $payloadRef: data was not written yet")
    }
    val buf = ByteArray(10) // 1 + (64 - 6) / 7 < 10
    appendLogStorage.read(payloadRef.offset, buf)
    val inp = ByteArrayInputStream(buf)
    val sizeBytes = try {
      DataInputOutputUtil.readLONG(DataInputStream(inp))
    }
    catch (e: IOException) {
      return State.NotAvailable("failed to read $payloadRef: size marker is malformed", e)
    }
    // hack: inp.available() = count - pos, so pos = count - inp.available() = buf.size - inp.available()
    val dataOffset = buf.size - inp.available()
    if (sizeBytes < 0) {
      return State.NotAvailable("failed to read $payloadRef: size marker is negative ($sizeBytes)")
    }
    val data = ByteArray(sizeBytes.toInt())
    appendLogStorage.read(payloadRef.offset + dataOffset, data)
    return data.let(State::Ready)
  }

  override fun size(): Long = appendLogStorage.size()

  override fun flush() {
    appendLogStorage.flush()
  }

  override fun dispose() {
    flush()
    appendLogStorage.close()
  }

  companion object {
    private const val MiB = 1024 * 1024
    private const val CHUNK_SIZE = 32 * MiB
  }
}