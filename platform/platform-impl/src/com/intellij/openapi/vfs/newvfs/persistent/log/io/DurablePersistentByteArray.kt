// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.io

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.newvfs.persistent.log.io.DurablePersistentByteArray.Companion.OpenMode
import com.intellij.util.io.ResilientFileChannel
import com.intellij.util.io.createFile
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.util.*
import java.util.zip.CRC32
import java.util.zip.Checksum
import kotlin.io.path.exists
import kotlin.io.path.moveTo

/**
 * [DurablePersistentByteArray] provides thread-safe means to store a ByteArray in a persistent storage with a durability guarantee.
 * E.g. if the application gets killed while the [commitChange] was in progress, on the next start the application will see
 * the last consistent state that was observable before the commit attempt.
 *
 * Actual amount of storage space it uses is `2*size + O(1)`.
 *
 * This code is written in assumption that parts of the file -- not blocks, but ranges of bytes, -- that are not touched by write operations,
 * would not change in any kind of catastrophe which the filesystem can survive. This may be a false assumption.
 *
 * There should not exist more than one instance on the same path.
 *
 * @param path where to store the data
 * @param mode If [mode] is [OpenMode.Read] and file at [path] does not yet exist, it won't be created
 * @param size size of the ByteArray to be stored and read. It is perhaps better to use multiples of 8 for better aligning.
 * @param makeDefaultValue will be used to generate the initial value, if file at [path] does not exist
 */
