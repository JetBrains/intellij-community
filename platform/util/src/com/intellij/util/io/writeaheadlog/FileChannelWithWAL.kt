// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.writeaheadlog

import com.intellij.util.SystemProperties.getBooleanProperty
import com.intellij.util.io.ChannelsAccessor
import com.intellij.util.io.ChannelsAccessor.FileChannelOperation
import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation
import com.intellij.util.io.Resilient
import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * A [FileChannel] which queues writes in a [WriteAheadLog], to be applied later.
 * Read operations ([read], [size], etc.) preserve the illusion that writes were already applied.
 * [force] could be used to apply the writes synchronously.
 * <p/>
 * <b>BEWARE</b>: With this channel it could be a delay until the changes sunk into the actual file.
 * E.g., <b>`Files.size(path)` is not guaranteed</b> to always == `FileChannelWithWAL.size()`
 * (which is usually true for regular (platform-default) `FileChannel` impls) -- and the same for
 * modified timestamps, etc.
 * <p/>
 * In general, even though this class does its best to honestly implement [FileChannel], it does so
 * in a limited way (see 'not-implemented-yet' methods) -- which is enough for its main use-case inside
 * [com.intellij.util.io.FilePageCache] and some other uses, but may be not enough for some other
 * use-cases -- hence, use it with caution.
 */
