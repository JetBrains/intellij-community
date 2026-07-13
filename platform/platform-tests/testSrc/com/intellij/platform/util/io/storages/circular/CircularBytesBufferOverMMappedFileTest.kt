// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.circular

import com.intellij.platform.util.io.storages.circular.CircularBytesBuffer.QueueFullException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CircularBytesBufferOverMMappedFileTest {
  @Test
  fun `hasUnprocessedRecords is false initially`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      assertFalse { queue.hasUnprocessedRecords() }
    }
  }

  @Test
  fun `hasUnprocessedRecords is become true after records added`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      assertFalse("Empty queue has no unprocessed records") { queue.hasUnprocessedRecords() }

      queue.append("one".toByteArray(UTF_8))

      assertTrue("Must have 1 unprocessed record") { queue.hasUnprocessedRecords() }

      queue.read { }

      assertTrue("Must still have 1 unprocessed record") { queue.hasUnprocessedRecords() }

      queue.readConsuming { }
      assertFalse("Must have no unprocessed record") { queue.hasUnprocessedRecords() }
    }
  }

  @Test
  fun `read-only keeps entries but continues scanning`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      queue.append("one".toByteArray(UTF_8))
      queue.append("two".toByteArray(UTF_8))

      val firstPass = ArrayList<String>()
      queue.read { entryData ->
        firstPass += readString(entryData)
      }

      assertThat(firstPass).containsExactly("one", "two")

      val secondPass = ArrayList<String>()
      val consumedOnSecondPass = queue.readConsuming { entryData ->
        secondPass += readString(entryData)
      }

      assertThat(consumedOnSecondPass).isEqualTo(2)
      assertThat(secondPass).containsExactly("one", "two")
    }
  }

  @Test
  fun `stop keeps current and later entries for the next scan`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      queue.append("one".toByteArray(UTF_8))
      queue.append("two".toByteArray(UTF_8))
      queue.append("three".toByteArray(UTF_8))

      val firstPass = ArrayList<String>()
      val consumed = queue.readMaybeConsuming { entryData ->
        when (val record = readString(entryData)) {
          "one" -> CircularBytesBuffer.ReadDecision.consumeBy { leasedEntryData ->
            firstPass += readString(leasedEntryData)
          }
          "two" -> CircularBytesBuffer.ReadDecision.stop()
          else -> error("stop() must prevent processing later FIFO records, but reached $record")
        }
      }

      assertThat(consumed)
        .withFailMessage("stop() must leave the current record and all later records unconsumed")
        .isEqualTo(1)
      assertThat(firstPass).containsExactly("one")

      val secondPass = ArrayList<String>()
      queue.readConsuming { entryData ->
        secondPass += readString(entryData)
      }

      assertThat(secondPass).containsExactly("two", "three")
    }
  }

  @Test
  fun `entries are read in FIFO order even after wrap-around`(@TempDir tempDir: Path) {
    withQueue(tempDir, capacity = 64) { queue ->
      queue.append(record(size = 8, marker = 1))
      queue.append(record(size = 8, marker = 2))
      queue.append(record(size = 8, marker = 3))

      val consumedBeforeWrap = queue.readMaybeConsuming { entryData ->
        when (val marker = entryData.get(entryData.position()).toInt()) {
          1, 2 -> CircularBytesBuffer.ReadDecision.consumeBy { }
          3 -> CircularBytesBuffer.ReadDecision.stop()
          else -> error("Unexpected record marker before wrap-around: $marker")
        }
      }
      assertThat(consumedBeforeWrap)
        .withFailMessage("The third record must remain as the FIFO prefix before appending wrapped records")
        .isEqualTo(2)

      queue.append(record(size = 20, marker = 4))
      queue.append(record(size = 4, marker = 5))

      val records = ArrayList<ByteArray>()
      queue.readConsuming { entryData ->
        records += readBytes(entryData)
      }

      assertThat(records.map { it.size }).containsExactly(8, 20, 4)
      assertThat(records.map { it[0].toInt() }).containsExactly(3, 4, 5)
    }
  }

  @Test
  fun `append fails if there is no room`(@TempDir tempDir: Path) {
    withQueue(tempDir, capacity = 64) { queue ->
      repeat(5) {
        queue.append(record(size = 8, marker = it))
      }

      assertThrows(QueueFullException::class.java) {
        queue.append(record(size = 8, marker = 6))
      }
    }
  }

  @Test
  fun `append fails if writer does not fill the whole entry buffer`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      assertThrows(IllegalStateException::class.java) {
        queue.append(
          { target ->
            target.put(1.toByte())
            target
          },
          2
        )
      }
    }
  }

  @Test
  fun `append is not blocked by active reader callback`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      queue.append("one".toByteArray(UTF_8))

      val readerStarted = CountDownLatch(1)
      val readerCanFinish = CountDownLatch(1)
      val readerError = AtomicReference<Throwable?>()
      val readerThread = Thread {
        try {
          queue.readConsuming { entryData ->
            assertThat(readString(entryData)).isEqualTo("one")
            readerStarted.countDown()
            assertTrue("Reader callback must be released by the test") {
              readerCanFinish.await(5, SECONDS)
            }
          }
        }
        catch (t: Throwable) {
          readerError.set(t)
        }
      }

      readerThread.start()
      assertTrue("Reader callback must start") { readerStarted.await(5, SECONDS) }

      val appendCompleted = CountDownLatch(1)
      val appendError = AtomicReference<Throwable?>()
      val appendThread = Thread {
        try {
          queue.append("two".toByteArray(UTF_8))
          appendCompleted.countDown()
        }
        catch (t: Throwable) {
          appendError.set(t)
        }
      }
      appendThread.start()

      assertTrue("append() must not wait until reader callback exits") { appendCompleted.await(5, SECONDS) }

      readerCanFinish.countDown()
      readerThread.join(5_000)
      appendThread.join(5_000)

      assertFalse("Reader thread must finish") { readerThread.isAlive }
      assertFalse("Append thread must finish") { appendThread.isAlive }
      appendError.get()?.let { throw AssertionError("append() failed", it) }
      readerError.get()?.let { throw AssertionError("readConsuming() failed", it) }

      val remainingRecords = ArrayList<String>()
      queue.readConsuming { entryData ->
        remainingRecords += readString(entryData)
      }
      assertThat(remainingRecords).containsExactly("two")
    }
  }

  @Test
  fun `decision is not recomputed after leased record is released unconsumed`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      queue.append("one".toByteArray(UTF_8))

      val firstReaderStarted = CountDownLatch(1)
      val firstReaderCanFinish = CountDownLatch(1)
      val firstReaderError = AtomicReference<Throwable?>()
      val firstReaderThread = Thread {
        try {
          val consumed = queue.readMaybeConsuming {
            CircularBytesBuffer.ReadDecision.readBy { entryData ->
              assertThat(readString(entryData)).isEqualTo("one")
              firstReaderStarted.countDown()
              assertTrue("The test must release the first reader after the second reader decides") {
                firstReaderCanFinish.await(5, SECONDS)
              }
            }
          }
          assertThat(consumed)
            .withFailMessage("The first reader must release the lease without consuming the record")
            .isEqualTo(0)
        }
        catch (t: Throwable) {
          firstReaderError.set(t)
        }
      }

      firstReaderThread.start()
      try {
        assertTrue("The first reader must lease the record before the second reader starts") {
          firstReaderStarted.await(5, SECONDS)
        }

        val secondReaderDecided = CountDownLatch(1)
        val secondReaderError = AtomicReference<Throwable?>()
        val decisions = AtomicInteger()
        val secondReaderThread = Thread {
          try {
            val consumed = queue.readMaybeConsuming { entryData ->
              decisions.incrementAndGet()
              assertThat(readString(entryData))
                .withFailMessage("The decision stage may read routing data but must be reset before processing")
                .isEqualTo("one")
              secondReaderDecided.countDown()
              CircularBytesBuffer.ReadDecision.consumeBy { leasedEntryData ->
                assertThat(readString(leasedEntryData)).isEqualTo("one")
              }
            }
            assertThat(consumed).isEqualTo(1)
          }
          catch (t: Throwable) {
            secondReaderError.set(t)
          }
        }

        secondReaderThread.start()
        assertTrue("The second reader must decide while the record is leased by the first reader") {
          secondReaderDecided.await(5, SECONDS)
        }
        assertThat(decisions.get())
          .withFailMessage("Decision must be made once and reused after the foreign lease is released")
          .isEqualTo(1)

        firstReaderCanFinish.countDown()
        firstReaderThread.join(5_000)
        secondReaderThread.join(5_000)

        assertFalse("The first reader thread must finish") { firstReaderThread.isAlive }
        assertFalse("The second reader thread must finish") { secondReaderThread.isAlive }
        firstReaderError.get()?.let { throw AssertionError("first reader failed", it) }
        secondReaderError.get()?.let { throw AssertionError("second reader failed", it) }
        assertThat(decisions.get())
          .withFailMessage("Waiting for a leased record must not rerun the already computed decision")
          .isEqualTo(1)
        assertFalse("The second reader must consume the record") { queue.hasUnprocessedRecords() }
      }
      finally {
        firstReaderCanFinish.countDown()
      }
    }
  }

  @Test
  fun `full buffer is distinguished from empty buffer when offsets match`(@TempDir tempDir: Path) {
    withQueue(tempDir, capacity = 64) { queue ->
      repeat(4) {
        queue.append(record(size = 12, marker = it))
      }

      assertThrows(QueueFullException::class.java) {
        queue.append(record(size = 1, marker = 5))
      }

      val records = ArrayList<ByteArray>()
      queue.readConsuming { entryData ->
        records += readBytes(entryData)
      }

      assertThat(records.map { it.size }).containsExactly(12, 12, 12, 12)
      assertThat(records.map { it[0].toInt() }).containsExactly(0, 1, 2, 3)

      queue.append(record(size = 12, marker = 4))

      val recordsAfterDrain = ArrayList<ByteArray>()
      queue.readConsuming { entryData ->
        recordsAfterDrain += readBytes(entryData)
      }

      assertThat(recordsAfterDrain.map { it[0].toInt() }).containsExactly(4)
    }
  }

  @Test
  fun `unconsumed entries survive reopen`(@TempDir tempDir: Path) {
    val storagePath = tempDir.resolve("queue.mmap")
    var queue = openQueue(storagePath)
    try {
      queue.append("one".toByteArray(UTF_8))
      queue.append("two".toByteArray(UTF_8))
      queue.close()

      queue = openQueue(storagePath)

      val records = ArrayList<String>()
      queue.readConsuming { entryData ->
        records += readString(entryData)
      }

      assertThat(records).containsExactly("one", "two")
    }
    finally {
      queue.closeAndClean()
    }
  }

  @Test
  fun `withFileSizeNoMoreThan creates file no larger than power-of-two limit`(@TempDir tempDir: Path) {
    val maxFileSize = 128

    assertFileSizeNoMoreThan(tempDir, maxFileSize)
  }

  @Test
  fun `withFileSizeNoMoreThan creates file no larger than non-power-of-two limit`(@TempDir tempDir: Path) {
    val maxFileSize = 1_000

    assertFileSizeNoMoreThan(tempDir, maxFileSize)
  }

  private fun withQueue(tempDir: Path, capacity: Int = 128, action: (CircularBytesBufferOverMMappedFile) -> Unit) {
    val queue = openQueue(tempDir.resolve("queue.mmap"), capacity)
    try {
      action(queue)
    }
    finally {
      queue.closeAndClean()
    }
  }

  private fun openQueue(storagePath: Path, capacity: Int = 128): CircularBytesBufferOverMMappedFile {
    return CircularBytesBufferOverMMappedFile.Factory.withCapacityAtLeast(capacity).open(storagePath)
  }

  private fun assertFileSizeNoMoreThan(tempDir: Path, maxFileSize: Int) {
    val storagePath = tempDir.resolve("queue.mmap")
    val queue = CircularBytesBufferOverMMappedFile.Factory.withFileSizeNoMoreThan(maxFileSize).open(storagePath)
    try {
      assertThat(Files.size(storagePath))
        .withFailMessage("Factory must create a file no larger than the requested maxFileSize")
        .isLessThanOrEqualTo(maxFileSize.toLong())
    }
    finally {
      queue.closeAndClean()
    }
  }

  private fun CircularBytesBuffer.append(bytes: ByteArray) {
    append(bytes, 0, bytes.size)
  }

  private fun record(size: Int, marker: Int): ByteArray = ByteArray(size) { marker.toByte() }

  private fun readString(buffer: ByteBuffer): String = String(readBytes(buffer), UTF_8)

  private fun readBytes(buffer: ByteBuffer): ByteArray {
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
  }
}