class DurablePersistentByteArray(
  private val path: Path,
  private val mode: OpenMode,
  val size: Int,
  makeDefaultValue: () -> ByteArray
) : AutoCloseable {
  private val layout: Layout = CompactLayout

  /**
   * null in case the file does not exist and mode is [OpenMode.Read]
   */
  private val fileHandler: ResilientFileChannel?

  // note: it is sufficient to leave only one variable marked as volatile, but for the sake of easier reasoning, I won't do that
  /**
   * Whether [currentState] is stored in the area for the second instance in the file.
   */
  @Volatile
  private var storedInSecondInstance: Boolean

  /**
   * null only in case [DurablePersistentByteArray] is closed
   */
  @Volatile
  private var currentState: ByteArray?

  init {
    require(size in 1..layout.maxStateSize) {
      "size=$size must be in 1..${layout.maxStateSize}"
    }
    if (path.exists()) {
      fileHandler = ResilientFileChannel(path, mode.openOptions)
      try {
        val (lastState, lastStateIsInSecondInstance) = recoverLastState()
        storedInSecondInstance = lastStateIsInSecondInstance
        currentState = lastState
      }
      catch (e: Throwable) {
        try {
          fileHandler.close()
        } catch (closeE: Throwable) {
          e.addSuppressed(closeE)
        }
        throw e
      }
    }
    else {
      val defaultValue = makeDefaultValue().copyOf() // make sure we have a unique ref
      require(defaultValue.size == size)

      fileHandler = when (mode) {
        OpenMode.Read -> null
        OpenMode.ReadWrite -> {
          path.parent?.let { Files.createDirectories(it) }
          createInitialState(defaultValue)
          ResilientFileChannel(path, mode.openOptions)
        }
      }

      storedInSecondInstance = false
      currentState = defaultValue
    }
  }

  // assumes that FS move is atomic
  private fun createInitialState(defaultValue: ByteArray) {
    val initPath = path.resolveSibling(path.fileName.toString() + ".init")
    if (!initPath.exists()) {
      initPath.createFile()
    }
    ResilientFileChannel(initPath, OpenMode.ReadWrite.openOptions).use {
      it.truncate(layout.getFullSize(size).toLong())
      val checkSum = defaultValue.getCheckSum()
      layout.writeHeader(it, LAYOUT_VERSION, size, FIRST_INSTANCE_MARK, checkSum.toLong(), checkSum.toLong())
      layout.writeState(it, defaultValue, false)
      layout.writeState(it, defaultValue, true)
      it.force(false)
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
   * called only from ctor
   * @return last state bytes and whether it was read from the second instance
   */
  private fun recoverLastState(): Pair<ByteArray, Boolean> {
    fileHandler!!
    check(fileHandler.size() == layout.getFullSize(size).toLong()) {
      "$this: file length (=${fileHandler.size()}) doesn't match the expected length (${layout.getFullSize(size)})"
    }

    val version = layout.readVersion(fileHandler)
    check(version == LAYOUT_VERSION) {
      "$this: binary version mismatch (file version=${version} vs current=${LAYOUT_VERSION})"
    }
    val stateSize = layout.readSize(fileHandler)
    check(stateSize == size) {
      "$this: state size mismatch (file state size=$stateSize vs current=$size)"
    }

    val useSecondInstance: Boolean = when (val instanceValue = layout.readCurrentInstance(fileHandler)) {
      FIRST_INSTANCE_MARK -> false
      SECOND_INSTANCE_MARK -> true
      else -> {
        LOG.debug { "instance value is broken: $instanceValue" }
        false
      }
    }

    fun tryReadState(useSecondInstance: Boolean): ByteArray? {
      val stateArr = layout.readState(fileHandler, size, useSecondInstance)
      val stateCheckSum = stateArr.getCheckSum().toLong()
      val checkSum = layout.readCheckSum(fileHandler, useSecondInstance)
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

  /**
   * @return a copy of current ByteArray state
   */
  fun getLastSnapshot(): ByteArray = currentState?.copyOf() ?: throw IllegalStateException("$this is already closed")

  /**
   * If this method returns without an exception, the modification has been successfully written to the persistent storage, and even
   * in an event of application kill, the following [getLastSnapshot] will produce the updated state.
   * @param modify change the state of the stored ByteArray
   */
  @Throws(IOException::class)
  @Synchronized
  fun commitChange(modify: (ByteArray) -> Unit): ByteArray {
    check(mode == OpenMode.ReadWrite) { "$this is opened in read-only mode" }
    fileHandler!!
    val newState = currentState!!.copyOf()
    modify(newState)
    val useSecondInstanceForNewState = !storedInSecondInstance

    // we don't really rely on it, but it should increase our chances a bit in case there is a checksum collision
    layout.writeCheckSum(fileHandler, INVALID_CHECKSUM, useSecondInstanceForNewState)
    layout.writeState(fileHandler, newState, useSecondInstanceForNewState)
    fileHandler.force(false)

    layout.writeCheckSum(fileHandler, newState.getCheckSum().toLong(), useSecondInstanceForNewState)
    layout.writeCurrentInstance(fileHandler, if (useSecondInstanceForNewState) SECOND_INSTANCE_MARK else FIRST_INSTANCE_MARK)
    fileHandler.force(false)

    storedInSecondInstance = useSecondInstanceForNewState
    currentState = newState
    return newState.copyOf()
  }

  @Throws(IOException::class)
  @Synchronized
  override fun close() {
    if (fileHandler != null) {
      assert(currentState != null)
      try {
        fileHandler.close()
      }
      finally {
        currentState = null
      }
    }
  }

  override fun toString(): String {
    return "DurablePersistentByteArray(path=$path, mode=$mode, size=$size)"
  }


  companion object {
    private val LOG = logger<DurablePersistentByteArray>()

    /**
     * Use this version to check binary compatibility
     */
    const val LAYOUT_VERSION: Int = 1

    private val checkSum: Checksum = CRC32()
    private const val INVALID_CHECKSUM: Long = 0x239L shl 48 // special value

    private const val FIRST_INSTANCE_MARK: Int = 0x1a1a1a1a
    private const val SECOND_INSTANCE_MARK: Int = 0x2b2b2b2

    private fun ByteArray.getCheckSum(): Int {
      checkSum.reset()
      checkSum.update(this)
      return checkSum.value.toInt()
    }

    private fun ResilientFileChannel.readFully(buf: ByteBuffer, offset: Long) {
      position(offset)
      while (buf.hasRemaining()) {
        check(read(buf) >= 0) { "$this: read has failed (EOF)" }
      }
    }

    private fun ResilientFileChannel.writeFully(buf: ByteBuffer, offset: Long) {
      position(offset)
      while (buf.hasRemaining()) write(buf)
    }

    private fun ResilientFileChannel.readInt(offset: Long): Int = ByteBuffer.allocate(4).run {
      readFully(this, offset)
      getInt(0)
    }

    private fun ResilientFileChannel.writeInt(value: Int, offset: Long) = ByteBuffer.allocate(4).run {
      putInt(0, value)
      writeFully(this, offset)
    }

    private fun ResilientFileChannel.readLong(offset: Long): Long = ByteBuffer.allocate(8).run {
      readFully(this, offset)
      getLong(0)
    }

    private fun ResilientFileChannel.writeLong(value: Long, offset: Long) = ByteBuffer.allocate(8).run {
      putLong(0, value)
      writeFully(this, offset)
    }

    enum class LayoutType(internal val layout: Layout) {
      Compact(CompactLayout)
    }

    internal interface Layout {
      val maxStateSize: Int

      fun getFullSize(stateSize: Int): Int

      fun writeHeader(fileHandler: ResilientFileChannel,
                      version: Int, size: Int, currentInstance: Int,
                      checkSumFirst: Long, checkSumSecond: Long)

      fun writeCurrentInstance(fileHandler: ResilientFileChannel, currentInstance: Int)
      fun writeCheckSum(fileHandler: ResilientFileChannel, checkSum: Long, secondInstance: Boolean)
      fun writeState(fileHandler: ResilientFileChannel, state: ByteArray, secondInstance: Boolean)

      fun readVersion(fileHandler: ResilientFileChannel): Int
      fun readSize(fileHandler: ResilientFileChannel): Int
      fun readCurrentInstance(fileHandler: ResilientFileChannel): Int
      fun readCheckSum(fileHandler: ResilientFileChannel, secondInstance: Boolean): Long
      fun readState(fileHandler: ResilientFileChannel, size: Int, secondInstance: Boolean): ByteArray
    }

    private object CompactLayout : Layout {
      const val HEADER_OFFSET = 0
      const val VERSION_OFFSET = HEADER_OFFSET // 0, int
      const val SIZE_OFFSET = VERSION_OFFSET + 4 // 4, int
      const val CURRENT_INSTANCE_OFFSET = SIZE_OFFSET + 4 // 8, int
      const val RESERVED_OFFSET__ = CURRENT_INSTANCE_OFFSET + 4 // 12, int
      const val CHECKSUM_FIRST_OFFSET = RESERVED_OFFSET__ + 4 // 16, long
      const val CHECKSUM_SECOND_OFFSET = CHECKSUM_FIRST_OFFSET + 8 // 24, long
      // 32..63 is reserved
      const val HEADER_SIZE = 64

      override val maxStateSize = (Int.MAX_VALUE - HEADER_SIZE) / 2
      override fun getFullSize(stateSize: Int): Int = HEADER_SIZE + 2 * stateSize

      override fun writeHeader(fileHandler: ResilientFileChannel,
                               version: Int,
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

      override fun writeCurrentInstance(fileHandler: ResilientFileChannel, currentInstance: Int) =
        fileHandler.writeInt(currentInstance, CURRENT_INSTANCE_OFFSET.toLong())

      override fun writeCheckSum(fileHandler: ResilientFileChannel, checkSum: Long, secondInstance: Boolean) =
        fileHandler.writeLong(checkSum, (if (secondInstance) CHECKSUM_SECOND_OFFSET else CHECKSUM_FIRST_OFFSET).toLong())

      override fun writeState(fileHandler: ResilientFileChannel, state: ByteArray, secondInstance: Boolean) =
        fileHandler.writeFully(ByteBuffer.wrap(state), HEADER_SIZE.toLong() + if (secondInstance) state.size else 0)

      override fun readVersion(fileHandler: ResilientFileChannel): Int = fileHandler.readInt(VERSION_OFFSET.toLong())
      override fun readSize(fileHandler: ResilientFileChannel): Int = fileHandler.readInt(SIZE_OFFSET.toLong())
      override fun readCurrentInstance(fileHandler: ResilientFileChannel): Int = fileHandler.readInt(CURRENT_INSTANCE_OFFSET.toLong())
      override fun readCheckSum(fileHandler: ResilientFileChannel, secondInstance: Boolean): Long =
        fileHandler.readLong((if (secondInstance) CHECKSUM_SECOND_OFFSET else CHECKSUM_FIRST_OFFSET).toLong())

      override fun readState(fileHandler: ResilientFileChannel, size: Int, secondInstance: Boolean): ByteArray = ByteArray(size).run {
        fileHandler.readFully(ByteBuffer.wrap(this), HEADER_SIZE.toLong() + if (secondInstance) size else 0)
        this
      }
    }

    enum class OpenMode(val openOptions: Set<OpenOption>) {
      Read(EnumSet.of(READ)),
      ReadWrite(EnumSet.of(READ, WRITE))
    }
  }
}