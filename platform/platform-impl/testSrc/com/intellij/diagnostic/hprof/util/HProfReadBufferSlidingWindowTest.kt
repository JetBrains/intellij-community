// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.hprof.util

import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.min

internal class HProfReadBufferSlidingWindowTest {

  @TempDir
  lateinit var tempDir: Path

  private lateinit var parserChannel: FileChannel
  private lateinit var parser: HProfEventBasedParser

  @BeforeEach
  fun setUp() {
    val header = ByteArrayOutputStream().also { baos ->
      DataOutputStream(baos).use { dos ->
        dos.write("JAVA PROFILE 1.0.1".toByteArray(Charsets.UTF_8))
        dos.writeByte(0)
        dos.writeInt(8)
        dos.writeLong(0L)
      }
    }.toByteArray()
    val hprofFile = tempDir.resolve("min.hprof")
    Files.write(hprofFile, header)
    parserChannel = FileChannel.open(hprofFile, StandardOpenOption.READ)
    parser = HProfEventBasedParser(parserChannel)
  }

  @AfterEach
  fun tearDown() {
    parser.close()
    parserChannel.close()
  }

  // region Helpers

  private fun createByteArray(size: Int): ByteArray {
    val bytes = ByteArray(size)
    val full = size / 4
    val tail = size % 4
    val buf = ByteBuffer.wrap(bytes)
    for (i in 0 until full) buf.putInt(i)
    if (tail > 0) {
      val tailInt = ByteBuffer.allocate(4).putInt(full).array()
      System.arraycopy(tailInt, 0, bytes, full * 4, tail)
    }
    return bytes
  }

  private fun createSlidingWindowBuffer(data: ByteArray, windowSize: Int): HProfReadBufferSlidingWindow {
    val totalSize = data.size.toLong()
    return HProfReadBufferSlidingWindow(
      parser = parser,
      totalSize = totalSize,
      mapper = { start ->
        val length = min(windowSize.toLong(), totalSize - start).toInt()
        ByteBuffer.wrap(data, start.toInt(), length).slice()
      },
      windowSize = windowSize,
    )
  }

  /** Reads `size` bytes via `getByteBuffer(size)` and drains the returned sub-buffer; restores the source position. */
  private fun getBytes(buffer: HProfReadBufferSlidingWindow, from: Long, size: Long): ByteArray {
    buffer.mark()
    return try {
      buffer.position(from)
      val sub = buffer.getByteBuffer(size)
      readAllBytes(sub)
    }
    finally {
      buffer.reset()
    }
  }

  /** Returns the entire content of `buffer`; restores the buffer's position. */
  private fun readAllBytes(buffer: HProfReadBufferSlidingWindow): ByteArray {
    buffer.mark()
    return try {
      buffer.position(0)
      val out = ByteArray(buffer.limit().toInt())
      if (out.isNotEmpty()) buffer.get(out)
      out
    }
    finally {
      buffer.reset()
    }
  }

  private fun assertByteBuffer(expected: ByteArray, actual: ByteArray) {
    assertArrayEquals(expected, actual)
  }

  private fun expectedSlice(data: ByteArray, from: Int, size: Int): ByteArray =
    data.copyOfRange(from, from + size)

  private fun expectedIntAt(data: ByteArray, position: Int): Int =
    ByteBuffer.wrap(data, position, 4).int

  private fun expectedShortAt(data: ByteArray, position: Int): Short =
    ByteBuffer.wrap(data, position, 2).short

  private fun expectedLongAt(data: ByteArray, position: Int): Long =
    ByteBuffer.wrap(data, position, 8).long

  private inline fun <T> readAt(buffer: HProfReadBufferSlidingWindow, position: Long, op: HProfReadBufferSlidingWindow.() -> T): T {
    buffer.mark()
    return try {
      buffer.position(position)
      buffer.op()
    }
    finally {
      buffer.reset()
    }
  }

  @Test
  fun testByteBufferLargerThanWindow() {
    val data = createByteArray(size = 1_000_000)
    val buffer = createSlidingWindowBuffer(data, windowSize = 1024)
    assertByteBuffer(expectedSlice(data, from = 1000, size = 500_000), getBytes(buffer, from = 1000, size = 500_000))
  }

