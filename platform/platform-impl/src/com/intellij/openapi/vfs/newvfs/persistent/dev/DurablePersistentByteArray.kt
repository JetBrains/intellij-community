// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.newvfs.persistent.dev.DurablePersistentByteArray.Companion.FIRST_INSTANCE_MARK
import com.intellij.openapi.vfs.newvfs.persistent.dev.DurablePersistentByteArray.Companion.INVALID_CHECKSUM
import com.intellij.openapi.vfs.newvfs.persistent.dev.DurablePersistentByteArray.Companion.OpenMode
import com.intellij.openapi.vfs.newvfs.persistent.dev.DurablePersistentByteArray.Companion.SECOND_INSTANCE_MARK
import com.intellij.openapi.vfs.newvfs.persistent.dev.DurablePersistentByteArrayImpl.Companion.CompactLayoutBuilder
import com.intellij.openapi.vfs.newvfs.persistent.dev.DurablePersistentByteArrayImpl.Companion.LayoutHandler
import com.intellij.util.io.ResilientFileChannel
import com.intellij.util.io.createParentDirectories
import org.jetbrains.annotations.ApiStatus
import java.io.Flushable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.*
import java.util.zip.CRC32
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.moveTo

/**
 * [DurablePersistentByteArray] provides thread-safe means to store a ByteArray in a persistent storage with a durability guarantee.
 * E.g. if the application gets killed while the [commitChange] was in progress, on the next start the application will see
 * the last consistent state that was observable before the commit attempt.
 *
 * Actual amount of storage space it uses is `2*size + O(1)`.
 *
 * This code is written in an assumption that parts of the file -- not blocks, but ranges of bytes -- that are not touched by write operations
 * would not change in any kind of catastrophe that the filesystem can survive. This may be a false assumption.
 *
 * There should not exist more than one instance on the same path.
 */
@ApiStatus.Internal
interface DurablePersistentByteArray : AutoCloseable {
  fun getLastSnapshot(): ByteArray
  fun commitChange(modify: (ByteArray) -> Unit): ByteArray

