// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.util.io.FileUtil
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService

private const val VERSION = 0

internal enum class WalOpCode(internal val code: Int) {
  PUT(0),
  REMOVE(1),
  APPEND(2)
}

internal class PersistentMapWal<K, V>(private val keyDescriptor: KeyDescriptor<K>,
                                      private val valueExternalizer: DataExternalizer<V>,
                                      private val file: Path,
                                      private val walIoExecutor: ExecutorService /*todo ensure sequential*/) {
  private val out = DataOutputStream(Files.newOutputStream(file))

  private fun WalOpCode.write(outputStream: DataOutputStream): Unit = outputStream.writeByte(code)

  private fun ByteArray.write(outputStream: DataOutputStream) {
    outputStream.writeInt(size)
    outputStream.write(this)
  }

  private fun <V> writeToByteArray(value: V, valueExternalizer: DataExternalizer<V>): ByteArray {
    val baos = UnsyncByteArrayOutputStream()
    valueExternalizer.save(DataOutputStream(baos), value)
    return baos.toByteArray()
  }

  private fun AppendablePersistentMap.ValueDataAppender.writeToByteArray(): ByteArray {
    val baos = UnsyncByteArrayOutputStream()
    append(DataOutputStream(baos))
    return baos.toByteArray()
  }

  @Throws(IOException::class)
  fun appendData(key: K, appender: AppendablePersistentMap.ValueDataAppender) {
    walIoExecutor.submit {
      WalOpCode.APPEND.write(out)
      keyDescriptor.save(out, key)
      appender.writeToByteArray().write(out)
    }
  }

  @Throws(IOException::class)
  fun put(key: K, value: V) {
    walIoExecutor.submit {
      WalOpCode.PUT.write(out)
      keyDescriptor.save(out, key)
      writeToByteArray(value, valueExternalizer).write(out)
    }
  }

  @Throws(IOException::class)
  fun remove(key: K) {
    walIoExecutor.submit {
      WalOpCode.REMOVE.write(out)
      keyDescriptor.save(out, key)
    }
  }

  @Throws(IOException::class) // todo rethrow io exception
  fun flush() {
    walIoExecutor.submit {
      out.flush()
    }.get()
  }

  @Throws(IOException::class) // todo rethrow io exception
  fun close() {
    walIoExecutor.submit {
      out.close()
    }.get()
  }

  @Throws(IOException::class)
  fun closeAndDelete() {
    close()
    FileUtil.deleteWithRenaming(file)
  }

  val version: Int = VERSION
}

sealed class WalEvent<K, V> {
  data class PutEvent<K, V>(val key: K, val value: V): WalEvent<K, V>()
  data class RemoveEvent<K, V>(val key: K): WalEvent<K, V>()
  data class AppendEvent<K, V>(val key: K, val data: ByteArray): WalEvent<K, V>() {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as AppendEvent<*, *>

      if (key != other.key) return false
      if (!data.contentEquals(other.data)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = key?.hashCode() ?: 0
      result = 31 * result + data.contentHashCode()
      return result
    }
  }
}

class PersistentMapWalPlayer<K, V>(private val keyDescriptor: KeyDescriptor<K>,
                                            private val valueExternalizer: DataExternalizer<V>,
                                            file: Path): Closeable {
  private val input = DataInputStream(Files.newInputStream(file))

  val version: Int = VERSION

  fun readWal(): Sequence<WalEvent<K, V>> = generateSequence {
    readNextEvent()
  }

  @Throws(IOException::class)
  override fun close() = input.close()

  @Throws(IOException::class)
  private fun readNextEvent(): WalEvent<K, V>? {
    val walOpCode: WalOpCode
    try {
      walOpCode = readNextOpCode(input)
    }
    catch (e: EOFException) {
      return null
    }

    return when (walOpCode) {
      WalOpCode.PUT -> WalEvent.PutEvent(keyDescriptor.read(input), valueExternalizer.read(DataInputStream(ByteArrayInputStream(readByteArray(input)))))
      WalOpCode.REMOVE -> WalEvent.RemoveEvent(keyDescriptor.read(input))
      WalOpCode.APPEND -> WalEvent.AppendEvent(keyDescriptor.read(input), readByteArray(input))
    }
  }

  private fun readByteArray(inputStream: DataInputStream): ByteArray =
    ByteArray(inputStream.readInt()).also { inputStream.readFully(it) }

  private fun readNextOpCode(inputStream: DataInputStream): WalOpCode =
    WalOpCode.values()[inputStream.readByte().toInt()]
}