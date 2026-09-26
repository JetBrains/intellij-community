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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OpenChannelsCacheTest {
  @Test
  fun `accessor views are stable and mode-bound`() {
    val cache = OpenChannelsCache("test-cache", 2, RecordingChannelOpener())

    assertSame(cache.asReadOnly(), cache.asReadOnly(), "Read-only accessor view must be stable")
    assertSame(cache.asWritable(), cache.asWritable(), "Writable accessor view must be stable")
    assertNotSame(cache.asReadOnly(), cache.asWritable(), "Different modes must use different accessor views")
    assertTrue(cache.asReadOnly().isReadOnly, "Read-only accessor view must report read-only mode")
    assertFalse(cache.asWritable().isReadOnly, "Writable accessor view must report writable mode")
  }

  @Test
  fun `executeOp reuses cached channel for repeated read access`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache("test-cache", 2, opener)
    val readOnlyAccessor = cache.asReadOnly()

    try {
      val firstChannel = readOnlyAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }
      val secondChannel = readOnlyAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }

      assertSame(firstChannel, secondChannel, "Repeated read-only access must reuse the cached descriptor")
      assertEquals(listOf(true), opener.opened.map { it.readOnly }, "Only one read-only channel should be opened")
      assertFalse(firstChannel.wasClosed, "Cached descriptor must stay open after operation release")
      assertEquals(1, cache.statistics.load, "First access should be counted as a cache load")
      assertEquals(1, cache.statistics.hit, "Second access should be counted as a cache hit")
      assertEquals(0, cache.statistics.miss, "No eviction or read/write mode switch should happen")
    }
    finally {
      readOnlyAccessor.closeChannel(file)
    }
  }

  @Test
  fun `executeIdempotentOp reuses cached resilient channel`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache("test-cache", 2, opener)
    val readOnlyAccessor = cache.asReadOnly()

    try {
      val firstChannel = readOnlyAccessor.executeIdempotentOp(file) { channel -> channel as TrackingFileChannel }
      val secondChannel = readOnlyAccessor.executeIdempotentOp(file) { channel -> channel as TrackingFileChannel }

      assertSame(firstChannel, secondChannel, "Idempotent operations must reuse the cached descriptor")
      assertEquals(2, firstChannel.idempotentOperationCount, "Both operations should run through the same resilient channel")
      assertEquals(listOf(true), opener.opened.map { it.readOnly }, "Only one read-only channel should be opened")
      assertFalse(firstChannel.wasClosed, "Cached descriptor must stay open after operation release")
    }
    finally {
      readOnlyAccessor.closeChannel(file)
    }
  }

  @Test
  fun `read-only accessor returns channel that rejects writes`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache("test-cache", 2, opener)
    val readOnlyAccessor = cache.asReadOnly()

    try {
      readOnlyAccessor.executeOp(file) { channel ->
        assertThrows<NonWritableChannelException>("Read-only channel must reject writes") {
          writeSingleByte(channel)
        }
      }

      assertEquals(listOf(true), opener.opened.map { it.readOnly }, "The descriptor should be opened in read-only mode")
    }
    finally {
      readOnlyAccessor.closeChannel(file)
    }
  }

  @Test
  fun `read-only accessor returns non-writable channel even if writable channel is cached`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache("test-cache", 2, opener)
    val readOnlyAccessor = cache.asReadOnly()
    val writableAccessor = cache.asWritable()

    try {
      val writableChannel = writableAccessor.executeOp(file) { channel ->
        writeSingleByte(channel)
        channel as TrackingFileChannel
      }

      assertFalse(writableChannel.readOnly, "Precondition: first cached descriptor must be writable")

      readOnlyAccessor.executeOp(file) { channel ->
        assertThrows<NonWritableChannelException>("Read-only request must not reuse a cached writable descriptor") {
          writeSingleByte(channel)
        }
      }

      assertEquals(listOf(false, true), opener.opened.map { it.readOnly }, "Read-only request should open a separate descriptor")
    }
    finally {
      readOnlyAccessor.closeChannel(file)
      writableAccessor.closeChannel(file)
    }
  }

  @Test
  fun `unlocked descriptor is closed on shared-capacity eviction`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache("test-cache", 1, opener)
    val readOnlyAccessor = cache.asReadOnly()
    val writableAccessor = cache.asWritable()

    try {
      val readOnlyChannel = readOnlyAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }
      val writableChannel = writableAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }

      assertTrue(readOnlyChannel.wasClosed, "Shared capacity should evict the first unlocked descriptor regardless of mode")
      assertFalse(writableChannel.wasClosed, "Newest descriptor should remain cached")
      assertEquals(listOf(true, false), opener.opened.map { it.readOnly }, "Both modes should be opened once")
      assertEquals(1, cache.statistics.load, "First access should be counted as a cache load")
      assertEquals(1, cache.statistics.miss, "Second access should be counted as a miss caused by eviction")
      assertEquals(1, cache.statistics.capacity, "Owner statistics must report shared physical capacity once")
    }
    finally {
      readOnlyAccessor.closeChannel(file)
      writableAccessor.closeChannel(file)
    }
  }

  @Test
  fun `read-only and writable descriptors are cached independently under shared owner`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache("test-cache", 2, opener)
    val readOnlyAccessor = cache.asReadOnly()
    val writableAccessor = cache.asWritable()

    try {
      val readOnlyChannel = readOnlyAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }
      val writableChannel = writableAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }
      val cachedReadOnlyChannel = readOnlyAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }
      val cachedWritableChannel = writableAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }

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
      assertEquals(2, cache.statistics.capacity, "Owner statistics must report shared physical capacity once")
    }
    finally {
      readOnlyAccessor.closeChannel(file)
      writableAccessor.closeChannel(file)
    }
  }

  @Test
  fun `closeChannel on one view does not close descriptor from another mode`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache("test-cache", 2, opener)
    val readOnlyAccessor = cache.asReadOnly()
    val writableAccessor = cache.asWritable()

    val readOnlyChannel = readOnlyAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }
    val writableChannel = writableAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }

    readOnlyAccessor.closeChannel(file)

    assertTrue(readOnlyChannel.wasClosed, "Read-only view should close the read-only descriptor")
    assertFalse(writableChannel.wasClosed, "Read-only view must not close the writable descriptor")

    writableAccessor.closeChannel(file)

    assertEquals(1, writableChannel.closeCount, "Writable view should close the writable descriptor exactly once")
  }

  @Test
  fun `opening channel does not block unrelated cache access`(@TempDir tempDir: Path) {
    val blockedFile = tempDir.resolve("blocked.bin")
    val probeFile = tempDir.resolve("probe.bin")
    val openEntered = CountDownLatch(1)
    val openMayFinish = CountDownLatch(1)
    val accessor = OpenChannelsCache(
      "test-cache",
      2,
      BlockingOpenChannelOpener(blockedFile, openEntered, openMayFinish),
    ).asWritable()

    val openFinished = CountDownLatch(1)
    val openFailure = AtomicReference<Throwable>()
    val openThread = runInThread("test-channel-opening", openFinished, openFailure) {
      accessor.executeOp(blockedFile) { }
    }

    val probeFinished = CountDownLatch(1)
    val probeFailure = AtomicReference<Throwable>()
    var probeThread: Thread? = null
    val probeCompletedWhileOpenIsBlocked: Boolean
    try {
      assertTrue(openEntered.await(5, TimeUnit.SECONDS), "Channel opener must enter the blocked open call")

      probeThread = runInThread("test-cache-probe-while-opening", probeFinished, probeFailure) {
        accessor.executeOp(probeFile) { }
      }
      probeCompletedWhileOpenIsBlocked = probeFinished.await(2, TimeUnit.SECONDS)
    }
    finally {
      openMayFinish.countDown()
      openThread.join(5_000)
      probeThread?.join(5_000)
      accessor.closeChannel(blockedFile)
      accessor.closeChannel(probeFile)
    }

    throwFailure("Blocked channel open failed", openFailure)
    throwFailure("Cache probe failed", probeFailure)
    assertTrue(openFinished.count == 0L, "Blocked channel open must finish after the test releases it")
    assertTrue(
      probeCompletedWhileOpenIsBlocked,
      "Opening a channel must not keep OpenChannelsCache locked for unrelated paths"
    )
  }

  @Test
  fun `closing evicted channel does not block unrelated cache access`(@TempDir tempDir: Path) {
    val evictedFile = tempDir.resolve("evicted.bin")
    val nextFile = tempDir.resolve("next.bin")
    val probeFile = tempDir.resolve("probe.bin")
    val closeEntered = CountDownLatch(1)
    val closeMayFinish = CountDownLatch(1)
    val accessor = OpenChannelsCache(
      "test-cache",
      1,
      BlockingCloseChannelOpener(evictedFile, closeEntered, closeMayFinish),
    ).asWritable()

    accessor.executeOp(evictedFile) { }

    val evictionFinished = CountDownLatch(1)
    val evictionFailure = AtomicReference<Throwable>()
    val evictionThread = runInThread("test-channel-eviction", evictionFinished, evictionFailure) {
      accessor.executeOp(nextFile) { }
    }

    val probeFinished = CountDownLatch(1)
    val probeFailure = AtomicReference<Throwable>()
    var probeThread: Thread? = null
    val probeCompletedWhileCloseIsBlocked: Boolean
    try {
      assertTrue(closeEntered.await(5, TimeUnit.SECONDS), "Eviction must start closing the cached channel")

      probeThread = runInThread("test-cache-probe-while-closing", probeFinished, probeFailure) {
        accessor.executeOp(probeFile) { }
      }
      probeCompletedWhileCloseIsBlocked = probeFinished.await(2, TimeUnit.SECONDS)
    }
    finally {
      closeMayFinish.countDown()
      evictionThread.join(5_000)
      probeThread?.join(5_000)
      accessor.closeChannel(evictedFile)
      accessor.closeChannel(nextFile)
      accessor.closeChannel(probeFile)
    }

    throwFailure("Evicting cached channel failed", evictionFailure)
    throwFailure("Cache probe failed", probeFailure)
    assertTrue(evictionFinished.count == 0L, "Eviction must finish after the test releases close")
    assertTrue(
      probeCompletedWhileCloseIsBlocked,
      "Closing an evicted channel must not keep OpenChannelsCache locked for unrelated paths"
    )
  }

  @Test
  fun `StorageLockContext assertNoOpenChannels reports descriptors from both mode views`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val cache = OpenChannelsCache("test-cache", 2, RecordingChannelOpener())
    val context = StorageLockContext(false, cache.asReadOnly(), cache.asWritable())
    val readOnlyAccessor = context.getChannelsAccessor(true)
    val writableAccessor = context.getChannelsAccessor(false)

    try {
      context.assertNoOpenChannels(file)

      writableAccessor.executeOp(file) { }
      val writableError = assertThrows<AssertionError> {
        context.assertNoOpenChannels(file)
      }
      assertTrue(writableError.message!!.contains("writable accessor"), writableError.message)

      readOnlyAccessor.executeOp(file) { }
      val bothModesError = assertThrows<AssertionError> {
        context.assertNoOpenChannels(file)
      }
      assertTrue(bothModesError.message!!.contains("read-only accessor"), bothModesError.message)
      assertTrue(bothModesError.message!!.contains("writable accessor"), bothModesError.message)
      assertTrue(bothModesError.message!!.contains(file.toString()), bothModesError.message)

      writableAccessor.closeChannel(file)
      val readOnlyError = assertThrows<AssertionError> {
        context.assertNoOpenChannels(file)
      }
      assertTrue(readOnlyError.message!!.contains("read-only accessor"), readOnlyError.message)

      readOnlyAccessor.closeChannel(file)
      context.assertNoOpenChannels(file)
    }
    finally {
      readOnlyAccessor.closeChannel(file)
      writableAccessor.closeChannel(file)
    }
  }

  @Test
  fun `locked read-only descriptor keeps cached channel and caches writable descriptor separately`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("storage.bin")
    val opener = RecordingChannelOpener()
    val cache = OpenChannelsCache("test-cache", 2, opener)
    val readOnlyAccessor = cache.asReadOnly()
    val writableAccessor = cache.asWritable()

    lateinit var readOnlyChannel: TrackingFileChannel
    lateinit var writableChannel: TrackingFileChannel

    try {
      readOnlyAccessor.executeOp(file) { outerChannel ->
        readOnlyChannel = outerChannel as TrackingFileChannel
        writableChannel = writableAccessor.executeOp(file) { nestedChannel -> nestedChannel as TrackingFileChannel }

        assertNotSame(readOnlyChannel, writableChannel, "Nested writable request must use a separate descriptor")
        assertFalse(readOnlyChannel.wasClosed, "Locked read-only descriptor must stay cached")
        assertFalse(writableChannel.wasClosed, "Nested writable descriptor should be cached, not closed as temporary")
      }

      val cachedWritableChannel = writableAccessor.executeOp(file) { channel -> channel as TrackingFileChannel }

      assertSame(writableChannel, cachedWritableChannel, "Nested writable descriptor should be reusable after outer operation completes")
      assertFalse(readOnlyChannel.wasClosed, "Read-only descriptor must remain cached until its view closes it")
      assertFalse(writableChannel.wasClosed, "Writable descriptor must remain cached until its view closes it")
      assertEquals(listOf(true, false), opener.opened.map { it.readOnly }, "Read-only and writable descriptors should be opened once each")
      assertEquals(2, cache.statistics.load, "First access for each read/write mode should be counted as a load")
      assertEquals(0, cache.statistics.miss, "Nested writable access should not bypass or miss the cache")
      assertEquals(1, cache.statistics.hit, "Repeated writable access should be counted as a hit")
    }
    finally {
      readOnlyAccessor.closeChannel(file)
      writableAccessor.closeChannel(file)
    }

    assertTrue(readOnlyChannel.wasClosed, "Read-only descriptor must be closed by read-only view")
    assertEquals(1, writableChannel.closeCount, "Writable descriptor must be closed by writable view exactly once")
  }

  private class RecordingChannelOpener : ChannelsAccessor.FileChannelOpener {
    val opened = mutableListOf<TrackingFileChannel>()

    override fun open(path: Path, readOnly: Boolean): FileChannel {
      return TrackingFileChannel(readOnly).also { opened += it }
    }
  }

  /** Blocks in [open] */
  private class BlockingOpenChannelOpener(
    private val path: Path,
    private val openEntered: CountDownLatch,
    private val openMayFinish: CountDownLatch,
  ) : ChannelsAccessor.FileChannelOpener {
    override fun open(path: Path, readOnly: Boolean): FileChannel {
      if (path == this@BlockingOpenChannelOpener.path) {
        openEntered.countDown()
        awaitLatch(openMayFinish)
      }
      return TrackingFileChannel(readOnly)
    }
  }

  /** Returned [FileChannel] blocks on [FileChannel.close] */
  private class BlockingCloseChannelOpener(
    private val path: Path,
    private val closeEntered: CountDownLatch,
    private val closeMayFinish: CountDownLatch,
  ) : ChannelsAccessor.FileChannelOpener {
    override fun open(path: Path, readOnly: Boolean): FileChannel {
      return if (path == this@BlockingCloseChannelOpener.path) {
        BlockingCloseFileChannel(readOnly, closeEntered, closeMayFinish)
      }
      else {
        TrackingFileChannel(readOnly)
      }
    }
  }

  /** Blocks during [close] */
  private class BlockingCloseFileChannel(
    readOnly: Boolean,
    private val closeEntered: CountDownLatch,
    private val closeMayFinish: CountDownLatch,
  ) : TrackingFileChannel(readOnly) {
    override fun implCloseChannel() {
      closeEntered.countDown()
      awaitLatch(closeMayFinish)
      super.implCloseChannel()
    }
  }

  private fun writeSingleByte(channel: FileChannel) {
    channel.write(ByteBuffer.wrap(byteArrayOf(42)))
  }

  private open class TrackingFileChannel(val readOnly: Boolean) : FileChannel(), Resilient {
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

  companion object {
    private fun runInThread(
      name: String,
      finished: CountDownLatch,
      failure: AtomicReference<Throwable>,
      action: () -> Unit,
    ): Thread {
      return Thread {
        try {
          action()
        }
        catch (t: Throwable) {
          failure.set(t)
        }
        finally {
          finished.countDown()
        }
      }.also {
        it.name = name
        it.isDaemon = true
        it.start()
      }
    }

    private fun throwFailure(message: String, failure: AtomicReference<Throwable>) {
      failure.get()?.let { throw AssertionError(message, it) }
    }
  }
}

private fun awaitLatch(latch: CountDownLatch) {
  var interrupted = false
  while (true) {
    try {
      if (latch.await(30, TimeUnit.SECONDS)) {
        break
      }
    }
    catch (_: InterruptedException) {
      interrupted = true
    }
  }
  if (interrupted) {
    Thread.currentThread().interrupt()
  }
}
