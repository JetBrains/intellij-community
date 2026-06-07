// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.writeaheadlog

import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory
import com.intellij.platform.util.io.storages.circular.CircularBytesBuffer
import com.intellij.platform.util.io.storages.circular.CircularBytesBufferOverMMappedFile
import com.intellij.platform.util.io.storages.circular.WriteAheadLogOverCircularBuffer
import com.intellij.platform.util.io.storages.circular.WriteAheadLogOverCircularBuffer.WriteAheadLogStatistics
import com.intellij.platform.util.io.storages.enumerator.DurableEnumerator
import com.intellij.platform.util.io.storages.enumerator.DurableEnumeratorFactory
import com.intellij.util.io.ChannelsAccessor
import com.intellij.util.io.CorruptedException
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.DurableDataEnumerator
import com.intellij.util.io.FileChannelInterruptsRetryer
import com.intellij.util.io.IOUtil.MiB
import com.intellij.util.io.blobstorage.ByteBufferWriter
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.channels.Channels.newInputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.open
import java.nio.file.Files
import java.nio.file.Files.readAllBytes
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.random.Random


private const val TURNS_COUNT = 8
private const val WRITES_PER_TURN = 4096
private const val INITIAL_FILE_SIZE = 256
private const val MAX_WRITE_OFFSET = 1 shl 20
private const val MAX_WRITE_SIZE = 1 shl 14
private const val WAL_CAPACITY = 64 * 1024

class WriteAheadLogFileChannelOverCircularBufferTest {
  @Test
  fun `write fails immediately if WAL record is larger than circular buffer capacity`(@TempDir tempDir: Path) {
    val writeAheadLog = WriteAheadLogOverCircularBuffer(
      CapacityOnlyCircularBytesBuffer(maxEntrySize = Int.SIZE_BYTES + Long.SIZE_BYTES),
      singlePathEnumerator(),
      { _, _, _ -> },
    )

    val fileWriter = writeAheadLog.openFor(tempDir.resolve("storage.bin"))

    assertThrows(IllegalArgumentException::class.java) {
      fileWriter.write(0, byteArrayOf(1), 0, 1)
    }
  }

  @Test
  fun `statistics counts queued bytes and queue-full flushes`(@TempDir tempDir: Path) {
    WriteAheadLogOverCircularBuffer(
      QueueFullOnceCircularBytesBuffer(), singlePathEnumerator(), { _, _, _ -> },
    ).use { writeAheadLog ->
      val fileWriter = writeAheadLog.openFor(tempDir.resolve("storage.bin"))

      fileWriter.write(0, byteArrayOf(1, 2, 3), 0, 3)

      val expectedStatistics = WriteAheadLogStatistics(
        bytesQueued = 3,
        flushesForcedByOverflow = 1,
        entriesFlushed = 0,
        bytesCopiedByApplyUnfinished = 0,
      )
      assertEquals(expectedStatistics, writeAheadLog.statistics)

      val allStatistics = WriteAheadLogOverCircularBuffer.getAggregatedStatistics()
      assertTrue(allStatistics.bytesQueued >= expectedStatistics.bytesQueued)
      assertTrue(allStatistics.flushesForcedByOverflow >= expectedStatistics.flushesForcedByOverflow)
      assertTrue(allStatistics.entriesFlushed >= expectedStatistics.entriesFlushed)
      assertTrue(allStatistics.bytesCopiedByApplyUnfinished >= expectedStatistics.bytesCopiedByApplyUnfinished)
    }
  }

