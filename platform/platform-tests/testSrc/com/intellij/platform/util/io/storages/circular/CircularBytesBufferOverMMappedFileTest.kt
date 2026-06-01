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

      queue.read { /*consume: */false }

      assertTrue("Must still have 1 unprocessed record") { queue.hasUnprocessedRecords() }

      queue.read { /*consume: */ true }
      assertFalse("Must have no unprocessed record") { queue.hasUnprocessedRecords() }
    }
  }

  @Test
  fun `read returning false keeps entries but continues scanning`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      queue.append("one".toByteArray(UTF_8))
      queue.append("two".toByteArray(UTF_8))

      val firstPass = ArrayList<String>()
      val consumed = queue.read { entryData ->
        firstPass += readString(entryData)
        false
      }

      assertThat(consumed).isEqualTo(0)
      assertThat(firstPass).containsExactly("one", "two")

      val secondPass = ArrayList<String>()
      val consumedOnSecondPass = queue.read { entryData ->
        secondPass += readString(entryData)
        true
      }

      assertThat(consumedOnSecondPass).isEqualTo(2)
      assertThat(secondPass).containsExactly("one", "two")
    }
  }

  @Test
  fun `consumed entries are skipped even if an earlier entry is kept`(@TempDir tempDir: Path) {
    withQueue(tempDir) { queue ->
      queue.append("one".toByteArray(UTF_8))
      queue.append("two".toByteArray(UTF_8))
      queue.append("three".toByteArray(UTF_8))

      val firstPass = ArrayList<String>()
      val consumed = queue.read { entryData ->
        firstPass += readString(entryData)
        firstPass.size != 2
      }

      assertThat(consumed).isEqualTo(2)
      assertThat(firstPass).containsExactly("one", "two", "three")

      val secondPass = ArrayList<String>()
      queue.read { entryData ->
        secondPass += readString(entryData)
        true
      }

      assertThat(secondPass).containsExactly("two")
    }
  }

  @Test
  fun `entries are read in FIFO order even after wrap-around`(@TempDir tempDir: Path) {
    withQueue(tempDir, capacity = 64) { queue ->
      queue.append(record(size = 8, marker = 1))
      queue.append(record(size = 8, marker = 2))
      queue.append(record(size = 8, marker = 3))

      queue.read { entryData ->
        readBytes(entryData)[0] != 3.toByte()
      }

      queue.append(record(size = 20, marker = 4))
      queue.append(record(size = 4, marker = 5))

      val records = ArrayList<ByteArray>()
      queue.read { entryData ->
        records += readBytes(entryData)
        true
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
  fun `full buffer is distinguished from empty buffer when offsets match`(@TempDir tempDir: Path) {
    withQueue(tempDir, capacity = 64) { queue ->
      repeat(4) {
        queue.append(record(size = 12, marker = it))
      }

      assertThrows(QueueFullException::class.java) {
        queue.append(record(size = 1, marker = 5))
      }

      val records = ArrayList<ByteArray>()
      queue.read { entryData ->
        records += readBytes(entryData)
        true
      }

      assertThat(records.map { it.size }).containsExactly(12, 12, 12, 12)
      assertThat(records.map { it[0].toInt() }).containsExactly(0, 1, 2, 3)

      queue.append(record(size = 12, marker = 4))

      val recordsAfterDrain = ArrayList<ByteArray>()
      queue.read { entryData ->
        recordsAfterDrain += readBytes(entryData)
        true
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
      queue.read { entryData ->
        records += readString(entryData)
        true
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
