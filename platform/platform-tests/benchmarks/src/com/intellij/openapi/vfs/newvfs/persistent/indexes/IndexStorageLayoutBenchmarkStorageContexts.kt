// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.indexes

import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProvider
import com.intellij.util.indexing.impl.storage.PersistentWriteAheadLogFactory
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.io.ChannelsAccessor
import com.intellij.util.io.ChannelsAccessor.FileChannelOperation
import com.intellij.util.io.ChannelsAccessor.FileChannelOpener
import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation
import com.intellij.util.io.OpenChannelsCache
import com.intellij.util.io.PageCacheUtils
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.writeaheadlog.FileChannelWithWAL
import com.intellij.util.io.writeaheadlog.WriteAheadLog
import com.intellij.util.io.writeaheadlog.WriteAheadLog.ToFileWriter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

// Java-facing entry point for building storage providers with benchmark-specific WAL and IO-delay behavior.
object IndexStorageLayoutBenchmarkStorageContexts {
  private const val DISABLED_WAL_MODE = "disabled"
  private const val GLOBAL_WAL_PROPERTY = "indexes.use-write-ahead-log"
  private val DEFAULT_PROVIDER_CLASS_NAME = DefaultIndexStorageLayoutProvider::class.java.name

  @JvmStatic
  fun isDefaultProvider(className: String): Boolean = className == DEFAULT_PROVIDER_CLASS_NAME

  @JvmStatic
  fun defaultProvider(
    walMode: String,
    profile: IoLatencyProfile,
    asyncWALFlusher: Boolean,
  ): FileBasedIndexLayoutProvider {
    // Keep production global WAL disabled so this benchmark controls WAL explicitly through StorageLockContext.
    System.setProperty(GLOBAL_WAL_PROPERTY, DISABLED_WAL_MODE)

    val factory = BenchmarkStorageLockContextFactory(WalMode.parse(walMode), profile, asyncWALFlusher)
    return BenchmarkStorageLayoutProvider(DefaultIndexStorageLayoutProvider(factory::createStorageLockContext), factory)
  }

}

// WAL modes exposed as JMH parameter values.
private enum class WalMode {
  DISABLED,
  PERSISTENT;

  companion object {
    fun parse(value: String): WalMode = when (value) {
      "disabled" -> DISABLED
      "persistent" -> PERSISTENT
      else -> throw IllegalArgumentException("Unknown WAL mode: $value")
    }
  }
}

// Describes an artificial IO delay profile and collects delayed-operation statistics for benchmark diagnostics.
class IoLatencyProfile private constructor(
  private val name: String,
  private val readDelayNanos: Long,
  private val writeDelayNanos: Long,
  private val forceDelayNanos: Long,
  private val writeSpikeProbability: Double,
  private val writeSpikeDelayNanos: Long,
) {
  private val readCalls = AtomicLong()
  private val writeCalls = AtomicLong()
  private val forceCalls = AtomicLong()
  private val readBytes = AtomicLong()
  private val writeBytes = AtomicLong()
  private val totalDelayNanos = AtomicLong()

  fun afterRead(path: Path, bytes: Long) {
    delay(path, IoOperation.READ, bytes, readDelayNanos)
  }

  fun afterWrite(path: Path, bytes: Long) {
    val spikeDelayNanos = if (writeSpikeProbability > 0.0 && ThreadLocalRandom.current().nextDouble() < writeSpikeProbability) {
      writeSpikeDelayNanos
    }
    else {
      0L
    }
    delay(path, IoOperation.WRITE, bytes, writeDelayNanos + spikeDelayNanos)
  }

  fun afterForce(path: Path) {
    delay(path, IoOperation.FORCE, 0, forceDelayNanos)
  }

  fun resetStatistics() {
    readCalls.set(0)
    writeCalls.set(0)
    forceCalls.set(0)
    readBytes.set(0)
    writeBytes.set(0)
    totalDelayNanos.set(0)
  }

  fun drainStatistics(): String {
    val delayedReads = readCalls.getAndSet(0)
    val delayedWrites = writeCalls.getAndSet(0)
    val delayedForces = forceCalls.getAndSet(0)
    val delayedReadBytes = readBytes.getAndSet(0)
    val delayedWriteBytes = writeBytes.getAndSet(0)
    val delayedNanos = totalDelayNanos.getAndSet(0)

    if (delayedReads == 0L && delayedWrites == 0L && delayedForces == 0L) {
      return ""
    }

    return "IO latency profile '$name': " +
           "delayedReads=$delayedReads, delayedReadBytes=$delayedReadBytes, " +
           "delayedWrites=$delayedWrites, delayedWriteBytes=$delayedWriteBytes, " +
           "delayedForces=$delayedForces, totalDelayMs=${delayedNanos / 1_000_000}"
  }

  private fun delay(path: Path, operation: IoOperation, bytes: Long, delayNanos: Long) {
    // Delay index storage files only. WAL log files are excluded because this benchmark measures how WAL hides slow backing-file IO.
    if (delayNanos <= 0 || isWalFile(path)) {
      return
    }

    when (operation) {
      IoOperation.READ -> {
        readCalls.incrementAndGet()
        readBytes.addAndGet(bytes)
      }
      IoOperation.WRITE -> {
        writeCalls.incrementAndGet()
        writeBytes.addAndGet(bytes)
      }
      IoOperation.FORCE -> forceCalls.incrementAndGet()
    }

    totalDelayNanos.addAndGet(delayNanos)
    LockSupport.parkNanos(delayNanos)
  }

  private fun isWalFile(path: Path): Boolean {
    return path.fileName?.toString()?.startsWith("write-ahead-log.") == true
  }

  private enum class IoOperation {
    READ,
    WRITE,
    FORCE,
  }

  companion object {
    @JvmStatic
    fun parse(value: String): IoLatencyProfile = when (value) {
      "none" -> IoLatencyProfile(value, 0, 0, 0, 0.0, 0)
      "write_200us" -> IoLatencyProfile(value, 0, 200_000, 0, 0.0, 0)
      "write_1ms_force_2ms" -> IoLatencyProfile(value, 0, 1_000_000, 2_000_000, 0.0, 0)
      "write_200us_p99_50ms" -> IoLatencyProfile(value, 0, 200_000, 0, 0.01, 50_000_000)
      else -> throw IllegalArgumentException("Unknown IO latency profile: $value")
    }
  }
}