  @RepeatedTest(TURNS_COUNT)
  fun `writes to WAL-backed Channel produces the same file content as writes to FileChannel`(
    @TempDir tempDir: Path,
    repetitionInfo: RepetitionInfo,
  ) {
    val turnNo = repetitionInfo.currentRepetition
    val caseDir = tempDir.resolve("seed-$turnNo")
    Files.createDirectories(caseDir)
    val testCase = generateTestCase(turnNo)

    val directFile = caseDir.resolve("direct.bin")
    val walBackedFile = caseDir.resolve("wal-backed.bin")
    testCase.applyInitialState(directFile)
    testCase.applyInitialState(walBackedFile)
    assertArrayEquals(
      testCase.initialContent,
      readAllBytes(walBackedFile),
      "[$turnNo]: WAL-backed file content must == initialContent before writes applied"
    )

    open(directFile, READ, WRITE, CREATE).use { directChannel ->
      testCase.applyWritesTo(directChannel)
    }
    val directlyWrittenFileContent = readAllBytes(directFile)


    open(walBackedFile, READ, WRITE, CREATE).use { walBackedFileChannel ->
      val channelsAccessor = channelsAccessor(walBackedFile, walBackedFileChannel)


      openPersistentWriteAheadLog(caseDir, channelsAccessor).use { writeAheadLog ->
        FileChannelWithWAL(walBackedFile, writeAheadLog, channelsAccessor, readOnly = false).use { walBackedChannel ->
          testCase.applyWritesTo(walBackedChannel)

          //read through the walBackedChannel
          val walChannelContent = readChannelContent(walBackedChannel)

          assertArrayEquals(
            directlyWrittenFileContent,
            walChannelContent,
            "[$turnNo]: after writes applied WAL-channel content must be the == regular file content"
          )

          writeAheadLog.flush()
          val walBackedFileContent = readAllBytes(walBackedFile)
          assertArrayEquals(
            directlyWrittenFileContent,
            walBackedFileContent,
            "[$turnNo]: after writes applied and flush()-ed WAL-mediated file content must be the == regular file content"
          )
        }
      }
    }
  }

  @RepeatedTest(TURNS_COUNT)
  fun `each write to WAL-backed Channel produces the same file content as write to FileChannel`(
    @TempDir tempDir: Path,
    repetitionInfo: RepetitionInfo,
  ) {
    val turnNo = repetitionInfo.currentRepetition
    val caseDir = tempDir.resolve("seed-$turnNo")
    Files.createDirectories(caseDir)
    val testCase = generateTestCase(turnNo)

    val directFile = caseDir.resolve("direct.bin")
    val walBackedFile = caseDir.resolve("wal-backed.bin")
    testCase.applyInitialState(directFile)
    testCase.applyInitialState(walBackedFile)
    assertArrayEquals(
      testCase.initialContent,
      readAllBytes(walBackedFile),
      "[$turnNo]: WAL-backed file content must == initialContent before writes applied"
    )

    open(directFile, READ, WRITE, CREATE).use { directChannel ->
      open(walBackedFile, READ, WRITE, CREATE).use { walBackedChannel ->
        val channelsAccessor = channelsAccessor(walBackedFile, walBackedChannel)
        openPersistentWriteAheadLog(caseDir, channelsAccessor).use { writeAheadLog ->
          FileChannelWithWAL(walBackedFile, writeAheadLog, channelsAccessor, readOnly = false).use { walBackedChannel ->
            testCase.writes.forEach { write ->
              write.writeFullyTo(directChannel)
              write.writeFullyTo(walBackedChannel)

              writeAheadLog.flush()

              val directlyWrittenFileContent = readAllBytes(directFile)
              val walChannelContent = readChannelContent(walBackedChannel)
              assertArrayEquals(
                directlyWrittenFileContent,
                walChannelContent,
                "[$turnNo]: after each write WAL-channel content must be the == regular file content"
              )
            }
          }
        }
      }
    }
  }


  @Test
  fun `applyUnfinished overlays persistent records without consuming them`(@TempDir tempDir: Path) {
    val caseDir = tempDir.resolve("wal")
    Files.createDirectories(caseDir)

    val storageFile = caseDir.resolve("storage.bin")
    val initialContent = byteArrayOf(1, 2, 3, 4, 5, 6)
    Files.write(storageFile, initialContent)
    val storagePath = storageFile.toRealPath()

    open(storagePath, READ, WRITE).use { channel ->
      val channelsAccessor = channelsAccessor(storagePath, channel)

      openPersistentWriteAheadLog(caseDir, channelsAccessor).use { writeAheadLog ->
        val fileWriter = writeAheadLog.openFor(storagePath)
        fileWriter.write(2, byteArrayOf(9, 9, 9), 0, 3)
        fileWriter.write(4, byteArrayOf(7, 7), 0, 2)

        val buffer = ByteBuffer.wrap(byteArrayOf(0, 1, 2, 3, 4, 5, 6))
        buffer.position(1)
        buffer.limit(6)

        assertEquals(0, writeAheadLog.statistics.bytesCopiedByApplyUnfinished)

        fileWriter.applyUnfinished(1, 5, buffer, 1)

        assertArrayEquals(byteArrayOf(0, 1, 9, 9, 7, 7, 6), buffer.array())
        assertEquals(1, buffer.position())
        assertEquals(6, buffer.limit())
        assertTrue(fileWriter.hasUnfinished())
        assertEquals(5, writeAheadLog.statistics.bytesCopiedByApplyUnfinished)
        assertArrayEquals(initialContent, readDirectChannelContent(channel))
      }
    }
  }