  @Test
  fun testGetIntCrossesWindowBoundary() {
    val data = createByteArray(size = 16) // ints 0..3 at byte offsets 0,4,8,12
    val buffer = createSlidingWindowBuffer(data, windowSize = 8)

    // straddles the [0;8) / [8;16) boundary by 2 bytes
    assertEquals(expectedIntAt(data, 6), readAt(buffer, 6) { getInt() })
    // straddles by 1 byte from the other side
    assertEquals(expectedIntAt(data, 7), readAt(buffer, 7) { getInt() })
    // stays entirely inside the first window
    assertEquals(expectedIntAt(data, 0), readAt(buffer, 0) { getInt() })
    // stays entirely inside the second window
    assertEquals(expectedIntAt(data, 12), readAt(buffer, 12) { getInt() })
  }

  @Test
  fun testWindowLargerThanTotal() {
    val data = createByteArray(size = 100)
    val buffer = createSlidingWindowBuffer(data, windowSize = 1024)

    assertEquals(100L, buffer.limit())
    assertByteBuffer(data, getBytes(buffer, from = 0, size = 100))
    assertByteBuffer(expectedSlice(data, 40, 20), getBytes(buffer, from = 40, size = 20))
    assertEquals(expectedIntAt(data, 96), readAt(buffer, 96) { getInt() })
  }

  @Test
  fun testWindowSameAsTotal() {
    val data = createByteArray(size = 1024)
    val buffer = createSlidingWindowBuffer(data, windowSize = 1024)

    assertEquals(1024L, buffer.limit())
    assertByteBuffer(data, getBytes(buffer, from = 0, size = 1024))
    assertByteBuffer(expectedSlice(data, 1020, 4), getBytes(buffer, from = 1020, size = 4))
    // last 4 bytes encode int #255 (1024 / 4 - 1)
    assertEquals(255, readAt(buffer, 1020) { getInt() })
  }

  @Test
  fun testWindowSmallerThanTotal() {
    val data = createByteArray(size = 4096)
    val buffer = createSlidingWindowBuffer(data, windowSize = 64)

    assertByteBuffer(data, getBytes(buffer, from = 0, size = 4096))
    assertByteBuffer(expectedSlice(data, 100, 200), getBytes(buffer, from = 100, size = 200))
    assertByteBuffer(expectedSlice(data, 1000, 2000), getBytes(buffer, from = 1000, size = 2000))
  }

  @Test
  fun testByteBufferFitsCurrentWindow() {
    val data = createByteArray(size = 1024)
    val buffer = createSlidingWindowBuffer(data, windowSize = 256)
    // entirely inside the first window [0; 256)
    assertByteBuffer(expectedSlice(data, 16, 32), getBytes(buffer, from = 16, size = 32))
  }

  @Test
  fun testByteBufferRequiresOneRemap() {
    val data = createByteArray(size = 1024)
    val buffer = createSlidingWindowBuffer(data, windowSize = 256)
    // at position 200 the current window has only 56 bytes left, but 100 < windowSize so a single remap satisfies the read
    assertByteBuffer(expectedSlice(data, 200, 100), getBytes(buffer, from = 200, size = 100))
  }

  @Test
  fun testByteBufferExceedingTotalThrowsBufferUnderflow() {
    val data = createByteArray(size = 1024)
    val buffer = createSlidingWindowBuffer(data, windowSize = 64)
    buffer.position(0)
    // 2000 > windowSize, so getByteBuffer goes through the byte-copy path which raises BufferUnderflowException
    assertThrows(BufferUnderflowException::class.java) {
      buffer.getByteBuffer(2000)
    }
  }

  @Test
  fun testPositionPastTotalSizeThrows() {
    val data = createByteArray(size = 1024)
    val buffer = createSlidingWindowBuffer(data, windowSize = 64)
    assertThrows(BufferUnderflowException::class.java) {
      buffer.position(1025)
    }
    // landing exactly on totalSize is allowed (it's the EOF position)
    buffer.position(1024)
    assertTrue(buffer.isEof())
  }

