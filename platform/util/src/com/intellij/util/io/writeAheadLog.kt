// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.CompressionUtil
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.indexing.impl.IndexStorageUtil
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.io.*
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ExecutorService
import java.util.function.Function
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream
import java.util.zip.Checksum

private const val VERSION = 0

internal enum class WalOpCode(internal val code: Int) {
  PUT(0),
  REMOVE(1),
  APPEND(2);

  companion object {
    val size = values().size
  }
}

private val checksumGen = { CRC32() }

@Volatile
var debugWalRecords = false

private val log = logger<WalRecord>()

private class CorruptionException(val reason: String): IOException(reason)

private class EndOfLog: IOException()

internal class PersistentEnumeratorWal<Data> @Throws(IOException::class) @JvmOverloads constructor(dataDescriptor: KeyDescriptor<Data>,
                                                                                                   useCompression: Boolean,
                                                                                                   file: Path,
                                                                                                   walIoExecutor: ExecutorService,
                                                                                                   compact: Boolean = false) : Closeable {
  private val underlying = PersistentMapWal(dataDescriptor, integerExternalizer, useCompression, file, walIoExecutor, compact)

  fun enumerate(data: Data, id: Int) = underlying.put(data, id)

  fun flush() = underlying.flush()

  override fun close() = underlying.close()
}