  @Test
  fun `overlay read applies persistent pending records without flushing them`(@TempDir tempDir: Path) {
    val caseDir = tempDir.resolve("wal")
    Files.createDirectories(caseDir)

    val storageFile = caseDir.resolve("storage.bin")
    val initialContent = byteArrayOf(1, 2, 3, 4)
    Files.write(storageFile, initialContent)
    val storagePath = storageFile.toRealPath()

    open(storagePath, READ, WRITE).use { channel ->
      val writableChannelsAccessor = channelsAccessor(storagePath, channel, readOnly = false)
      val readOnlyChannelsAccessor = channelsAccessor(storagePath, channel, readOnly = true)

      openPersistentWriteAheadLog(caseDir, writableChannelsAccessor).use { writeAheadLog ->
        FileChannelWithWAL(
          storagePath,
          writeAheadLog,
          writableChannelsAccessor,
          readOnly = false,
          applyUnfinishedOnRead = true,
        ).use { writableChannel ->
          writableChannel.write(ByteBuffer.wrap(byteArrayOf(9, 8)), 1)

          assertArrayEquals(initialContent, readDirectChannelContent(channel))

          FileChannelWithWAL(
            storagePath,
            writeAheadLog,
            readOnlyChannelsAccessor,
            readOnly = true,
            applyUnfinishedOnRead = true,
          ).use { readOnlyChannel ->
            val target = ByteBuffer.allocate(4)

            assertEquals(4, readOnlyChannel.read(target, 0))

            assertArrayEquals(byteArrayOf(1, 9, 8, 4), target.array())
            assertTrue(writeAheadLog.hasUnfinished())
            assertArrayEquals(initialContent, readDirectChannelContent(channel))
          }
        }
      }
    }
  }