  companion object {
    internal val LOG = logger<DurablePersistentByteArray>()

    enum class OpenMode(val openOptions: Set<OpenOption>) {
      Read(EnumSet.of(READ)),
      ReadWrite(EnumSet.of(READ, WRITE))
    }

    internal const val INVALID_CHECKSUM: Long = 0x239L shl 48 // special value

    internal const val FIRST_INSTANCE_MARK: Int = 0x1a1a1a1a
    internal const val SECOND_INSTANCE_MARK: Int = 0x2b2b2b2

    /**
     * @param path where to store the data
     * @param size size of the ByteArray to be stored and read. It is perhaps better to use multiples of 8 for better aligning.
     * @param makeDefaultValue will be used to generate the initial value, if file at [path] does not exist
     * @param mode if equals [OpenMode.Read] and [path] does not exist,
     * won't create the file and will instead return an instance filled with contents of [makeDefaultValue]
     *
     * @throws IncompatibleLayoutException if there is a version mismatch or size does not match the one that was used to create the file
     * @throws IOException
     */
    @Throws(IncompatibleLayoutException::class, IOException::class)
    fun open(path: Path, mode: OpenMode, size: Int, makeDefaultValue: () -> ByteArray): DurablePersistentByteArray {
      val layoutBuilder = CompactLayoutBuilder
      require(size in 1..layoutBuilder.maxStateSize) {
        "size=$size must be in 1..${layoutBuilder.maxStateSize}"
      }
      if (path.exists()) {
        ResilientFileChannel(path, mode.openOptions).use { fileHandler ->
          val layout = layoutBuilder.buildLayout(fileHandler, size)
          val (lastState, lastStateIsInSecondInstance) = recoverLastState(fileHandler, layout, size)
          return DurablePersistentByteArrayImpl(path, mode, layout, lastStateIsInSecondInstance, lastState)
        }
      }
      else {
        when (mode) {
          OpenMode.Read -> return ImmutableVirtualByteArray(path, makeDefaultValue().copyOf())
          OpenMode.ReadWrite -> {
            val defaultValue = makeDefaultValue().copyOf() // make sure we have a unique ref
            require(defaultValue.size == size)
            path.createParentDirectories().createFile()
            createInitialState(path, layoutBuilder, defaultValue)
            val fileHandler = ResilientFileChannel(path, mode.openOptions)
            val layout = layoutBuilder.buildLayout(fileHandler, size)
            return DurablePersistentByteArrayImpl(path, mode, layout, false, defaultValue)
          }
        }
      }
    }

    /**
     * Thrown if [open] was invoked with parameters do not match ones were used
     * to create existing DurablePersistentByteArray state in the file.
     */
    class IncompatibleLayoutException(message: String) : Exception(message)

    // assumes that FS move is atomic
    private fun createInitialState(path: Path, layoutBuilder: CompactLayoutBuilder, defaultValue: ByteArray) {
      val initPath = path.resolveSibling(path.fileName.toString() + ".init")
      if (!initPath.exists()) {
        initPath.createParentDirectories().createFile()
      }
      val size = defaultValue.size
      layoutBuilder.buildLayout(ResilientFileChannel(initPath, OpenMode.ReadWrite.openOptions), size).use {
        val checkSum = defaultValue.getCheckSum()
        it.writeHeader(DurablePersistentByteArrayImpl.LAYOUT_VERSION, size, FIRST_INSTANCE_MARK, checkSum.toLong(), checkSum.toLong())
        it.writeState(defaultValue, false)
        it.writeState(defaultValue, true)
        it.flush()
      }
      initPath.moveTo(path, overwrite = true)
      /*
      TODO it is possible that the containing directory (`initPath.parent`) is not yet flushed, so the `initPath` and consequently `path`
       will be lost in case of a power outage. If we really want to survive even such kind of accidents, then uncommenting the following may
       help, though it's rather a hack (there is still no official fsync for directories in Java)
      try {
        FileChannel.open(initPath.parent, READ).use {
          it.force(true)
        }
      } catch (ignored: Throwable) { }
      */
    }

    /**
     * @return last state bytes and whether it was read from the second instance
     */
    private fun recoverLastState(
      fileHandler: ResilientFileChannel,
      layout: LayoutHandler,
      expectedSize: Int,
    ): Pair<ByteArray, Boolean> {
      if (fileHandler.size() != layout.fullSize.toLong()) {
        throw IncompatibleLayoutException(
          "$this: file length (=${fileHandler.size()}) doesn't match the expected length (${layout.fullSize})")
      }
      val version = layout.readVersion()
      if (version != DurablePersistentByteArrayImpl.LAYOUT_VERSION) {
        throw IncompatibleLayoutException(
          "$this: binary version mismatch (file version=${version} vs current=${DurablePersistentByteArrayImpl.LAYOUT_VERSION})")
      }
      val stateSize = layout.readSize()
      if (stateSize != expectedSize) {
        throw IncompatibleLayoutException("$this: state size mismatch (file state size=$stateSize vs expected=$expectedSize)")
      }

      val useSecondInstance: Boolean = when (val instanceValue = layout.readCurrentInstance()) {
        FIRST_INSTANCE_MARK -> false
        SECOND_INSTANCE_MARK -> true
        else -> {
          LOG.debug { "instance value is broken: $instanceValue" }
          false
        }
      }

      fun tryReadState(useSecondInstance: Boolean): ByteArray? {
        val stateArr = layout.readState(expectedSize, useSecondInstance)
        val stateCheckSum = stateArr.getCheckSum().toLong()
        val checkSum = layout.readCheckSum(useSecondInstance)
        if (stateCheckSum != checkSum) {
          LOG.debug {
            "$this: state (second instance=$useSecondInstance) checksum mismatch: " +
            "state checksum=0x${stateCheckSum.toString(16)} vs stored checksum=0x${checkSum.toString(16)}"
          }
          return null
        }
        return stateArr
      }

      val probableLastState = tryReadState(useSecondInstance)
      if (probableLastState != null) return probableLastState to useSecondInstance
      LOG.debug { "$this: probable state read failed (second instance=$useSecondInstance), trying the other one" }
      val fallbackState = tryReadState(!useSecondInstance)
      check(fallbackState != null) { "$this: state recovery failed: both instances are corrupted " }
      return fallbackState to !useSecondInstance
    }
  }
}