  @Test
  fun testSubBufferAllGetOperations() {
    val data = createByteArray(size = 1024)
    val buffer = createSlidingWindowBuffer(data, windowSize = 64)

    buffer.position(200)
    val sub = buffer.getByteBuffer(512)
    val subData = expectedSlice(data, 200, 512)

    assertEquals(512L, sub.limit())
    assertByteBuffer(subData, readAllBytes(sub))

    assertEquals(subData[0], readAt(sub, 0) { get() })
    assertEquals(subData[511], readAt(sub, 511) { get() })

    assertEquals(expectedShortAt(subData, 8), readAt(sub, 8) { getShort() })
    assertEquals(expectedIntAt(subData, 64), readAt(sub, 64) { getInt() })
    assertEquals(expectedLongAt(subData, 100), readAt(sub, 100) { getLong() })

    // a sub-of-sub still produces correct contents
    assertByteBuffer(
      expectedSlice(subData, 50, 100),
      getBytes(sub, from = 50, size = 100),
    )
  }

  @Test
  fun testSubBufferGetByteBufferAtAllSizes() {
    val data = createByteArray(size = 4096)
    val buffer = createSlidingWindowBuffer(data, windowSize = 256)
    buffer.position(100)
    val sub = buffer.getByteBuffer(2048)
    val subData = expectedSlice(data, 100, 2048)

    // smaller than the original window
    assertByteBuffer(expectedSlice(subData, 0, 50), getBytes(sub, from = 0, size = 50))
    // same size as the original window
    assertByteBuffer(expectedSlice(subData, 0, 256), getBytes(sub, from = 0, size = 256))
    // larger than the original window
    assertByteBuffer(expectedSlice(subData, 0, 1024), getBytes(sub, from = 0, size = 1024))
    // the entire sub-buffer
    assertByteBuffer(subData, getBytes(sub, from = 0, size = 2048))
    // an interior range
    assertByteBuffer(expectedSlice(subData, 500, 800), getBytes(sub, from = 500, size = 800))
  }

  @Test
  fun testSubBufferRemapUsesPropagatedWindowSizeAndMapper() {
    // Reach the sub-buffer's "branch B" (size < windowSize but > currentWindow.remaining), forcing
    // a remap inside the sub. That requires both propagated windowSize and a mapper that translates
    // sub-relative offsets to the parent's data origin.
    val data = createByteArray(size = 4096)
    val parent = createSlidingWindowBuffer(data, windowSize = 256)
    parent.position(100)
    val sub = parent.getByteBuffer(2048) // sub.windowSize must be 256, mapper must point at parent[100..]
    val subData = expectedSlice(data, 100, 2048)

    // Drive sub.position so currentWindow.remaining equals the requested size: branch A's check is
    // strict (`<`), so the read falls through to branch B (148 < windowSize = 256) and remaps via
    // newMapper. If newMapper points at the wrong parent offset, the bytes read will not match.
    sub.position(1900)
    val subOfSub = sub.getByteBuffer(148)
    assertEquals(148L, subOfSub.limit())
    assertByteBuffer(expectedSlice(subData, 1900, 148), readAllBytes(subOfSub))
  }

  @Test
  fun testSubBufferRecursiveShrink() {
    // [start; end) → [start+1; end-1) inductively until the sub fits a single original window.
    val data = createByteArray(size = 256)
    val originalWindowSize = 32
    val rootBuffer = createSlidingWindowBuffer(data, originalWindowSize)

    var sub: HProfReadBufferSlidingWindow = rootBuffer.getByteBuffer(data.size.toLong())
    var start = 0
    var end = data.size

    while (end - start > originalWindowSize) {
      sub.position(1)
      val nextLen = (end - start - 2).toLong()
      sub = sub.getByteBuffer(nextLen)
      start += 1
      end -= 1
      assertEquals((end - start).toLong(), sub.limit())
      assertByteBuffer(expectedSlice(data, start, end - start), readAllBytes(sub))
    }
  }

  @Test
  fun testEofAndRemaining() {
    val data = createByteArray(size = 100)
    val buffer = createSlidingWindowBuffer(data, windowSize = 32)

    // start
    assertFalse(buffer.isEof())
    assertEquals(100L, buffer.remaining())
    assertTrue(buffer.hasRemaining())

    // middle, on a window boundary
    buffer.position(32)
    assertFalse(buffer.isEof())
    assertEquals(68L, buffer.remaining())
    assertTrue(buffer.hasRemaining())

    // middle, off-boundary
    buffer.position(40)
    assertEquals(60L, buffer.remaining())
    assertTrue(buffer.hasRemaining())

    // end
    buffer.position(100)
    assertTrue(buffer.isEof())
    assertEquals(0L, buffer.remaining())
    assertFalse(buffer.hasRemaining())
  }
}