// Wraps the real layout provider and owns benchmark-only resources tied to it.
private class BenchmarkStorageLayoutProvider(
  private val delegate: FileBasedIndexLayoutProvider,
  private val closeable: AutoCloseable,
) : FileBasedIndexLayoutProvider, AutoCloseable {
  private var closed = false

  override fun <K, V> getLayout(
    extension: FileBasedIndexExtension<K, V>,
    otherApplicableProviders: Iterable<FileBasedIndexLayoutProvider>,
  ): VfsAwareIndexStorageLayout<K, V> {
    return delegate.getLayout(extension, otherApplicableProviders)
  }

  override fun isApplicable(extension: FileBasedIndexExtension<*, *>): Boolean = delegate.isApplicable(extension)

  override fun isSupported(): Boolean = delegate.isSupported

  override fun close() {
    if (!closed) {
      closed = true
      closeable.close()
    }
  }

  override fun toString(): String = delegate.toString()
}

// Builds benchmark-specific storage contexts without changing production storage code.
// Disabled WAL gets delayed direct channels; persistent WAL gets FileChannelWithWAL-backed channels.
private class BenchmarkStorageLockContextFactory(
  walMode: WalMode,
  profile: IoLatencyProfile,
  asyncWALFlusher: Boolean,
) : AutoCloseable {
  private val delayedReadAccessor = DelayedChannelsAccessor(PageCacheUtils.getCachedChannelsAccessor(/*readOnly: */ true), profile)
  private val delayedWriteAccessor = DelayedChannelsAccessor(PageCacheUtils.getCachedChannelsAccessor(/*readOnly: */false), profile)
  private val walRoot: Path?
  private val wal: WriteAheadLog?
  private val walChannels: OpenChannelsCache?

  init {
    if (walMode == WalMode.PERSISTENT) {
      walRoot = Files.createTempDirectory("IndexStorageLayoutBenchmark-wal-")
      wal = createWal(walRoot, asyncWALFlusher)
      walChannels = OpenChannelsCache("test-cache", PageCacheUtils.CHANNELS_CACHE_CAPACITY, createWalChannelOpener(wal))
    }
    else {
      walRoot = null
      wal = null
      walChannels = null
    }
  }

  fun createStorageLockContext(): StorageLockContext {
    val channels = walChannels
    return if (channels == null) {
      StorageLockContext(false, delayedReadAccessor, delayedWriteAccessor)
    }
    else {
      StorageLockContext(false, channels.asReadOnly(), channels.asWritable())
    }
  }

  override fun close() {
    wal?.close()
    walRoot?.let(NioFiles::deleteRecursively)
  }

  private fun createWal(walRoot: Path, asyncWALFlusher: Boolean): WriteAheadLog {
    return PersistentWriteAheadLogFactory.setup(
      directory = walRoot,
      // null keeps WAL flush work on the caller path; a thread factory moves it to the async flusher.
      flusherThreadFactory = if (asyncWALFlusher) Thread.ofVirtual().name("IndexStorageLayoutBenchmarkWALFlusher").factory() else null,
      toFileWriter = createDelayedToFileWriter(),
      invalidateCaches = {},
    ) ?: throw IOException("Persistent WAL initialization failed for $walRoot")
  }

  private fun createDelayedToFileWriter(): ToFileWriter {
    // WAL applies records back to target storage files through this callback, so route it through the delayed accessor too.
    return ToFileWriter { path, offsetInFile, buffer ->
      delayedWriteAccessor.executeOp(path) { channel ->
        var offset = offsetInFile
        while (buffer.hasRemaining()) {
          offset += channel.write(buffer, offset)
        }
      }
    }
  }

  private fun createWalChannelOpener(wal: WriteAheadLog): FileChannelOpener {
    return FileChannelOpener { path, readOnly ->
      FileChannelWithWAL(path, wal, if (readOnly) delayedReadAccessor else delayedWriteAccessor, readOnly)
    }
  }
}