  @Test
  fun `new channel sees size extended by persistent pending write`(@TempDir tempDir: Path) {
    val caseDir = tempDir.resolve("wal")
    Files.createDirectories(caseDir)

    val storageFile = caseDir.resolve("storage.bin")
    val initialContent = byteArrayOf(1, 2, 3, 4)
    Files.write(storageFile, initialContent)
    val storagePath = storageFile.toRealPath()

    open(storagePath, READ, WRITE).use { channel ->
      val writableChannelsAccessor = channelsAccessor(storagePath, channel, readOnly = false)
      val readOnlyChannelsAccessor = channelsAccessor(storagePath, channel, readOnly = true)

      openPersistentWriteAheadLog(caseDir, writableChannelsAccessor).use { writeAheadLog ->
        FileChannelWithWAL(
          storagePath,
          writeAheadLog,
          writableChannelsAccessor,
          readOnly = false,
          applyUnfinishedOnRead = true,
        ).use { writableChannel ->
          writableChannel.write(ByteBuffer.wrap(byteArrayOf(5, 6)), initialContent.size.toLong())

          assertEquals(6, writableChannel.size())
          assertArrayEquals(initialContent, readDirectChannelContent(channel))

          FileChannelWithWAL(
            storagePath,
            writeAheadLog,
            readOnlyChannelsAccessor,
            readOnly = true,
            applyUnfinishedOnRead = true,
          ).use { readOnlyChannel ->
            assertEquals(6, readOnlyChannel.size())

            val target = ByteBuffer.allocate(6)
            assertEquals(6, readOnlyChannel.read(target, 0))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), target.array())
            assertTrue(writeAheadLog.hasUnfinished())
            assertArrayEquals(initialContent, readDirectChannelContent(channel))
          }
        }
      }
    }
  }

  @Test
  fun `async flusher applies records and close stops flusher thread`(@TempDir tempDir: Path) {
    val caseDir = tempDir.resolve("wal")
    Files.createDirectories(caseDir)

    val storageFile = caseDir.resolve("storage.bin")
    val initialContent = byteArrayOf(1, 2, 3, 4)
    Files.write(storageFile, initialContent)
    val storagePath = storageFile.toRealPath()
    val flusherStarted = CountDownLatch(1)
    var flusherThread: Thread? = null
    val flusherThreadFactory = ThreadFactory { runnable ->
      Thread {
        flusherStarted.countDown()
        runnable.run()
      }.also {
        it.name = "test-wal-flusher"
        flusherThread = it
      }
    }

    open(storagePath, READ, WRITE).use { channel ->
      val channelsAccessor = channelsAccessor(storagePath, channel)
      WriteAheadLogOverCircularBuffer.openDefaultWAL(
        walBufferPath(caseDir),
        walEnumeratorPath(caseDir),
        WAL_CAPACITY,
        flusherThreadFactory = flusherThreadFactory,
        flushPeriodMs = 10,
      ) { path, offsetInFile, data ->
        writeFully(path, channelsAccessor, offsetInFile, data)
      }.use { writeAheadLog ->
        assertTrue(flusherStarted.await(5, TimeUnit.SECONDS), "WAL flusher thread must be started")

        writeAheadLog.openFor(storagePath).write(1, byteArrayOf(9, 8), 0, 2)

        assertAsyncFlushEventuallyAppliesRecords {
          readDirectChannelContent(channel).contentEquals(byteArrayOf(1, 9, 8, 4))
        }
      }
    }

    val stoppedFlusherThread = flusherThread ?: error("WAL flusher thread was not created")
    stoppedFlusherThread.join(5_000)
    assertFalse(stoppedFlusherThread.isAlive, "close() must stop WAL flusher thread")
  }

  @Test
  fun `unfinished records are applied after reopen`(@TempDir tempDir: Path) {
    val caseDir = tempDir.resolve("wal")
    Files.createDirectories(caseDir)

    val storageFile = caseDir.resolve("storage.bin")
    val initialContent = byteArrayOf(1, 2, 3, 4)
    Files.write(storageFile, initialContent)
    val storagePath = storageFile.toRealPath()

    open(storagePath, READ, WRITE).use { channel ->
      val channelsAccessor = channelsAccessor(storagePath, channel)

      openWriteAheadLogWithoutAutoFlushOnClose(caseDir, channelsAccessor).useWithoutFlushing { writeAheadLog ->
        val fileWriter = writeAheadLog.openFor(storagePath)
        fileWriter.write(1, byteArrayOf(9, 8), 0, 2)
        assertTrue(fileWriter.hasUnfinished())
      }

      assertArrayEquals(initialContent, readAllBytes(storagePath))

      openPersistentWriteAheadLog(caseDir, channelsAccessor).use { reopenedWriteAheadLog ->
        val reopenedFileWriter = reopenedWriteAheadLog.openFor(storagePath)
        assertFalse(reopenedFileWriter.hasUnfinished())
        assertEquals(0, reopenedWriteAheadLog.flush())
      }
    }

    assertArrayEquals(byteArrayOf(1, 9, 8, 4), readAllBytes(storagePath))
  }

  @Test
  fun `paths enumerator is cleaned on reopen if all records were applied`(@TempDir tempDir: Path) {
    val caseDir = tempDir.resolve("wal")
    Files.createDirectories(caseDir)

    val storageFile = caseDir.resolve("storage.bin")
    Files.write(storageFile, byteArrayOf(1, 2, 3, 4))
    val storagePath = storageFile.toRealPath()

    open(storagePath, READ, WRITE).use { channel ->
      val channelsAccessor = channelsAccessor(storagePath, channel)

      openPersistentWriteAheadLog(caseDir, channelsAccessor).use { writeAheadLog ->
        val fileWriter = writeAheadLog.openFor(storagePath)
        fileWriter.write(1, byteArrayOf(9, 8), 0, 2)

        writeAheadLog.flush()

        assertFalse(fileWriter.hasUnfinished())
      }

      openPersistentWriteAheadLog(caseDir, channelsAccessor).use {
        // openDefaultWAL must drop the stale path enumerator before opening a fresh empty one.
      }
    }

    openPathsEnumerator(caseDir).use { pathsEnumerator ->
      assertEquals(DataEnumerator.NULL_ID, pathsEnumerator.tryEnumerate(storagePath))
    }
  }

  @Test
  fun `reopen with unfinished records and missing paths enumerator fails as corrupted`(@TempDir tempDir: Path) {
    val caseDir = tempDir.resolve("wal")
    Files.createDirectories(caseDir)

    val storageFile = caseDir.resolve("storage.bin")
    Files.write(storageFile, byteArrayOf(1, 2, 3, 4))
    val storagePath = storageFile.toRealPath()

    open(storagePath, READ, WRITE).use { channel ->
      val channelsAccessor = channelsAccessor(storagePath, channel)

      openWriteAheadLogWithoutAutoFlushOnClose(caseDir, channelsAccessor).useWithoutFlushing { writeAheadLog ->
        writeAheadLog.openFor(storagePath).write(1, byteArrayOf(9, 8), 0, 2)
      }

      NioFiles.deleteRecursively(walEnumeratorPath(caseDir))

      assertThrows(CorruptedException::class.java) {
        openPersistentWriteAheadLog(caseDir, channelsAccessor)
      }
    }
  }

  private fun readChannelContent(walBackedChannel: FileChannelWithWAL): ByteArray {
    walBackedChannel.position(0)
    return newInputStream(walBackedChannel).readAllBytes()
  }

  private fun readDirectChannelContent(channel: FileChannel): ByteArray {
    val bytes = ByteArray(channel.size().toInt())
    val buffer = ByteBuffer.wrap(bytes)
    var offset = 0L
    while (buffer.hasRemaining()) {
      val bytesRead = channel.read(buffer, offset)
      if (bytesRead <= 0) {
        break
      }
      offset += bytesRead
    }
    return bytes
  }

  private fun assertAsyncFlushEventuallyAppliesRecords(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5_000)
    while (System.nanoTime() < deadline) {
      if (condition()) {
        return
      }
      Thread.sleep(10)
    }
    assertTrue(condition(), "Async flusher must apply pending WAL records")
  }


  private fun generateTestCase(seed: Int): TestCase {
    val random = Random(seed)
    val initialContent = random.nextBytes(INITIAL_FILE_SIZE)
    val writes = List(WRITES_PER_TURN) {
      Write(
        offsetInFile = random.nextLong(MAX_WRITE_OFFSET.toLong()),
        data = random.nextBytes(random.nextInt(1, MAX_WRITE_SIZE + 1)),
      )
    }
    return TestCase(initialContent, writes)
  }

  private fun openPersistentWriteAheadLog(
    tempDir: Path,
    channelsAccessor: ChannelsAccessor,
  ): WriteAheadLogOverCircularBuffer {
    return WriteAheadLogOverCircularBuffer.openDefaultWAL(
      walBufferPath(tempDir),
      walEnumeratorPath(tempDir),
      WAL_CAPACITY,
    ) { path, offsetInFile, data ->
      writeFully(path, channelsAccessor, offsetInFile, data)
    }
  }

  private fun openWriteAheadLogWithoutAutoFlushOnClose(
    tempDir: Path,
    channelsAccessor: ChannelsAccessor,
  ): OpenedWriteAheadLog {
    val circularBuffer = CircularBytesBufferOverMMappedFile.Factory
      .withCapacityAtLeast(WAL_CAPACITY)
      .cleanIfFileIncompatible()
      .open(walBufferPath(tempDir))
    val pathsEnumerator = openPathsEnumerator(tempDir)
    val writeAheadLog = WriteAheadLogOverCircularBuffer(circularBuffer, pathsEnumerator, { path, offsetInFile, data ->
      writeFully(path, channelsAccessor, offsetInFile, data)
    })

    return OpenedWriteAheadLog(writeAheadLog, circularBuffer, pathsEnumerator)
  }

  private class OpenedWriteAheadLog(
    val writeAheadLog: WriteAheadLogOverCircularBuffer,
    private val circularBuffer: CircularBytesBufferOverMMappedFile,
    private val pathsEnumerator: DurableDataEnumerator<Path>,
  ) {
    fun useWithoutFlushing(action: (WriteAheadLogOverCircularBuffer) -> Unit) {
      try {
        action(writeAheadLog)
      }
      finally {
        pathsEnumerator.force()
        circularBuffer.flush()
        pathsEnumerator.close()
        circularBuffer.close()
      }
    }
  }

  private fun openPathsEnumerator(tempDir: Path): DurableDataEnumerator<Path> {
    return DurableEnumeratorFactory.defaultWithInMemoryMap(WriteAheadLogOverCircularBuffer.CANONICAL_PATH_DESCRIPTOR)
      .valuesLogFactory(
        AppendOnlyLogFactory.withDefaults()
          .pageSize(1 * MiB)
          .cleanIfFileIncompatible()
          .failIfDataFormatVersionNotMatch(DurableEnumerator.DATA_FORMAT_VERSION)
      )
      .open(walEnumeratorPath(tempDir))
  }

  private fun walBufferPath(tempDir: Path): Path = tempDir.resolve("write-ahead-log.wal")

  private fun walEnumeratorPath(tempDir: Path): Path = tempDir.resolve("write-ahead-log.paths")

  @Suppress("ArrayInDataClass")
  private data class TestCase(val initialContent: ByteArray, val writes: List<Write>) {
    fun applyInitialState(path: Path) {
      Files.write(path, initialContent)
    }

    fun applyWritesTo(channel: FileChannel) {
      writes.forEach { write ->
        write.writeFullyTo(channel)
      }
    }
  }

  @Suppress("ArrayInDataClass")
  private data class Write(val offsetInFile: Long, val data: ByteArray) {
    fun writeFullyTo(channel: FileChannel) {
      writeFully(channel, offsetInFile, data)
    }
  }
}

