// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl

import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogOverMMappedFile
import com.intellij.platform.util.io.storages.database.impl.layout.BlockHeaderLayout
import com.intellij.platform.util.io.storages.database.impl.layout.CatalogChangeHeaderLayout
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkChangePayloadLayout
import com.intellij.platform.util.io.storages.database.impl.layout.ChunkHeaderLayout
import com.intellij.platform.util.io.storages.database.impl.layout.DatabaseHeaderLayout
import com.intellij.platform.util.io.storages.database.impl.layout.StoreCreatePayloadLayout
import com.intellij.platform.util.io.storages.database.impl.layout.StoreDropPayloadLayout
import com.intellij.platform.util.io.storages.database.impl.layout.StoreMetadataPayloadLayout
import java.io.IOException
import java.io.PrintStream
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import org.jetbrains.annotations.ApiStatus

/**
 * Prints the database structure without opening or changing the database.
 *
 * Usage: `database_dump <database-directory> [--brief|--verbose]`
 */
@ApiStatus.Internal
object DatabaseDumper {
  private val INT32_LAYOUT = ValueLayout.JAVA_INT.withOrder(ByteOrder.nativeOrder())
  private val INT64_LAYOUT = ValueLayout.JAVA_LONG.withOrder(ByteOrder.nativeOrder())
  private const val RECORD_LENGTH_MASK = 0x3FFF_FFFF
  private const val RECORD_COMMITTED_FLAG = 1 shl 30
  private const val RECORD_PADDING_FLAG = 1 shl 31

  @JvmStatic
  fun main(args: Array<String>) {
    require(args.size in 1..2 && (args.size == 1 || args[1] == "--brief" || args[1] == "--verbose")) {
      "Usage: database_dump <database-directory> [--brief|--verbose]"
    }
    dump(Path.of(args[0]), args.getOrNull(1) == "--verbose", System.out)
  }

  fun dump(directory: Path, verbose: Boolean, out: PrintStream) {
    require(Files.isDirectory(directory)) { "Not a database directory: $directory" }
    val header = dumpMetadata(directory.resolve("database.meta"), out)
    Files.newDirectoryStream(directory, "chunk-*.dat").use { paths ->
      paths.filter { it.fileName.toString().matches(Regex("chunk-[0-9]{8,}\\.dat")) }
        .sortedBy { it.fileName.toString() }
        .forEach { dumpChunk(it, header, verbose, out) }
    }
  }

