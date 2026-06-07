// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.writeaheadlog

import com.intellij.util.io.PageCacheUtils
import com.intellij.util.io.blobstorage.ByteBufferWriter
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

class FileChannelWithWALTest {
  @Test
  fun `write stores bytes in write ahead log and does not change file`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()
    Files.write(file, byteArrayOf(1, 2, 3, 4))
    val writeAheadLog = RecordingWriteAheadLog()

    openWritableChannel(file, writeAheadLog).use { channel ->
      val bytes = byteArrayOf(5, 6, 7)
      val source = ByteBuffer.wrap(bytes)

      assertEquals(bytes.size, channel.write(source, 4))

      assertEquals(bytes.size, source.position())
      assertEquals(7, channel.size())
    }

    assertArrayEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(file))
    assertEquals(listOf(Write(file, 4, byteArrayOf(5, 6, 7))), writeAheadLog.writes)
  }

  @Test
  fun `relative writes use and update channel position`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()
    val writeAheadLog = RecordingWriteAheadLog()

    openWritableChannel(file, writeAheadLog).use { channel ->
      channel.position(3)

      channel.write(ByteBuffer.wrap(byteArrayOf(1, 2)))
      channel.write(ByteBuffer.wrap(byteArrayOf(3)))

      assertEquals(6, channel.position())
      assertEquals(6, channel.size())
    }

    assertEquals(
      listOf(
        Write(file, 3, byteArrayOf(1, 2)),
        Write(file, 5, byteArrayOf(3)),
      ),
      writeAheadLog.writes,
    )
  }

  @Test
  fun `force flushes write ahead log`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()
    val writeAheadLog = RecordingWriteAheadLog()

    openWritableChannel(file, writeAheadLog).use { channel ->
      assertFalse(writeAheadLog.flushed)

      channel.force(true)

      assertTrue(writeAheadLog.flushed)
    }
  }

  @Test
  fun `foreground flush statistics count flushed records by reason`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()
    val before = FileChannelWithWAL.getFlushStatistics()
    val writeAheadLog = CountingFlushWriteAheadLog(2, 3, 5, 7)

    openWritableChannel(file, writeAheadLog).use { channel ->
      channel.force(true)
      channel.read(ByteBuffer.allocate(1), 0)
      channel.truncate(0)
    }

    val after = FileChannelWithWAL.getFlushStatistics()
    assertEquals(2, after.entriesFlushedOnForce - before.entriesFlushedOnForce)
    assertEquals(3, after.entriesFlushedOnRead - before.entriesFlushedOnRead)
    assertEquals(5, after.entriesFlushedOnTruncate - before.entriesFlushedOnTruncate)
    assertEquals(7, after.entriesFlushedOnClose - before.entriesFlushedOnClose)
  }

  @Test
  fun `writable channel initializes missing file size lazily`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("missing-storage.bin")
    val writeAheadLog = RecordingWriteAheadLog()

    assertFalse(Files.exists(file), "Precondition: file should not exist before channel opening")

    openWritableChannel(file, writeAheadLog).use { channel ->
      assertFalse(Files.exists(file), "Writable WAL channel must not create a missing file on opening")

      assertEquals(2, channel.write(ByteBuffer.wrap(byteArrayOf(1, 2)), 0))
      assertFalse(Files.exists(file), "WAL write must not create a missing file before underlying channel is needed")

      assertEquals(2, channel.size())
      assertTrue(Files.exists(file), "size() initializes underlying channel lazily")
    }
  }

  @Test
  fun `writable channel can create missing file on opening`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("missing-storage.bin")
    val writeAheadLog = RecordingWriteAheadLog()

    assertFalse(Files.exists(file), "Precondition: file should not exist before channel opening")

    FileChannelWithWAL(
      file,
      writeAheadLog,
      PageCacheUtils.getCachedChannelsAccessor(false),
      readOnly = false,
      createFileImmediately = true,
    ).use { channel ->
      assertTrue(Files.exists(file), "Writable WAL channel must create a missing file on opening if requested")
      assertEquals(0, channel.size())
    }
  }

  @Test
  fun `channel rejects underlying accessor with different mode`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()
    val writeAheadLog = RecordingWriteAheadLog()

    val error = assertThrows<IllegalArgumentException> {
      FileChannelWithWAL(file, writeAheadLog, PageCacheUtils.getCachedChannelsAccessor(true), readOnly = false)
    }

    assertTrue(error.message!!.contains("must match FileChannelWithWAL mode"))
  }

  @Test
  fun `channel rejects eager file creation in read only mode`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("missing-storage.bin")
    val writeAheadLog = RecordingWriteAheadLog()

    val error = assertThrows<IllegalArgumentException> {
      FileChannelWithWAL(
        file,
        writeAheadLog,
        PageCacheUtils.getCachedChannelsAccessor(true),
        readOnly = true,
        createFileImmediately = true,
      )
    }

    assertTrue(error.message!!.contains("createFileImmediately"))
  }

  @Test
  fun `read returns data with pending write applied even if the write is really pending`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()
    Files.write(file, byteArrayOf(1, 2, 3, 4))
    val writeAheadLog = fileBackedWriteAheadLog()

    FileChannelWithWAL(file, writeAheadLog, PageCacheUtils.getCachedChannelsAccessor(false), readOnly = false).use { writeableChannel ->
      writeableChannel.write(ByteBuffer.wrap(byteArrayOf(9, 8)), 1)

      assertArrayEquals(byteArrayOf(1, 2, 3, 4), Files.readAllBytes(file), "Write is pending: file content is unchanged")

      FileChannelWithWAL(file, writeAheadLog, PageCacheUtils.getCachedChannelsAccessor(true), readOnly = true).use { readOnlyChannel ->
        val target = ByteBuffer.allocate(4)

        assertEquals(4, readOnlyChannel.read(target, 0))

        assertArrayEquals(byteArrayOf(1, 9, 8, 4), target.array())
        assertArrayEquals(
          byteArrayOf(1, 9, 8, 4),
          Files.readAllBytes(file),
          "Default read path flushes pending writes"
        )
      }
    }
  }

  @Test
  fun `read can apply pending write without flushing if overlay read is enabled`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()
    val initialContent = byteArrayOf(1, 2, 3, 4)
    Files.write(file, initialContent)
    val writeAheadLog = fileBackedWriteAheadLog()

    FileChannelWithWAL(
      file,
      writeAheadLog,
      PageCacheUtils.getCachedChannelsAccessor(false),
      readOnly = false,
      applyUnfinishedOnRead = true
    ).use { writeableChannel ->
      writeableChannel.write(ByteBuffer.wrap(byteArrayOf(9, 8)), 1)

      assertArrayEquals(initialContent, Files.readAllBytes(file), "Write is pending: file content is unchanged")

      FileChannelWithWAL(
        file,
        writeAheadLog,
        PageCacheUtils.getCachedChannelsAccessor(true),
        readOnly = true,
        applyUnfinishedOnRead = true
      ).use { readOnlyChannel ->
        val target = ByteBuffer.allocate(4)

        assertEquals(4, readOnlyChannel.read(target, 0))

        assertArrayEquals(byteArrayOf(1, 9, 8, 4), target.array())
        assertTrue(writeAheadLog.hasUnfinished())
        assertArrayEquals(initialContent, Files.readAllBytes(file), "Overlay read must not flush pending writes")
      }
    }
  }

  @Test
  fun `new channel sees size extended by pending write from existing channel`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()
    val initialContent = byteArrayOf(1, 2, 3, 4)
    Files.write(file, initialContent)
    val writeAheadLog = fileBackedWriteAheadLog()

    FileChannelWithWAL(
      file,
      writeAheadLog,
      PageCacheUtils.getCachedChannelsAccessor(false),
      readOnly = false,
      applyUnfinishedOnRead = true,
    ).use { writeableChannel ->
      writeableChannel.write(ByteBuffer.wrap(byteArrayOf(5, 6)), initialContent.size.toLong())

      assertEquals(6, writeableChannel.size())
      assertEquals(initialContent.size.toLong(), Files.size(file), "Write is pending: file size is unchanged")

      FileChannelWithWAL(
        file,
        writeAheadLog,
        PageCacheUtils.getCachedChannelsAccessor(true),
        readOnly = true,
        applyUnfinishedOnRead = true,
      ).use { readOnlyChannel ->
        assertEquals(6, readOnlyChannel.size())

        val target = ByteBuffer.allocate(6)
        assertEquals(6, readOnlyChannel.read(target, 0))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), target.array())
      }
    }
  }

  @RepeatedTest(8)
  fun `overlay read is equivalent to flush read after random writes`(@TempDir tempDir: Path, repetitionInfo: RepetitionInfo) {
    val seed = repetitionInfo.currentRepetition
    val random = Random(seed)
    val initialContent = random.nextBytes(random.nextInt(1, 33))
    val writes = generateRandomWrites(random, initialContent.size)

    val flushReadFile = tempDir.resolve("flush-read.bin")
    val overlayReadFile = tempDir.resolve("overlay-read.bin")
    Files.write(flushReadFile, initialContent)
    Files.write(overlayReadFile, initialContent)

    FileChannelWithWAL(
      flushReadFile,
      fileBackedWriteAheadLog(),
      PageCacheUtils.getCachedChannelsAccessor(false),
      readOnly = false,
      applyUnfinishedOnRead = false,
    ).use { flushReadChannel ->
      FileChannelWithWAL(
        overlayReadFile,
        fileBackedWriteAheadLog(),
        PageCacheUtils.getCachedChannelsAccessor(false),
        readOnly = false,
        applyUnfinishedOnRead = true,
      ).use { overlayReadChannel ->
        writes.forEachIndexed { writeIndex, write ->
          assertEquals(write.data.size, flushReadChannel.write(ByteBuffer.wrap(write.data), write.offsetInFile))
          assertEquals(write.data.size, overlayReadChannel.write(ByteBuffer.wrap(write.data), write.offsetInFile))

          assertArrayEquals(
            readChannelContent(flushReadChannel),
            readChannelContent(overlayReadChannel),
            "seed=$seed, writeIndex=$writeIndex, offset=${write.offsetInFile}, size=${write.data.size}"
          )
        }
      }
    }
  }

  @Test
  fun `failed write still keeps channel position unchanged`(@TempDir tempDir: Path) {
    val file = tempDir.storageFile()

    val source = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
    openWritableChannel(file, FailingWriteAheadLog()).use { channel ->
      assertThrows<IOException> {
        channel.write(source, 0)
      }

      assertEquals(0, channel.position(), "Absolute-positioned write must not change channel.position regardless of success/failure")
    }
  }

  private fun Path.storageFile(): Path = resolve("storage.bin").also { Files.createFile(it) }

  private fun openWritableChannel(file: Path, writeAheadLog: WriteAheadLog): FileChannelWithWAL {
    return FileChannelWithWAL(file, writeAheadLog, PageCacheUtils.getCachedChannelsAccessor(false), readOnly = false)
  }

  private fun readChannelContent(channel: FileChannelWithWAL): ByteArray {
    val content = ByteArray(channel.size().toInt())
    val target = ByteBuffer.wrap(content)
    var offset = 0L
    while (target.hasRemaining()) {
      val bytesRead = channel.read(target, offset)
      if (bytesRead <= 0) {
        break
      }
      offset += bytesRead
    }
    return content
  }

  private fun generateRandomWrites(random: Random, initialSize: Int): List<WriteOperation> {
    var logicalSize = initialSize
    return List(64) { index ->
      val data = random.nextBytes(random.nextInt(1, 17))
      val offset = if (index % 4 == 0) {
        logicalSize + random.nextInt(1, 33)
      }
      else {
        random.nextInt(0, logicalSize + 33)
      }
      logicalSize = maxOf(logicalSize, offset + data.size)
      WriteOperation(offset.toLong(), data)
    }
  }

  private fun fileBackedWriteAheadLog(): WriteAheadLog {
    val writeableAccessor = PageCacheUtils.getCachedChannelsAccessor(/*readOnly = */false)
    return ByteArrayQueueWriteAheadLog { path, offsetInFile, data ->
      writeableAccessor.executeOp(path) { channel ->
        var offset = offsetInFile
        while (data.hasRemaining()) {
          offset += channel.write(data, offset)
        }
      }
    }
  }

  private data class Write(val file: Path, val offset: Long, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Write

      return file == other.file && offset == other.offset && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
      var result = file.hashCode()
      result = 31 * result + offset.hashCode()
      result = 31 * result + data.contentHashCode()
      return result
    }
  }

  private class WriteOperation(val offsetInFile: Long, val data: ByteArray)

  private class RecordingWriteAheadLog : WriteAheadLog {
    val writes: MutableList<Write> = mutableListOf()
    var flushed: Boolean = false

    override fun openFor(file: Path): WriteAheadLog.PerFileWriter {
      return object : WriteAheadLog.PerFileWriter {
        override fun write(fileOffset: Long, writer: ByteBufferWriter, recordSize: Int) {
          val record = ByteBuffer.allocate(recordSize)
          val written = writer.write(record)
          written.flip()

          val data = ByteArray(written.remaining())
          written.get(data)
          writes += Write(file, fileOffset, data)
        }

        override fun hasUnfinished(): Boolean = writes.isNotEmpty()

        override fun maxUnfinishedWriteOffset(): Long {
          return writes
            .asSequence()
            .filter { it.file == file }
            .maxOfOrNull { it.offset + it.data.size } ?: -1
        }

        override fun applyUnfinished(offsetInFile: Long, length: Int, targetBuffer: ByteBuffer, offsetInBuffer: Int) =
          throw UnsupportedOperationException("Intentionally not implemented")

        override fun flush(): Int {
          flushed = true
          return 0
        }
        //override fun flush(): Int = this@RecordingWriteAheadLog.flush()
      }
    }

    override fun flush(): Int {
      flushed = true
      return 0
    }

    override fun close() = Unit

    override fun hasUnfinished() = false
  }

  private class FailingWriteAheadLog : WriteAheadLog {
    override fun openFor(file: Path): WriteAheadLog.PerFileWriter {
      return object : WriteAheadLog.PerFileWriter {
        override fun write(fileOffset: Long, writer: ByteBufferWriter, recordSize: Int) = throw IOException("Expected")

        override fun hasUnfinished(): Boolean = false

        override fun maxUnfinishedWriteOffset(): Long = -1

        override fun applyUnfinished(offsetInFile: Long, length: Int, targetBuffer: ByteBuffer, offsetInBuffer: Int) =
          throw UnsupportedOperationException("Intentionally not implemented")

        override fun flush(): Int = this@FailingWriteAheadLog.flush()
      }
    }

    override fun flush(): Int = 0

    override fun close() = Unit

    override fun hasUnfinished(): Boolean = false
  }

  private class CountingFlushWriteAheadLog(vararg flushedRecords: Int) : WriteAheadLog {
    private val flushedRecords = java.util.ArrayDeque(flushedRecords.toList())

    override fun openFor(file: Path): WriteAheadLog.PerFileWriter {
      return object : WriteAheadLog.PerFileWriter {
        override fun write(fileOffset: Long, writer: ByteBufferWriter, recordSize: Int) = Unit

        override fun hasUnfinished(): Boolean = this@CountingFlushWriteAheadLog.hasUnfinished()

        override fun maxUnfinishedWriteOffset(): Long = -1

        override fun applyUnfinished(fileOffset: Long, length: Int, buffer: ByteBuffer, offsetInBuffer: Int) = Unit

        override fun flush(): Int = this@CountingFlushWriteAheadLog.flush()
      }
    }

    override fun flush(): Int = if (flushedRecords.isEmpty()) 0 else flushedRecords.removeFirst()

    override fun close() = Unit

    override fun hasUnfinished(): Boolean = flushedRecords.isNotEmpty()
  }
}