@ApiStatus.Internal
class FileChannelWithWAL @Throws(IOException::class) constructor(
  private val path: Path,
  writeAheadLog: WriteAheadLog,
  channelsAccessor: ChannelsAccessor,
  private val readOnly: Boolean = channelsAccessor.isReadOnly,
  private val closeUnderlyingChannelOnClose: Boolean = true,
  private val applyUnfinishedOnRead: Boolean = APPLY_UNFINISHED_ON_READ,
  /** `false`: create an underlying file lazily, maybe async -- when some IO op touches it; `true`: force creation on init */
  createFileImmediately: Boolean = false,
) : FileChannel(), Resilient {

  init {
    //MAYBE RC: why need 2 different sources of readOnly? maybe just use channelsAccessor.isReadOnly?
    require(channelsAccessor.isReadOnly == readOnly) {
      "channelsAccessor mode (${channelsAccessor.isReadOnly}) must match FileChannelWithWAL mode ($readOnly)"
    }
    require(!createFileImmediately || !readOnly) {
      "createFileImmediately is not applicable for readOnly channel"
    }
  }

  /** Seal `path` argument in `channelsAccessor` so that it can't be accidentally called with any other path. */
  private val channelOpExecutor: ChannelOpExecutor = ChannelOpExecutor.partiallyApply(channelsAccessor, path)

  private val perFileWriter: WriteAheadLog.PerFileWriter = writeAheadLog.openFor(path)

  /** Cache fileSize, so [size] is always actual even without [force]. */
  @Volatile
  private var fileSize: Long = UNINITIALIZED_FILE_SIZE

  init {
    if (createFileImmediately) { // call .size() to actually open the underlying channel => trigger file creation
      ensureFileSizeInitialized()
    }
  }

  /** @GuardedBy(this) */
  private var position: Long = 0

  @Throws(IOException::class)
  override fun size(): Long {
    ensureOpen()
    return ensureFileSizeInitialized()
  }

  @Throws(IOException::class)
  override fun force(metaData: Boolean) {
    ensureOpen()
    val entriesFlushed = perFileWriter.flush()
    channelOpExecutor { it.force(metaData) }

    entriesFlushedOnForce.add(entriesFlushed)
  }

  /**
   * read always returns fresh data, i.e., as-if all the writes were already applied.
   * The actual writes _may_ be still pending or may be applied during read -- this is implementation-specific.
   */
  @Throws(IOException::class)
  override fun read(target: ByteBuffer, offset: Long): Int {
    ensureOpen()
    if (!target.hasRemaining()) {
      return 0
    }

    if (applyUnfinishedOnRead) {
      val fileSize = ensureFileSizeInitialized()
      val bytesToRead = minOf(target.remaining().toLong(), fileSize - offset).toInt()
      if (bytesToRead <= 0) {
        return -1
      }

      //The approach:
      // 1) read from the channel the bytes already written
      // 2) pad the buffer with 0-es until fileSize (which is an actual file size, with all pending writes applied)
      // 3) apply pending writes on top
      //TODO RC: not thread-safe approach: a write A could be pending when .channel state is read, but already flush()-ed
      //         when .applyFinished() is called => hence A is missed in data read from the channel, and in pending writes
      //         Better approach will be to first collect unfinished (pending) writes (into a temporary buffer?), then read
      //         bytes from the channel, pad with 0s, and apply unfinished data on top of it -- in this order some writes
      //         could be applied twice (to channel and temp buffer) -- but never missed.
      //TODO RC: I dislike the implementation with applyUnfinished() in general 'cos it looks overly complicated.
      //         It is also not clear does it provide the speedup: it doesn't avoid IO, it does avoid write IO, but at the
      //         expense of (potentially) applying pending writes multiple times -- while .flush()-based approach does it
      //         only once. The .flush()-based impl seems like a better way to go overall

      val offsetInBuffer = target.position()
      val bytesReadFromChannel = readAvailableBytes(offset, target, offsetInBuffer, bytesToRead)
      fillWithZeros(target, offsetInBuffer + bytesReadFromChannel, bytesToRead - bytesReadFromChannel)

      perFileWriter.applyUnfinished(offset, bytesToRead, target, offsetInBuffer)
      target.position(offsetInBuffer + bytesToRead)
      return bytesToRead
    }
    else {// simpler approach: flush() and read the result
      val entriesFlushed = perFileWriter.flush()
      val bytesRead = channelOpExecutor(object : FileChannelOperation<Int> {
        override fun execute(channel: FileChannel) = channel.read(target, offset)
        override fun toString() = "read($target, $offset)"
      })
      entriesFlushedOnRead.add(entriesFlushed)
      return bytesRead
    }
  }

  @Throws(IOException::class)
  override fun write(source: ByteBuffer, offsetInFile: Long): Int {
    require(!readOnly) { "write() is not applicable for readOnly channel" }
    require(offsetInFile >= 0) { "offset must be non-negative: $offsetInFile" }
    ensureOpen()

    val bytesToWrite = source.remaining()
    if (bytesToWrite == 0) {
      return 0
    }

    perFileWriter.write(offsetInFile, { target ->
      target.put(source)
    }, bytesToWrite)

    updateSizeAfterWrite(offsetInFile, bytesToWrite)
    return bytesToWrite
  }

  @Synchronized
  @Throws(IOException::class)
  override fun write(source: ByteBuffer): Int {
    ensureOpen()

    val bytesWritten = write(source, position)
    if (bytesWritten > 0) {
      position += bytesWritten
    }
    return bytesWritten
  }

  @Synchronized
  @Throws(IOException::class)
  override fun position(newPosition: Long): FileChannel {
    ensureOpen()
    require(newPosition >= 0) { "newPosition must be non-negative: $newPosition" }
    position = newPosition
    return this
  }

  @Synchronized
  @Throws(IOException::class)
  override fun read(target: ByteBuffer): Int {
    ensureOpen()

    val bytesRead = read(target, position)
    if (bytesRead > 0) {
      position += bytesRead
    }
    return bytesRead
  }

  @Synchronized
  @Throws(IOException::class)
  override fun position(): Long {
    ensureOpen()
    return position
  }

  @Synchronized
  @Throws(IOException::class)
  override fun truncate(size: Long): FileChannel {
    require(!readOnly) { "truncate() is not applicable for readOnly channel" }
    ensureOpen()
    if (size > ensureFileSizeInitialized()) {
      return this//do nothing, as per spec
    }

    val entriesFlushed = perFileWriter.flush()
    channelOpExecutor { it.truncate(size) }

    fileSize = size
    position = position.coerceAtMost(size)

    entriesFlushedOnTruncate.add(entriesFlushed)
    return this
  }

  override fun toString(): String = "WriteAheadLogFileChannel[$path][readOnly:$readOnly]"


  @Synchronized
  @Throws(IOException::class)
  protected override fun implCloseChannel() {
    val entriesFlushed = perFileWriter.flush()
    entriesFlushedOnClose.add(entriesFlushed)
    if (closeUnderlyingChannelOnClose) {
      channelOpExecutor.close()
    }
  }

  override fun <T> executeOperation(operation: FileChannelIdempotentOperation<T>): T {
    //don't need retries if the operation is done over the write-ahead log?
    return operation.execute(this)
  }

  private fun updateSizeAfterWrite(offset: Long, bytesWritten: Int) {
    val newSize = offset + bytesWritten
    val currentFileSize = fileSize
    //if(FileSize == UNINITIALIZED_FILE_SIZE || newSize <= currentFileSize) return!
    if (currentFileSize != UNINITIALIZED_FILE_SIZE && newSize <= currentFileSize) {
      return
    }

    synchronized(this) {
      val recheckedFileSize = fileSize
      if (recheckedFileSize != UNINITIALIZED_FILE_SIZE) {
        fileSize = maxOf(recheckedFileSize, newSize)
      }
    }
  }

  private fun ensureFileSizeInitialized(): Long {
    val currentFileSize = fileSize
    if (currentFileSize != UNINITIALIZED_FILE_SIZE) {
      return currentFileSize
    }

    synchronized(this) {
      val recheckedFileSize = fileSize
      if (recheckedFileSize != UNINITIALIZED_FILE_SIZE) {
        return recheckedFileSize
      }

      return calculateInitialFileSize().also { fileSize = it }
    }
  }

  private fun calculateInitialFileSize(): Long {
    //Important to (read WAL) before actual channel.size(): this way fileSize is always correct even though
    // it could be a bit outdated, but it could miss only the writes coming _in parallel_ with
    // calculateInitialFileSize().
    // On the other hand: first channel.size() and then (WAL read) could _miss_ some writes that have came
    // _before_ the calculateInitialFileSize() is even started -- which is plainly incorrect.
    val maxUnfinishedWriteOffset = perFileWriter.maxUnfinishedWriteOffset()
    val channelSize = channelOpExecutor(object : FileChannelOperation<Long> {
      override fun execute(channel: FileChannel): Long = channel.size()

      override fun toString(): String = "size()"
    })

    return maxOf(channelSize, maxUnfinishedWriteOffset)
  }

  private fun ensureOpen() {
    if (!isOpen) {
      throw ClosedChannelException()
    }
  }


  data class FlushStatistics(
    val entriesFlushedOnRead: Long,
    val entriesFlushedOnForce: Long,
    val entriesFlushedOnTruncate: Long,
    val entriesFlushedOnClose: Long,
  )

  private fun readAvailableBytes(offsetInFile: Long, target: ByteBuffer, offsetInBuffer: Int, bytesToRead: Int): Int {
    return channelOpExecutor(object : FileChannelOperation<Int> {
      override fun execute(channel: FileChannel): Int {
        val bytesAvailableInChannel = minOf(bytesToRead.toLong(), channel.size() - offsetInFile).toInt()
        if (bytesAvailableInChannel <= 0) {
          return 0
        }

        val readTarget = target.duplicate()
        readTarget.position(offsetInBuffer)
        readTarget.limit(offsetInBuffer + bytesAvailableInChannel)

        var currentOffset = offsetInFile
        var bytesReadTotal = 0
        while (readTarget.hasRemaining()) {
          val bytesRead = channel.read(readTarget, currentOffset)
          if (bytesRead <= 0) {
            break
          }
          currentOffset += bytesRead
          bytesReadTotal += bytesRead
        }
        return bytesReadTotal
      }

      override fun toString(): String = "read($target, $offsetInFile)"
    })
  }

  private fun fillWithZeros(target: ByteBuffer, offsetInBuffer: Int, length: Int) {
    val zeroFillTarget = target.duplicate()
    zeroFillTarget.position(offsetInBuffer)
    zeroFillTarget.limit(offsetInBuffer + length)
    while (zeroFillTarget.hasRemaining()) {
      zeroFillTarget.put(0)
    }
  }

  companion object {
    private const val UNINITIALIZED_FILE_SIZE = -1L

    private val APPLY_UNFINISHED_ON_READ = getBooleanProperty("indexes.write-ahead-log.apply-unfinished-on-read", false)

    /** Accumulated statistics of flush()-ed entries, split by different 'causes' */
    private val entriesFlushedOnRead = AtomicLong()
    private val entriesFlushedOnForce = AtomicLong()
    private val entriesFlushedOnTruncate = AtomicLong()
    private val entriesFlushedOnClose = AtomicLong()

    @JvmStatic
    fun getFlushStatistics(): FlushStatistics = FlushStatistics(
      entriesFlushedOnRead = entriesFlushedOnRead.get(),
      entriesFlushedOnForce = entriesFlushedOnForce.get(),
      entriesFlushedOnTruncate = entriesFlushedOnTruncate.get(),
      entriesFlushedOnClose = entriesFlushedOnClose.get(),
    )

    private fun AtomicLong.add(add: Int) {
      if (add != 0) {
        addAndGet(add.toLong())
      }
    }
  }


  // ==================== not implemented: ============================================================ //
  // Some methods are hard to implement with WAL, while they are not used in current use-case (inside FilePageCache)

  @Deprecated("Method is not implemented yet: mapped writes would bypass WriteAheadLog")
  override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer {
    throw UnsupportedOperationException("Method not implemented yet: mapped writes would bypass WriteAheadLog which is undesirable")
  }

  @Deprecated("Method is not implemented yet: no use")
  override fun read(targets: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    throw UnsupportedOperationException("Method not implemented yet: no use")
  }

  @Deprecated("Method is not implemented yet: no use")
  override fun write(sources: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    throw UnsupportedOperationException("Method not implemented yet: no use")
  }

  @Deprecated("Method is not implemented yet: no use")
  override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long {
    throw UnsupportedOperationException("Method not implemented yet: no use")
  }

  @Deprecated("Method is not implemented yet: no use")
  override fun transferFrom(source: ReadableByteChannel, position: Long, count: Long): Long {
    throw UnsupportedOperationException("Method not implemented yet: no use")
  }

  @Deprecated("Method is not implemented yet: no use")
  override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
    throw UnsupportedOperationException("Method not implemented yet: no use")
  }

  @Deprecated("Method is not implemented yet: no use")
  override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock {
    throw UnsupportedOperationException("Method not implemented yet: no use")
  }


  private interface ChannelOpExecutor : Closeable {
    operator fun <T> invoke(op: FileChannelOperation<T>): T

    override fun close()

    companion object {
      /** Partial-application: fixes 'path' argument */
      @JvmStatic
      fun partiallyApply(channelsAccessor: ChannelsAccessor, path: Path): ChannelOpExecutor {
        return object : ChannelOpExecutor {
          override fun <T> invoke(op: FileChannelOperation<T>): T {
            return channelsAccessor.executeOp<T>(path, op)
          }

          override fun close() {
            channelsAccessor.closeChannel(path)
          }
        }
      }
    }
  }
}