internal class PersistentMapWal<K, V> @Throws(IOException::class) @JvmOverloads constructor(private val keyDescriptor: KeyDescriptor<K>,
                                                                                            private val valueExternalizer: DataExternalizer<V>,
                                                                                            private val useCompression: Boolean,
                                                                                            private val file: Path,
                                                                                            private val walIoExecutor: ExecutorService /*todo ensure sequential*/,
                                                                                            compact: Boolean = false) : Closeable {
  private val out: DataOutputStream

  val version: Int = VERSION

  init {
    if (compact) {
      tryCompact(file, keyDescriptor, valueExternalizer)?.let { compactedWal ->
        FileUtil.deleteWithRenaming(file)
        FileUtil.rename(compactedWal.toFile(), file.toFile())
      }
    }
    ensureCompatible(version, useCompression, file)
    out = DataOutputStream(Files.newOutputStream(file, StandardOpenOption.WRITE, StandardOpenOption.APPEND).buffered())
  }

  @Throws(IOException::class)
  private fun ensureCompatible(expectedVersion: Int, useCompression: Boolean, file: Path) {
    if (!Files.exists(file)) {
      Files.createDirectories(file.parent)
      DataOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)).use {
        DataInputOutputUtil.writeINT(it, expectedVersion)
        it.writeBoolean(useCompression)
      }
      return
    }
    val (actualVersion, actualUsesCompression) = DataInputStream(Files.newInputStream(file, StandardOpenOption.READ)).use {
      DataInputOutputUtil.readINT(it) to it.readBoolean()
    }
    if (actualVersion != expectedVersion) {
      throw VersionUpdatedException(file, expectedVersion, actualVersion)
    }
    if (actualUsesCompression != useCompression) {
      throw VersionUpdatedException(file, useCompression, actualUsesCompression)
    }
  }

  private fun ByteArray.write(outputStream: DataOutputStream) {
    if (useCompression) {
      CompressionUtil.writeCompressed(outputStream, this, 0, size)
    }
    else {
      outputStream.writeInt(size)
      outputStream.write(this)
    }
  }

  private fun AppendablePersistentMap.ValueDataAppender.writeToByteArray(): ByteArray {
    val baos = UnsyncByteArrayOutputStream()
    append(DataOutputStream(baos))
    return baos.toByteArray()
  }

  private fun appendRecord(key: K, appender: AppendablePersistentMap.ValueDataAppender) = WalRecord.writeRecord(WalOpCode.APPEND) {
    keyDescriptor.save(it, key)
    appender.writeToByteArray().write(it)
  }

  private fun putRecord(key: K, value: V) = WalRecord.writeRecord(WalOpCode.PUT) {
    keyDescriptor.save(it, key)
    writeData(value, valueExternalizer).write(it)
  }

  private fun removeRecord(key: K) = WalRecord.writeRecord(WalOpCode.REMOVE) {
    keyDescriptor.save(it, key)
  }

  private fun WalRecord.submitWrite() {
    walIoExecutor.submit {
      if (debugWalRecords) {
        println("write: $this")
      }
      this.write(out)
    }
  }

  @Throws(IOException::class)
  fun appendData(key: K, appender: AppendablePersistentMap.ValueDataAppender) {
    appendRecord(key, appender).submitWrite()
  }

  @Throws(IOException::class)
  fun put(key: K, value: V) {
    putRecord(key, value).submitWrite()
  }

  @Throws(IOException::class)
  fun remove(key: K) {
    removeRecord(key).submitWrite()
  }

  @Throws(IOException::class) // todo rethrow io exception
  fun flush() {
    walIoExecutor.submit {
      out.flush()
    }.get()
  }

  // todo rethrow io exception
  @Throws(IOException::class)
  override fun close() {
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

private val integerExternalizer: EnumeratorIntegerDescriptor
  get() = EnumeratorIntegerDescriptor.INSTANCE

sealed class WalEvent<K, V> {
  abstract val key: K

  data class PutEvent<K, V>(override val key: K, val value: V): WalEvent<K, V>()
  data class RemoveEvent<K, V>(override val key: K): WalEvent<K, V>()
  data class AppendEvent<K, V>(override val key: K, val data: ByteArray): WalEvent<K, V>() {
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

  object CorruptionEvent: WalEvent<Nothing, Nothing>() {
    override val key: Nothing
      get() = throw UnsupportedOperationException()
  }
}

@Throws(IOException::class)
fun <Data> restoreMemoryEnumeratorFromWal(walFile: Path,
                                          dataDescriptor: KeyDescriptor<Data>): List<Data> {
  return restoreFromWal(walFile, dataDescriptor, integerExternalizer, object : Accumulator<Data, Int, List<Data>> {
    val result = arrayListOf<Data>()

    override fun get(key: Data): Int = error("get not supported")

    override fun remove(key: Data) = error("remove not supported")

    override fun put(key: Data, value: Int) {
      assert(result.size == value)
      result.add(key)
    }

    override fun result(): List<Data> = result
  }).toList()
}

@Throws(IOException::class)
fun <Data> restorePersistentEnumeratorFromWal(walFile: Path,
                                              outputMapFile: Path,
                                              dataDescriptor: KeyDescriptor<Data>): PersistentEnumerator<Data> {
  if (Files.exists(outputMapFile)) {
    throw FileAlreadyExistsException(outputMapFile.toString())
  }
  return restoreFromWal(walFile, dataDescriptor, integerExternalizer, object : Accumulator<Data, Int, PersistentEnumerator<Data>> {
    val result = PersistentEnumerator(outputMapFile, dataDescriptor, 1024)

    override fun get(key: Data): Int = error("get not supported")

    override fun remove(key: Data) = error("remove not supported")

    override fun put(key: Data, value: Int) = assert(result.enumerate(key) == value)

    override fun result(): PersistentEnumerator<Data> = result
  })
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

private fun <K, V> tryCompact(walFile: Path,
                              keyDescriptor: KeyDescriptor<K>,
                              valueExternalizer: DataExternalizer<V>): Path? {
  if (!Files.exists(walFile)) {
    return null
  }

  val keyToLastEvent = IndexStorageUtil.createKeyDescriptorHashedMap<K, IntSet>(keyDescriptor)

  val shouldCompact = PersistentMapWalPlayer(keyDescriptor, valueExternalizer, walFile).use {
    var eventCount = 0

    for (walEvent in it.readWal()) {
      when (walEvent) {
        is WalEvent.AppendEvent -> keyToLastEvent.computeIfAbsent(walEvent.key, Function { IntLinkedOpenHashSet() }).add(eventCount)
        is WalEvent.PutEvent -> keyToLastEvent.put(walEvent.key, IntLinkedOpenHashSet().also{ set -> set.add(eventCount) })
        is WalEvent.RemoveEvent -> keyToLastEvent.put(walEvent.key, IntLinkedOpenHashSet())
        is WalEvent.CorruptionEvent -> throw CorruptionException("wal has been corrupted")
      }
      keyToLastEvent.computeIfAbsent(walEvent.key, Function { IntLinkedOpenHashSet() }).add(eventCount)
      eventCount++
    }

    keyToLastEvent.size * 2 < eventCount
  }

  if (!shouldCompact) return null

  val compactedWalFile = walFile.resolveSibling("${walFile.fileName}_compacted")
  PersistentMapWalPlayer(keyDescriptor, valueExternalizer, walFile).use { walPlayer ->
    PersistentMapWal(keyDescriptor, valueExternalizer, walPlayer.useCompression, compactedWalFile, ConcurrencyUtil.newSameThreadExecutorService()).use { compactedWal ->
      walPlayer.readWal().forEachIndexed{index, walEvent ->
        val key = walEvent.key
        val events = keyToLastEvent.get(key) ?: throw IOException("No events found for key =  $key")
        if (events.contains(index)) {
          when (walEvent) {
            is WalEvent.AppendEvent -> compactedWal.appendData(key, AppendablePersistentMap.ValueDataAppender { out -> out.write(walEvent.data) })
            is WalEvent.PutEvent -> compactedWal.put(key, walEvent.value)
            is WalEvent.RemoveEvent -> {/*do nothing*/}
            is WalEvent.CorruptionEvent -> throw CorruptionException("wal has been corrupted")
          }
        }
      }
    }
  }
  return compactedWalFile
}

private class ChecksumOutputStream(delegate: OutputStream, checksumGen: () -> Checksum): CheckedOutputStream(delegate, checksumGen()) {
  fun checksum() = checksum.value
}

private class ChecksumInputStream(delegate: InputStream, checksumGen: () -> Checksum): CheckedInputStream(delegate, checksumGen()) {
  fun checksum() = checksum.value
}

/**
 * +-------------+---------------+---------------------+---------+
 * | OpCode (1b) | Checksum (8b) | Payload Length (4b) | Payload |
 * +-------------+---------------+---------------------+---------+
 *
 * TODO it makes sense to add header for each record
 */
private class WalRecord(val opCode: WalOpCode,
                        private val checksum: Long,
                        val payload: ByteArraySequence) {
  override fun toString(): String {
    return "record($opCode, checksum = $checksum, len = ${payload.length()}, payload = ${StringUtil.toHexString(payload.toBytes())})"
  }

  @Throws(IOException::class)
  fun write(target: DataOutputStream) {
    target.writeByte(opCode.code)
    DataInputOutputUtil.writeLONG(target, checksum)
    DataInputOutputUtil.writeINT(target, payload.length)
    target.write(payload.internalBuffer, payload.offset, payload.length)
  }

  companion object {
    @Throws(IOException::class)
    fun writeRecord(opCode: WalOpCode, writer: (DataOutputStream) -> Unit): WalRecord {
      val baos = UnsyncByteArrayOutputStream()
      val cos = ChecksumOutputStream(baos, checksumGen)
      DataOutputStream(cos).use { writer(it) }
      return WalRecord(opCode, cos.checksum(), baos.toByteArraySequence())
    }

    @Throws(IOException::class)
    fun read(input: DataInputStream): WalRecord {
      val code: Int
      try {
        code = input.readByte().toInt()
      }
      catch (e: EOFException) {
        throw EndOfLog()
      }
      if (code >= WalOpCode.size) {
        throw CorruptionException("no opcode present for code $code")
      }
      val checksum = DataInputOutputUtil.readLONG(input)
      val payloadLength = DataInputOutputUtil.readINT(input)
      val cis = ChecksumInputStream(input, checksumGen)
      val data = cis.readNBytes(payloadLength)
      val actualChecksum = cis.checksum()
      if (actualChecksum != checksum) {
        throw CorruptionException("checksum is wrong for log record: expected = $checksum but actual = $actualChecksum")
      }
      return WalRecord(WalOpCode.values()[code], checksum, ByteArraySequence(data))
    }
  }
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
        is WalEvent.CorruptionEvent -> throw CorruptionException("wal has been corrupted")
      }
    }
    accumulator.result()
  }
}

