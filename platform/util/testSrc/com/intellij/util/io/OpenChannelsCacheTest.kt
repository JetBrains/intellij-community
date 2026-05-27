// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.NonWritableChannelException
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Path

class OpenChannelsCacheTest {
  @Test
  fun `executeOp reuses cached channel for repeated read access`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val readOnly = true

    val cache = OpenChannelsCache(2, opener)
    try {
      val firstChannel = cache.executeOp(file, { channel -> channel as TrackingFileChannel }, readOnly)
      val secondChannel = cache.executeOp(file, { channel -> channel as TrackingFileChannel }, readOnly)

      assertSame(firstChannel, secondChannel, "Repeated read-only access must reuse the cached descriptor")
      assertEquals(listOf(readOnly), opener.opened.map { it.readOnly }, "Only one read-only channel should be opened")
      assertFalse(firstChannel.wasClosed, "Cached descriptor must stay open after operation release")
      assertEquals(1, cache.statistics.load, "First access should be counted as a cache load")
      assertEquals(1, cache.statistics.hit, "Second access should be counted as a cache hit")
      assertEquals(0, cache.statistics.miss, "No eviction or read/write mode switch should happen")
    }
    finally {
      cache.closeChannel(file)
    }
  }

  @Test
  fun `executeIdempotentOp reuses cached resilient channel`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val readOnly = true

    val cache = OpenChannelsCache(2, opener)
    try {
      val firstChannel = cache.executeIdempotentOp(file, { channel -> channel as TrackingFileChannel }, readOnly)
      val secondChannel = cache.executeIdempotentOp(file, { channel -> channel as TrackingFileChannel }, readOnly)

      assertSame(firstChannel, secondChannel, "Idempotent operations must reuse the cached descriptor")
      assertEquals(2, firstChannel.idempotentOperationCount, "Both operations should run through the same resilient channel")
      assertEquals(listOf(readOnly), opener.opened.map { it.readOnly }, "Only one read-only channel should be opened")
      assertFalse(firstChannel.wasClosed, "Cached descriptor must stay open after operation release")
    }
    finally {
      cache.closeChannel(file)
    }
  }

  @Test
  fun `read-only request returns channel that rejects writes`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val readOnly = true

    val cache = OpenChannelsCache(2, opener)
    try {
      cache.executeOp(file, { channel ->
        assertThrows<NonWritableChannelException>("Read-only channel must reject writes") {
          writeSingleByte(channel)
        }
      }, readOnly)

      assertEquals(listOf(readOnly), opener.opened.map { it.readOnly }, "The descriptor should be opened in read-only mode")
    }
    finally {
      cache.closeChannel(file)
    }
  }

  @Test
  fun `read-only request returns non-writable channel even if writable channel is cached`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()

    val cache = OpenChannelsCache(2, opener)
    try {
      val writableChannel = cache.executeOp(file, { channel ->
        writeSingleByte(channel)
        channel as TrackingFileChannel
      }, /*readOnly: */false)

      assertFalse(writableChannel.readOnly, "Precondition: first cached descriptor must be writable")

      cache.executeOp(file, { channel ->
        assertThrows<NonWritableChannelException>("Read-only request must not reuse a cached writable descriptor") {
          writeSingleByte(channel)
        }
      }, /*readOnly: */true)

      assertEquals(listOf(false, true), opener.opened.map { it.readOnly }, "Read-only request should open a separate descriptor")
    }
    finally {
      cache.closeChannel(file)
    }
  }

  @Test
  fun `unlocked descriptor is closed on eviction`(@TempDir tempDir: Path) {
    val firstFile = tempDir.resolve("first.bin")
    val secondFile = tempDir.resolve("second.bin")
    val opener = RecordingChannelOpener()
    val readOnly = true

    val cache = OpenChannelsCache(1, opener)
    try {
      val firstChannel = cache.executeOp(firstFile, { channel -> channel as TrackingFileChannel }, readOnly)
      val secondChannel = cache.executeOp(secondFile, { channel -> channel as TrackingFileChannel }, readOnly)

      assertTrue(firstChannel.wasClosed, "Unlocked first descriptor should be closed when evicted")
      assertFalse(secondChannel.wasClosed, "Newest descriptor should remain cached")
      assertEquals(listOf(firstFile, secondFile), opener.opened.map { it.path }, "Both paths should be opened once")
      assertEquals(1, cache.statistics.load, "First access should be counted as a cache load")
      assertEquals(1, cache.statistics.miss, "Second access should be counted as a miss caused by eviction")
    }
    finally {
      cache.closeChannel(firstFile)
      cache.closeChannel(secondFile)
    }
  }

  @Test
  fun `read-only and writable descriptors are cached independently`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache(2, opener)

    try {
      val readOnlyChannel = cache.executeOp(file, { channel -> channel as TrackingFileChannel },       /*readOnly: */ true)
      val writableChannel = cache.executeOp(file, { channel -> channel as TrackingFileChannel },       /*readOnly: */ false)
      val cachedReadOnlyChannel = cache.executeOp(file, { channel -> channel as TrackingFileChannel }, /*readOnly: */ true)
      val cachedWritableChannel = cache.executeOp(file, { channel -> channel as TrackingFileChannel }, /*readOnly: */ false)

      assertNotSame(readOnlyChannel, writableChannel, "Read-only and writable descriptors must be cached separately")
      assertSame(readOnlyChannel, cachedReadOnlyChannel, "Read-only access should reuse the read-only descriptor")
      assertSame(writableChannel, cachedWritableChannel, "Writable access should reuse the writable descriptor")
      assertFalse(readOnlyChannel.wasClosed, "Read-only descriptor must remain cached")
      assertFalse(writableChannel.readOnly, "Writable descriptor should be opened in writable mode")
      assertFalse(writableChannel.wasClosed, "Writable descriptor must remain cached")
      assertEquals(listOf(true, false), opener.opened.map { it.readOnly }, "Read-only and writable descriptors should be opened once each")
      assertEquals(2, cache.statistics.load, "First access for each read/write mode should be counted as a load")
      assertEquals(0, cache.statistics.miss, "No eviction should happen when capacity fits both descriptors")
      assertEquals(2, cache.statistics.hit, "Second access for each read/write mode should be counted as a hit")
    }
    finally {
      cache.closeChannel(file)
    }
  }

  @Test
  fun `locked read-only descriptor keeps cached channel and caches writable descriptor separately`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()

    lateinit var readOnlyChannel: TrackingFileChannel
    lateinit var writableChannel: TrackingFileChannel

    val cache = OpenChannelsCache(2, opener)
    try {
      cache.executeOp(file, { outerChannel ->
        readOnlyChannel = outerChannel as TrackingFileChannel
        writableChannel = cache.executeOp(file, { nestedChannel -> nestedChannel as TrackingFileChannel }, false)

        assertNotSame(readOnlyChannel, writableChannel, "Nested writable request must use a separate descriptor")
        assertFalse(readOnlyChannel.wasClosed, "Locked read-only descriptor must stay cached")
        assertFalse(writableChannel.wasClosed, "Nested writable descriptor should be cached, not closed as temporary")
      }, true)

      val cachedWritableChannel = cache.executeOp(file, { channel -> channel as TrackingFileChannel }, false)

      assertSame(writableChannel, cachedWritableChannel, "Nested writable descriptor should be reusable after outer operation completes")
      assertFalse(readOnlyChannel.wasClosed, "Read-only descriptor must remain cached until closeChannel(path)")
      assertFalse(writableChannel.wasClosed, "Writable descriptor must remain cached until closeChannel(path)")
      assertEquals(listOf(true, false), opener.opened.map { it.readOnly }, "Read-only and writable descriptors should be opened once each")
      assertEquals(2, cache.statistics.load, "First access for each read/write mode should be counted as a load")
      assertEquals(0, cache.statistics.miss, "Nested writable access should not bypass or miss the cache")
      assertEquals(1, cache.statistics.hit, "Repeated writable access should be counted as a hit")
    }
    finally {
      cache.closeChannel(file)
    }

    assertTrue(readOnlyChannel.wasClosed, "closeChannel(path) should close the read-only descriptor")
    assertEquals(1, writableChannel.closeCount, "closeChannel(path) should close the writable descriptor exactly once")
  }

  private class RecordingChannelOpener : ChannelsAccessor.FileChannelOpener {
    val opened = mutableListOf<TrackingFileChannel>()

    override fun open(path: Path, readOnly: Boolean): FileChannel {
      return TrackingFileChannel(path, readOnly).also { opened += it }
    }
  }

  private fun writeSingleByte(channel: FileChannel) {
    channel.write(ByteBuffer.wrap(byteArrayOf(42)))
  }

  private class TrackingFileChannel(val path: Path, val readOnly: Boolean) : FileChannel(), Resilient {
    var closeCount: Int = 0
      private set

    var idempotentOperationCount: Int = 0
      private set

    val wasClosed: Boolean
      get() = closeCount > 0

    private var position: Long = 0

    override fun <T> executeOperation(operation: FileChannelInterruptsRetryer.FileChannelIdempotentOperation<T>): T {
      idempotentOperationCount++
      return operation.execute(this)
    }

    override fun read(dst: ByteBuffer): Int {
      ensureOpen()
      return -1
    }

    override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long {
      ensureOpen()
      return -1
    }

    override fun read(dst: ByteBuffer, position: Long): Int {
      ensureOpen()
      return -1
    }

    override fun write(src: ByteBuffer): Int {
      ensureOpen()
      ensureWritable()
      val bytesWritten = src.remaining()
      src.position(src.limit())
      position += bytesWritten
      return bytesWritten
    }

    override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long {
      ensureOpen()
      ensureWritable()
      var bytesWritten = 0L
      for (i in offset until offset + length) {
        bytesWritten += write(srcs[i]).toLong()
      }
      return bytesWritten
    }

    override fun write(src: ByteBuffer, position: Long): Int {
      ensureOpen()
      ensureWritable()
      val bytesWritten = src.remaining()
      src.position(src.limit())
      return bytesWritten
    }

    override fun position(): Long {
      ensureOpen()
      return position
    }

    override fun position(newPosition: Long): FileChannel {
      ensureOpen()
      position = newPosition
      return this
    }

    override fun size(): Long {
      ensureOpen()
      return 0
    }

    override fun truncate(size: Long): FileChannel {
      ensureOpen()
      if (position > size) {
        position = size
      }
      return this
    }

    override fun force(metaData: Boolean) {
      ensureOpen()
    }

    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long {
      throw UnsupportedOperationException()
    }

    override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long {
      throw UnsupportedOperationException()
    }

    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer {
      throw UnsupportedOperationException()
    }

    override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
      throw UnsupportedOperationException()
    }

    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock {
      throw UnsupportedOperationException()
    }

    override fun implCloseChannel() {
      closeCount++
    }

    private fun ensureOpen() {
      if (!isOpen) {
        throw ClosedChannelException()
      }
    }

    private fun ensureWritable() {
      if (readOnly) {
        throw NonWritableChannelException()
      }
    }
  }
}