// Preserves the original ChannelsAccessor cache/retry behavior and injects delays only at FileChannel operation boundaries.
private class DelayedChannelsAccessor(
  private val delegate: ChannelsAccessor,
  private val profile: IoLatencyProfile,
) : ChannelsAccessor {
  override fun isReadOnly(): Boolean = delegate.isReadOnly

  override fun <T> executeOp(path: Path, operation: FileChannelOperation<T>): T {
    return delegate.executeOp(path, FileChannelOperation { channel ->
      operation.execute(DelayedFileChannel(path, channel, profile))
    })
  }

  override fun <T> executeIdempotentOp(path: Path, operation: FileChannelIdempotentOperation<T>): T {
    return delegate.executeIdempotentOp(path, FileChannelIdempotentOperation { channel ->
      operation.execute(DelayedFileChannel(path, channel, profile))
    })
  }

  override fun closeChannel(path: Path) {
    delegate.closeChannel(path)
  }

  override fun toString(): String = "DelayedChannelsAccessor[$delegate]"
}

// FileChannel wrapper that delegates operations and reports read/write/force calls to the active latency profile.
private class DelayedFileChannel(
  private val path: Path,
  private val delegate: FileChannel,
  private val profile: IoLatencyProfile,
) : FileChannel() {
  override fun read(dst: ByteBuffer): Int {
    val bytes = delegate.read(dst)
    profile.afterRead(path, maxOf(bytes.toLong(), 0))
    return bytes
  }

  override fun read(dst: ByteBuffer, position: Long): Int {
    val bytes = delegate.read(dst, position)
    profile.afterRead(path, maxOf(bytes.toLong(), 0))
    return bytes
  }

  override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    val bytes = delegate.read(dsts, offset, length)
    profile.afterRead(path, maxOf(bytes, 0))
    return bytes
  }

  override fun write(src: ByteBuffer): Int {
    val bytes = delegate.write(src)
    profile.afterWrite(path, maxOf(bytes.toLong(), 0))
    return bytes
  }

  override fun write(src: ByteBuffer, position: Long): Int {
    val bytes = delegate.write(src, position)
    profile.afterWrite(path, maxOf(bytes.toLong(), 0))
    return bytes
  }

  override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    val bytes = delegate.write(srcs, offset, length)
    profile.afterWrite(path, maxOf(bytes, 0))
    return bytes
  }

  override fun position(): Long = delegate.position()

  override fun position(newPosition: Long): FileChannel {
    delegate.position(newPosition)
    return this
  }

  override fun size(): Long = delegate.size()

  override fun truncate(size: Long): FileChannel {
    delegate.truncate(size)
    return this
  }

  override fun force(metaData: Boolean) {
    delegate.force(metaData)
    profile.afterForce(path)
  }

  override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long {
    return delegate.transferTo(position, count, target)
  }

  override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long {
    return delegate.transferFrom(src, position, count)
  }

  override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer {
    // Page faults from MappedByteBuffer access are outside this FileChannel-level delay model.
    return delegate.map(mode, position, size)
  }

  override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
    return delegate.lock(position, size, shared)
  }

  override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? {
    return delegate.tryLock(position, size, shared)
  }

  override fun implCloseChannel() {
    delegate.close()
  }

  override fun toString(): String = "DelayedFileChannel[$delegate]"
}