private fun channelsAccessor(
  channelPath: Path,
  channel: FileChannel,
  readOnly: Boolean = false,
): ChannelsAccessor = object : ChannelsAccessor {
  override fun isReadOnly(): Boolean = readOnly

  override fun <T> executeOp(
    path: Path,
    operation: ChannelsAccessor.FileChannelOperation<T?>,
  ): T? {
    require(path == channelPath) { "Only [$channelPath] could be accessed, but [$path] requested" }
    return operation.execute(channel)
  }

  override fun <T> executeIdempotentOp(
    path: Path,
    operation: FileChannelInterruptsRetryer.FileChannelIdempotentOperation<T?>,
  ): T? {
    require(path == channelPath) { "Only [$channelPath] could be accessed, but [$path] requested" }
    return operation.execute(channel)
  }

  override fun closeChannel(path: Path) {
    channel.close()
  }
}

private fun writeFully(path: Path, channelsAccessor: ChannelsAccessor, offsetInFile: Long, data: ByteBuffer) {
  channelsAccessor.executeIdempotentOp(path) { channel -> writeFully(channel, offsetInFile, data) }
}

private fun writeFully(channel: FileChannel, offsetInFile: Long, data: ByteArray) {
  writeFully(channel, offsetInFile, ByteBuffer.wrap(data))
}