private fun ByteArray.getCheckSum(): Int {
  val crc32 = CRC32()
  crc32.update(this)
  return crc32.value.toInt()
}

private class DurablePersistentByteArrayImpl(
  private val path: Path,
  private val mode: OpenMode,
  private val layoutHandler: LayoutHandler,
  // note: it is sufficient to leave only one variable marked as volatile, but for the sake of easier reasoning, I won't do that
  /**
   * Whether [currentState] is stored in the area for the second instance in the file.
   */
  @Volatile private var storedInSecondInstance: Boolean,
  /**
   * null only in case [DurablePersistentByteArray] is closed
   */
  @Volatile private var currentState: ByteArray?
) : DurablePersistentByteArray {
  /**
   * @return a copy of current ByteArray state
   */
  override fun getLastSnapshot(): ByteArray = currentState?.copyOf() ?: throw IllegalStateException("$this is already closed")

  /**
   * If this method returns without an exception, the modification has been successfully written to the persistent storage, and even
   * in an event of application kill, the following [getLastSnapshot] will produce the updated state.
   * @param modify change the state of the stored ByteArray
   */
  override fun commitChange(modify: (ByteArray) -> Unit): ByteArray = synchronized(this) {
    if (mode == OpenMode.Read) throw IllegalAccessError("$this is opened in read-only mode")
    val newState = currentState!!.copyOf()
    modify(newState)
    val useSecondInstanceForNewState = !storedInSecondInstance

    // we don't really rely on it, but it should increase our chances a bit in case there is a checksum collision
    layoutHandler.writeCheckSum(INVALID_CHECKSUM, useSecondInstanceForNewState)
    layoutHandler.writeState(newState, useSecondInstanceForNewState)
    layoutHandler.flush()

    layoutHandler.writeCheckSum(newState.getCheckSum().toLong(), useSecondInstanceForNewState)
    layoutHandler.writeCurrentInstance(if (useSecondInstanceForNewState) SECOND_INSTANCE_MARK else FIRST_INSTANCE_MARK)
    layoutHandler.flush()

    storedInSecondInstance = useSecondInstanceForNewState
    currentState = newState
    return newState.copyOf()
  }

  override fun close() = synchronized(this) {
    try {
      layoutHandler.close()
    }
    finally {
      currentState = null
    }
  }

  override fun toString(): String {
    return "DurablePersistentByteArray(path=$path, mode=$mode, size=${layoutHandler.stateSize})"
  }


  companion object {
    /**
     * Use this version to check binary compatibility
     */
    const val LAYOUT_VERSION: Int = 1

    private fun FileChannel.readFully(buf: ByteBuffer, offset: Long) {
      position(offset)
      while (buf.hasRemaining()) {
        check(read(buf) >= 0) { "$this: read has failed (EOF)" }
      }
    }

    private fun FileChannel.writeFully(buf: ByteBuffer, offset: Long) {
      position(offset)
      while (buf.hasRemaining()) write(buf)
    }

    private fun FileChannel.readInt(offset: Long): Int = ByteBuffer.allocate(4).run {
      readFully(this, offset)
      getInt(0)
    }

    private fun FileChannel.writeInt(value: Int, offset: Long) = ByteBuffer.allocate(4).run {
      putInt(0, value)
      writeFully(this, offset)
    }

    private fun FileChannel.readLong(offset: Long): Long = ByteBuffer.allocate(8).run {
      readFully(this, offset)
      getLong(0)
    }

    private fun FileChannel.writeLong(value: Long, offset: Long) = ByteBuffer.allocate(8).run {
      putLong(0, value)
      writeFully(this, offset)
    }

    internal interface LayoutHandler : AutoCloseable, Flushable {
      val stateSize: Int
      val fullSize: Int
      val layoutVersion: Int

      fun writeHeader(version: Int, size: Int, currentInstance: Int,
                      checkSumFirst: Long, checkSumSecond: Long)

      fun writeCurrentInstance(currentInstance: Int)
      fun writeCheckSum(checkSum: Long, secondInstance: Boolean)
      fun writeState(state: ByteArray, secondInstance: Boolean)

      fun readVersion(): Int
      fun readSize(): Int
      fun readCurrentInstance(): Int
      fun readCheckSum(secondInstance: Boolean): Long
      fun readState(size: Int, secondInstance: Boolean): ByteArray
    }

    object CompactLayoutBuilder {
      const val HEADER_OFFSET = 0
      const val VERSION_OFFSET = HEADER_OFFSET // 0, int
      const val SIZE_OFFSET = VERSION_OFFSET + 4 // 4, int
      const val CURRENT_INSTANCE_OFFSET = SIZE_OFFSET + 4 // 8, int
      const val RESERVED_OFFSET__ = CURRENT_INSTANCE_OFFSET + 4 // 12, int
      const val CHECKSUM_FIRST_OFFSET = RESERVED_OFFSET__ + 4 // 16, long
      const val CHECKSUM_SECOND_OFFSET = CHECKSUM_FIRST_OFFSET + 8 // 24, long

      // 32..63 is reserved
      const val HEADER_SIZE = 64

      val maxStateSize get() = (Int.MAX_VALUE - HEADER_SIZE) / 2
      fun getFullSize(stateSize: Int): Int = HEADER_SIZE + 2 * stateSize

      fun buildLayout(fileHandler: FileChannel, stateSize: Int): LayoutHandler = object : LayoutHandler {
        override val stateSize: Int = stateSize
        override val fullSize: Int = getFullSize(stateSize)
        override val layoutVersion: Int get() = LAYOUT_VERSION

        override fun writeHeader(version: Int,
                                 size: Int,
                                 currentInstance: Int,
                                 checkSumFirst: Long,
                                 checkSumSecond: Long) {
          val buf = ByteBuffer.allocate(HEADER_SIZE)
          buf.putInt(VERSION_OFFSET, version)
          buf.putInt(SIZE_OFFSET, size)
          buf.putInt(CURRENT_INSTANCE_OFFSET, currentInstance)
          buf.putLong(CHECKSUM_FIRST_OFFSET, checkSumFirst)
          buf.putLong(CHECKSUM_SECOND_OFFSET, checkSumSecond)
          fileHandler.writeFully(buf, HEADER_OFFSET.toLong())
        }

        override fun writeCurrentInstance(currentInstance: Int) =
          fileHandler.writeInt(currentInstance, CURRENT_INSTANCE_OFFSET.toLong())

        override fun writeCheckSum(checkSum: Long, secondInstance: Boolean) =
          fileHandler.writeLong(checkSum, (if (secondInstance) CHECKSUM_SECOND_OFFSET else CHECKSUM_FIRST_OFFSET).toLong())

        override fun writeState(state: ByteArray, secondInstance: Boolean) =
          fileHandler.writeFully(ByteBuffer.wrap(state), HEADER_SIZE.toLong() + if (secondInstance) state.size else 0)

        override fun readVersion(): Int = fileHandler.readInt(VERSION_OFFSET.toLong())
        override fun readSize(): Int = fileHandler.readInt(SIZE_OFFSET.toLong())
        override fun readCurrentInstance(): Int = fileHandler.readInt(CURRENT_INSTANCE_OFFSET.toLong())
        override fun readCheckSum(secondInstance: Boolean): Long =
          fileHandler.readLong((if (secondInstance) CHECKSUM_SECOND_OFFSET else CHECKSUM_FIRST_OFFSET).toLong())

        override fun readState(size: Int, secondInstance: Boolean): ByteArray = ByteArray(size).run {
          fileHandler.readFully(ByteBuffer.wrap(this), HEADER_SIZE.toLong() + if (secondInstance) size else 0)
          this
        }

        override fun flush() {
          fileHandler.force(true)
        }

        override fun close() {
          fileHandler.close()
        }
      }
    }
  }
}

private class ImmutableVirtualByteArray(val path: Path, val data: ByteArray) : DurablePersistentByteArray {
  override fun getLastSnapshot(): ByteArray {
    return data
  }

  override fun commitChange(modify: (ByteArray) -> Unit): ByteArray {
    throw IllegalAccessError("$this is opened in read-only mode")
  }

  override fun close() {
    // no op
  }

  override fun toString(): String {
    return "ImmutableVirtualByteArray(path=$path, mode=${OpenMode.Read}, size=${data.size})"
  }
}