  private fun dumpMetadata(path: Path, out: PrintStream): DatabaseHeaderLayout.DatabaseHeader {
    FileChannel.open(path, StandardOpenOption.READ).use { channel ->
      Arena.ofConfined().use { arena ->
        val file = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena)
        val headerSize = AppendOnlyLogOverMMappedFile.HeaderLayout.HEADER_SIZE.toLong()
        checkRange(path, file, 0, headerSize)
        check(file.get(INT32_LAYOUT, 0) == DatabaseHeaderLayout.MAGIC) { "$path: invalid metadata magic" }
        check(file.get(INT32_LAYOUT, 4) == AppendOnlyLogOverMMappedFile.CURRENT_IMPLEMENTATION_VERSION) {
          "$path: unsupported metadata log version"
        }
        check(file.get(INT32_LAYOUT, 8) == DatabaseHeaderLayout.FORMAT_MAJOR.toInt()) { "$path: unsupported database version" }
        val pageSize = file.get(INT32_LAYOUT, 12)
        val allocatedTail = file.get(INT64_LAYOUT, 16)
        val committedTail = file.get(INT64_LAYOUT, 24)
        check(pageSize > headerSize && pageSize.countOneBits() == 1) { "$path: invalid metadata page size" }
        check(committedTail in headerSize..allocatedTail && allocatedTail <= file.byteSize()) { "$path: invalid metadata tails" }

        out.println("database.meta[${channel.size()} b] {")
        out.println("\t[log header]: version=${file.get(INT32_LAYOUT, 4)}, pageSize=$pageSize, " +
                    "committedTail=$committedTail, allocatedTail=$allocatedTail, records=${file.get(INT32_LAYOUT, 32)}")
        var offset = headerSize
        var databaseHeader: DatabaseHeaderLayout.DatabaseHeader? = null
        while (offset < committedTail) {
          checkRange(path, file, offset, Int.SIZE_BYTES.toLong())
          val recordHeader = file.get(INT32_LAYOUT, offset)
          val length = recordHeader and RECORD_LENGTH_MASK
          val paddedLength = (length.toLong() + 3) and -4L
          check(length >= Int.SIZE_BYTES && offset + paddedLength <= committedTail) { "$path: invalid record at $offset" }
          check(offset % pageSize + paddedLength <= pageSize) { "$path: record crosses a page at $offset" }
          check(recordHeader and RECORD_COMMITTED_FLAG != 0) { "$path: uncommitted record at $offset" }
          if (recordHeader and RECORD_PADDING_FLAG == 0) {
            val payload = file.asSlice(offset + Int.SIZE_BYTES, length - Int.SIZE_BYTES.toLong()).asByteBuffer()
            if (databaseHeader == null) {
              databaseHeader = DatabaseHeaderLayout.DatabaseHeader.read(path, payload)
              out.println("\t${formatOffset(offset)} DBHeader[${payload.remaining()} b] { " +
                          "databaseId=${databaseHeader.databaseId().toHexString()}, chunkSize=${databaseHeader.chunkSize()} }")
            }
            else {
              dumpChange(path, offset, payload, out)
            }
          }
          offset += paddedLength
        }
        out.println("}")
        return databaseHeader ?: throw IOException("$path: missing database header")
      }
    }
  }

  private fun dumpChange(path: Path, offset: Long, record: java.nio.ByteBuffer, out: PrintStream) {
    check(record.remaining() >= CatalogChangeHeaderLayout.HEADER_SIZE) { "$path: truncated record at $offset" }
    val header = CatalogChangeHeaderLayout.CatalogChangeRecordHeader.read(record)
    val payload = record.slice(CatalogChangeHeaderLayout.HEADER_SIZE, record.remaining() - CatalogChangeHeaderLayout.HEADER_SIZE)
    val fields = when (header.type()) {
      CatalogChangeHeaderLayout.ChangeType.CHUNK_CREATE,
      CatalogChangeHeaderLayout.ChangeType.CHUNK_SEAL,
      CatalogChangeHeaderLayout.ChangeType.CHUNK_RETIRE -> {
        check(header.version() == ChunkChangePayloadLayout.PAYLOAD_FORMAT_VERSION &&
              payload.remaining() == ChunkChangePayloadLayout.PAYLOAD_SIZE) { "$path: invalid chunk record at $offset" }
        "chunkId=${ChunkChangePayloadLayout.ChunkChangeRecordPayload.read(payload).chunkId()}"
      }
      CatalogChangeHeaderLayout.ChangeType.STORE_CREATE -> {
        check(header.version() == StoreCreatePayloadLayout.PAYLOAD_FORMAT_VERSION) { "$path: unsupported store record at $offset" }
        val store = StoreCreatePayloadLayout.StoreCreateRecordPayload.read(payload)
        "storeId=${store.storeId()}, name='${store.name()}', dataVersion=${store.dataVersion()}, " +
        "metadataVersion=${store.storeMetadata().version()}, customStorePayload=<${store.storeMetadata().size()} b>"
      }
      CatalogChangeHeaderLayout.ChangeType.STORE_DROP -> {
        check(header.version() == StoreDropPayloadLayout.PAYLOAD_FORMAT_VERSION &&
              payload.remaining() == StoreDropPayloadLayout.PAYLOAD_SIZE) { "$path: invalid store drop at $offset" }
        "storeId=${StoreDropPayloadLayout.StoreDropRecordPayload.read(payload).storeId()}"
      }
      CatalogChangeHeaderLayout.ChangeType.STORE_METADATA_UPDATE -> {
        check(header.version() == StoreMetadataPayloadLayout.PAYLOAD_FORMAT_VERSION) {
          "$path: unsupported store metadata at $offset"
        }
        val update = StoreMetadataPayloadLayout.StoreMetadataRecordPayload.read(payload)
        "storeId=${update.storeId()}, metadataVersion=${update.storeMetadata().version()}, " +
        "customStorePayload=<${update.storeMetadata().size()} b>"
      }
    }
    out.println("\t${formatOffset(offset)} ${header.type()}[${record.remaining()} b] { version=${header.version()}, $fields }")
  }

  private fun dumpChunk(path: Path, database: DatabaseHeaderLayout.DatabaseHeader, verbose: Boolean, out: PrintStream) {
    FileChannel.open(path, StandardOpenOption.READ).use { channel ->
      Arena.ofConfined().use { arena ->
        check(channel.size() == database.chunkSize().toLong()) { "$path: unexpected chunk size ${channel.size()}" }
        val fileSegment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena)
        ChunkHeaderLayout.validate(path, fileSegment, database.chunkSize())
        val id = ChunkHeaderLayout.readChunkId(fileSegment)
        check(ChunkHeaderLayout.readDatabaseId(fileSegment) == database.databaseId()) { "$path: foreign database ID" }
        check(path.fileName.toString() == String.format(Locale.ROOT, "chunk-%08d.dat", id)) {
          "$path: chunk ID differs from file name"
        }
        val committedTail = ChunkHeaderLayout.readCommittedTail(fileSegment)
        val counts = IntArray(4)
        var offset = ChunkHeaderLayout.HEADER_SIZE.toLong()
        while (offset < committedTail) {
          checkRange(path, fileSegment, offset, BlockHeaderLayout.HEADER_SIZE.toLong())
          val block = fileSegment.asSlice(offset, BlockHeaderLayout.HEADER_SIZE.toLong())
          BlockHeaderLayout.validate(path, block, offset)
          val length = BlockHeaderLayout.readBlockLength(block)
          check(length >= BlockHeaderLayout.HEADER_SIZE && length % BlockHeaderLayout.BLOCK_ALIGNMENT == 0 &&
                offset + length <= committedTail) { "$path: invalid block length at $offset" }
          val state = BlockHeaderLayout.readState(block)
          counts[state.ordinal]++
          offset += length
        }
        out.println("${path.fileName}[${channel.size()} b] {")
        out.println("\theader: {chunkId=$id, state=${ChunkHeaderLayout.readState(fileSegment)}, databaseId=${database.databaseId().toHexString()}, " +
                    "committedTail=$committedTail, allocatedTail=${ChunkHeaderLayout.readAllocatedTail(fileSegment)}}")
        out.println("\tblocks: ${counts.sum()} (allocated: ${counts[0]}, active: ${counts[1]}, " +
                    "sealed: ${counts[2]}, retired: ${counts[3]})")
        if (verbose) {
          offset = ChunkHeaderLayout.HEADER_SIZE.toLong()
          while (offset < committedTail) {
            val block = fileSegment.asSlice(offset, BlockHeaderLayout.HEADER_SIZE.toLong())
            val length = BlockHeaderLayout.readBlockLength(block)
            out.println("\t\t${formatOffset(offset)} block[$length b] { blockId=${BlockHeaderLayout.readBlockId(block)}, " +
                        "storeId=${BlockHeaderLayout.readStoreId(block)}, role=${BlockHeaderLayout.readRole(block)}, " +
                        "state=${BlockHeaderLayout.readState(block)} }")
            offset += length
          }
        }
        out.println("}")
      }
    }
  }

  private fun checkRange(path: Path, file: MemorySegment, offset: Long, length: Long) {
    check(offset >= 0 && length >= 0 && offset <= file.byteSize() - length) { "$path: truncated data at $offset" }
  }

  private fun formatOffset(offset: Long): String = String.format(Locale.ROOT, "[0x%08X]", offset)
}