private fun writeFully(channel: FileChannel, offsetInFile: Long, data: ByteBuffer) {
  var position = offsetInFile
  while (data.hasRemaining()) {
    position += channel.write(data, position)
  }
}

private class CapacityOnlyCircularBytesBuffer(
  private val maxEntrySize: Int,
) : CircularBytesBuffer {
  override fun hasUnprocessedRecords(): Boolean = false

  override fun maxEntrySize(): Int = maxEntrySize

  override fun append(writer: ByteBufferWriter, entrySize: Int) {
    throw AssertionError("Too large WAL record must be rejected before appending to circular buffer")
  }

  override fun read(reader: CircularBytesBuffer.DataReader): Int = 0

  override fun close() = Unit

  override fun flush() = Unit
}

private class QueueFullOnceCircularBytesBuffer : CircularBytesBuffer {
  private var queueFullReported = false

  override fun hasUnprocessedRecords(): Boolean = false

  override fun maxEntrySize(): Int = 1024

  override fun append(writer: ByteBufferWriter, entrySize: Int) {
    if (!queueFullReported) {
      queueFullReported = true
      throw CircularBytesBuffer.QueueFullException()
    }
    writer.write(ByteBuffer.allocate(entrySize))
  }

  override fun read(reader: CircularBytesBuffer.DataReader): Int = 0

  override fun close() = Unit

  override fun flush() = Unit
}

private fun singlePathEnumerator(): DurableDataEnumerator<Path> = object : DurableDataEnumerator<Path> {
  override fun enumerate(value: Path?): Int = 1

  override fun valueOf(idx: Int): Path? = null

  override fun tryEnumerate(value: Path?): Int = throw UnsupportedOperationException("not implemented")

  override fun close() = Unit

  override fun force() = Unit

  override fun isDirty() = false
}
