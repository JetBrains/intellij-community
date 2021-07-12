// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.util.io.FileUtil
import java.io.*
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ExecutorService

private const val VERSION = 0

internal enum class WalOpCode(internal val code: Int) {
  PUT(0),
  REMOVE(1),
  APPEND(2)
}

internal class PersistentMapWal<K, V> @Throws(IOException::class) constructor(private val keyDescriptor: KeyDescriptor<K>,
                                                                              private val valueExternalizer: DataExternalizer<V>,
                                                                              private val file: Path,
                                                                              private val walIoExecutor: ExecutorService /*todo ensure sequential*/) {
  private val out: DataOutputStream

  val version: Int = VERSION

  init {
    ensureVersionCompatible(version, file)
    out = DataOutputStream(Files.newOutputStream(file, StandardOpenOption.WRITE, StandardOpenOption.APPEND).buffered())
  }

  @Throws(IOException::class)
  private fun ensureVersionCompatible(expectedVersion: Int, file: Path) {
    if (!Files.exists(file)) {
      Files.createDirectories(file.parent)
      DataOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)).use {
        DataInputOutputUtil.writeINT(it, expectedVersion)
      }
      return
    }
    val actualVersion = DataInputStream(Files.newInputStream(file, StandardOpenOption.READ)).use {
      DataInputOutputUtil.readINT(it)
    }
    if (actualVersion != expectedVersion) {
      throw VersionUpdatedException(file, expectedVersion, actualVersion)
    }
  }

  private fun WalOpCode.write(outputStream: DataOutputStream): Unit = outputStream.writeByte(code)

  private fun ByteArray.write(outputStream: DataOutputStream) {
    outputStream.writeInt(size)
    outputStream.write(this)
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
      writeData(value, valueExternalizer).write(out)
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

@Throws(IOException::class)
fun <K, V> restorePersistentMapFromWal(walFile: Path,
                                       outputMapFile: Path,
                                       keyDescriptor: KeyDescriptor<K>,
                                       valueExternalizer: DataExternalizer<V>): PersistentMap<K, V> {
  if (Files.exists(outputMapFile)) {
    throw FileAlreadyExistsException(outputMapFile.toString())
  }
  return restoreFromWal(walFile, keyDescriptor, valueExternalizer, object : Accumulator<K, V, PersistentMap<K, V>> {
    val result = PersistentHashMap(outputMapFile, keyDescriptor, valueExternalizer)

    override fun get(key: K): V? = result.get(key)

    override fun remove(key: K) = result.remove(key)

    override fun put(key: K, value: V) = result.put(key, value)

    override fun result(): PersistentMap<K, V> = result
  })
}

@Throws(IOException::class)
fun <K, V> restoreHashMapFromWal(walFile: Path,
                                 keyDescriptor: KeyDescriptor<K>,
                                 valueExternalizer: DataExternalizer<V>): Map<K, V> {
  return restoreFromWal(walFile, keyDescriptor, valueExternalizer, object : Accumulator<K, V, Map<K, V>> {
    private val map = linkedMapOf<K, V>()

    override fun get(key: K): V? = map.get(key)

    override fun remove(key: K) {
      map.remove(key)
    }

    override fun put(key: K, value: V) {
      map.put(key, value)
    }

    override fun result(): Map<K, V> = map
  })
}

private fun <V> readData(array: ByteArray, valueExternalizer: DataExternalizer<V>): V {
  return valueExternalizer.read(DataInputStream(ByteArrayInputStream(array)))
}

private fun <V> writeData(value: V, valueExternalizer: DataExternalizer<V>): ByteArray {
  val baos = UnsyncByteArrayOutputStream()
  valueExternalizer.save(DataOutputStream(baos), value)
  return baos.toByteArray()
}

private interface Accumulator<K, V, R> {
  fun get(key: K): V?

  fun remove(key: K)

  fun put(key: K, value: V)

  fun result(): R
}

private fun <K, V, R> restoreFromWal(walFile: Path,
                                     keyDescriptor: KeyDescriptor<K>,
                                     valueExternalizer: DataExternalizer<V>,
                                     accumulator: Accumulator<K, V, R>): R {
  return PersistentMapWalPlayer(keyDescriptor, valueExternalizer, walFile).use {
    for (walEvent in it.readWal()) {
      when (walEvent) {
        is WalEvent.AppendEvent -> {
          val previous = accumulator.get(walEvent.key)
          val currentData = if (previous == null) walEvent.data else writeData(previous, valueExternalizer) + walEvent.data
          accumulator.put(walEvent.key, readData(currentData, valueExternalizer))
        }
        is WalEvent.PutEvent -> accumulator.put(walEvent.key, walEvent.value)
        is WalEvent.RemoveEvent -> accumulator.remove(walEvent.key)
      }
    }
    accumulator.result()
  }
}

class PersistentMapWalPlayer<K, V> @Throws(IOException::class) constructor(private val keyDescriptor: KeyDescriptor<K>,
                                                                           private val valueExternalizer: DataExternalizer<V>,
                                                                           file: Path) : Closeable {
  private val input: DataInputStream

  val version: Int = VERSION

  init {
    if (!Files.exists(file)) {
      throw FileNotFoundException(file.toString())
    }
    input = DataInputStream(Files.newInputStream(file).buffered())
    ensureVersionCompatible(version, input, file)
  }

  @Throws(IOException::class)
  private fun ensureVersionCompatible(expectedVersion: Int, input: DataInputStream, file: Path) {
    val actualVersion = DataInputOutputUtil.readINT(input)
    if (actualVersion != expectedVersion) {
      throw VersionUpdatedException(file, expectedVersion, actualVersion)
    }
  }

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
      WalOpCode.PUT -> WalEvent.PutEvent(keyDescriptor.read(input), readData(readByteArray(input), valueExternalizer))
      WalOpCode.REMOVE -> WalEvent.RemoveEvent(keyDescriptor.read(input))
      WalOpCode.APPEND -> WalEvent.AppendEvent(keyDescriptor.read(input), readByteArray(input))
    }
  }

  private fun readByteArray(inputStream: DataInputStream): ByteArray =
    ByteArray(inputStream.readInt()).also { inputStream.readFully(it) }

  private fun readNextOpCode(inputStream: DataInputStream): WalOpCode =
    WalOpCode.values()[inputStream.readByte().toInt()]
}