class PersistentMapWalPlayer<K, V> @Throws(IOException::class) constructor(private val keyDescriptor: KeyDescriptor<K>,
                                                                           private val valueExternalizer: DataExternalizer<V>,
                                                                           file: Path) : Closeable {
  private val input: DataInputStream
  internal val useCompression: Boolean

  val version: Int = VERSION

  init {
    if (!Files.exists(file)) {
      throw FileNotFoundException(file.toString())
    }
    input = DataInputStream(Files.newInputStream(file).buffered())
    ensureCompatible(version, input, file)
    useCompression = input.readBoolean()
  }

  @Throws(IOException::class)
  private fun ensureCompatible(expectedVersion: Int, input: DataInputStream, file: Path) {
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
    val record: WalRecord
    try {
      record = WalRecord.read(input)
    }
    catch (e: EndOfLog) {
      return null
    }
    catch (e: IOException) {
      return WalEvent.CorruptionEvent as WalEvent<K, V>
    }

    if (debugWalRecords) {
      println("read: $record")
    }

    val recordStream = record.payload.toInputStream()
    return when (record.opCode) {
      WalOpCode.PUT -> WalEvent.PutEvent(keyDescriptor.read(recordStream), readData(readByteArray(recordStream), valueExternalizer))
      WalOpCode.REMOVE -> WalEvent.RemoveEvent(keyDescriptor.read(recordStream))
      WalOpCode.APPEND -> WalEvent.AppendEvent(keyDescriptor.read(recordStream), readByteArray(recordStream))
    }
  }

  private fun readByteArray(inputStream: DataInputStream): ByteArray {
    if (useCompression) {
      return CompressionUtil.readCompressed(inputStream)
    }
    else {
      return ByteArray(inputStream.readInt()).also { inputStream.readFully(it) }
    }
  }
}