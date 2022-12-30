// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.Unsafe
import com.intellij.util.io.ByteBufferUtil
import com.intellij.util.io.DirectByteBufferAllocator
import com.intellij.util.io.ResizeableMappedFile
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ReadableByteChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.withLock
import kotlin.math.max

@ApiStatus.Internal
internal class PersistentFSLockFreeRecordsStorage @Throws(IOException::class) constructor(private val file: ResizeableMappedFile): PersistentFSRecordsStorage() {
  private val metadataReadLock = file.storageLockContext.readLock()
  private val metadataWriteLock = file.storageLockContext.writeLock()

  private val _globalModCount: AtomicInteger
  private val recordCount: AtomicInteger

  init {
    _globalModCount = AtomicInteger(readGlobalModCount())
    recordCount = AtomicInteger((length() / RECORD_SIZE).toInt())

    val corruptedRecordIds = checkCorruptedRecords()
    if (corruptedRecordIds.isNotEmpty()) {
      thisLogger().error("Storage $file corrupted, corrupted ids: ${corruptedRecordIds.contentToString()}")
      throw IOException("Storage $file corrupted") // todo replace with granular rebuild
    }
  }

  override fun getGlobalModCount(): Int = _globalModCount.get()

  @Throws(IOException::class)
  private fun readGlobalModCount(): Int = metadataReadLock.withLock {
    file.getInt(PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET.toLong())
  }

  @Throws(IOException::class)
  private fun saveGlobalModCount() = metadataWriteLock.withLock {
    file.putInt(PersistentFSHeaders.HEADER_GLOBAL_MOD_COUNT_OFFSET.toLong(), globalModCount)
  }

  private fun incGlobalModCount(): Int {
    return _globalModCount.incrementAndGet()
  }

  @Throws(IOException::class)
  override fun getTimestamp(): Long = metadataReadLock.withLock {
    file.getLong(PersistentFSHeaders.HEADER_TIMESTAMP_OFFSET.toLong())
  }

  @Throws(IOException::class)
  override fun setVersion(version: Int) = metadataWriteLock.withLock {
    file.putInt(PersistentFSHeaders.HEADER_VERSION_OFFSET.toLong(), version)
    file.putLong(PersistentFSHeaders.HEADER_TIMESTAMP_OFFSET.toLong(), System.currentTimeMillis())
  }

  @Throws(IOException::class)
  override fun getVersion(): Int = metadataReadLock.withLock {
    file.getInt(PersistentFSHeaders.HEADER_VERSION_OFFSET.toLong())
  }

  @Throws(IOException::class)
  override fun getConnectionStatus() = metadataReadLock.withLock {
    file.getInt(PersistentFSHeaders.HEADER_CONNECTION_STATUS_OFFSET.toLong())
  }

  @Throws(IOException::class)
  override fun setConnectionStatus(code: Int) = metadataWriteLock.withLock {
    file.putInt(PersistentFSHeaders.HEADER_CONNECTION_STATUS_OFFSET.toLong(), code)
  }

  @Throws(IOException::class)
  override fun getNameId(id: Int): Int = acquireRecord(id, AccessType.READ) {
    nameId()
  }

  @Throws(IOException::class)
  override fun setNameId(id: Int, nameId: Int) = acquireRecord(id, AccessType.WRITE) {
    nameId(nameId)
  }

  @Throws(IOException::class)
  override fun getParent(id: Int): Int = acquireRecord(id, AccessType.READ) {
    parent()
  }

  @Throws(IOException::class)
  override fun setParent(id: Int, parent: Int) = acquireRecord(id, AccessType.WRITE) {
    parent(parent)
  }

  @Throws(IOException::class)
  override fun getModCount(id: Int): Int = acquireRecord(id, AccessType.READ) {
    modCount()
  }

  @Throws(IOException::class)
  override fun getFlags(id: Int): @PersistentFS.Attributes Int = acquireRecord(id, AccessType.READ) {
    flags()
  }

  @Throws(IOException::class)
  override fun setFlags(id: Int, flags: @PersistentFS.Attributes Int): Boolean = acquireRecord(id, AccessType.WRITE_AND_INCREMENT_MOD_COUNTER) {
    if(flags!=flags()) {
      flags(flags)
      return true
    }
    return false
  }

  @Throws(IOException::class)
  override fun markRecordAsModified(id: Int) = acquireRecord(id, AccessType.WRITE_AND_INCREMENT_MOD_COUNTER) {
    //do nothing but increment modCount
  }

  @Throws(IOException::class)
  override fun getContentRecordId(id: Int): Int = acquireRecord(id, AccessType.READ) {
    content()
  }

  @Throws(IOException::class)
  override fun setContentRecordId(id: Int, value: Int): Boolean = acquireRecord(id, AccessType.WRITE) {
    if (content() != value) {
      content(value)
      return true
    }
    else {
      return false
    }
  }

  @Throws(IOException::class)
  override fun getAttributeRecordId(id: Int): Int = acquireRecord(id, AccessType.READ) {
    attrs()
  }

  @Throws(IOException::class)
  override fun setAttributeRecordId(id: Int, value: Int) = acquireRecord(id, AccessType.WRITE) {
    attrs(value)
  }

  @Throws(IOException::class)
  override fun getTimestamp(id: Int): Long = acquireRecord(id, AccessType.READ) {
    timeStamp()
  }

  @Throws(IOException::class)
  override fun putTimestamp(id: Int, value: Long): Boolean = acquireRecord(id, AccessType.WRITE_AND_INCREMENT_MOD_COUNTER) {
    if(timeStamp()!=value) {
      timeStamp(value)
      return true
    }
    return false
  }

  @Throws(IOException::class)
  override fun getLength(id: Int): Long = acquireRecord(id, AccessType.READ) {
    length()
  }

  @Throws(IOException::class)
  override fun putLength(id: Int, value: Long): Boolean = acquireRecord(id, AccessType.WRITE_AND_INCREMENT_MOD_COUNTER) {
    if(value != length()) {
      length(value)
      return true
    }else{
      return false
    }
  }

  @Throws(IOException::class)
  override fun cleanRecord(id: Int) = metadataWriteLock.withLock {
    recordCount.updateAndGet { operand: Int -> max(id + 1, operand) }
    file.put(id.toLong() * RECORD_SIZE, ZEROES, 0, RECORD_SIZE)
  }

  override fun allocateRecord(): Int = recordCount.getAndIncrement()

  @Throws(IOException::class)
  override fun fillRecord(id: Int,
                          timestamp: Long,
                          length: Long,
                          flags: Int,
                          nameId: Int,
                          parentId: Int,
                          overwriteAttrRef: Boolean) = acquireRecord(id, AccessType.WRITE) {
    setup(parentId, nameId, flags, 0, 0, timestamp, length, overwriteAttrRef)
  }

  override fun length(): Long = metadataReadLock.withLock {
    file.length()
  }

  @Throws(IOException::class)
  override fun close() = metadataWriteLock.withLock {
    saveGlobalModCount()
    file.close()
  }

  @Throws(IOException::class)
  override fun force() = metadataWriteLock.withLock {
    saveGlobalModCount()
    file.force()
  }

  override fun isDirty(): Boolean = file.isDirty

  @Throws(IOException::class)
  override fun processAllRecords(operator: FsRecordProcessor) = this.metadataReadLock.withLock {
    // skip header
    file.force()
    // skip header
    file.readChannel { ch: ReadableByteChannel ->
      val buffer = ByteBuffer.allocateDirect(RECORD_SIZE)
      try {
        var id = 0
        while (ch.read(buffer) >= RECORD_SIZE) {
          if (id != 0) {
            buffer.position(0)
            val record = LockFreeRecord(buffer)

            operator.process(id, record.nameId(), record.flags(), record.parent(), !record.isSaved())
          }
          else {
            // metadata record
          }
          id++
          buffer.rewind()
        }
      }
      catch (ignore: IOException) {
      }
      true
    }
  }

  private fun checkCorruptedRecords(): IntArray {
    val corruptedIds = IntArrayList()
    processAllRecords { fileId, _, _, _, corrupted ->
      if (corrupted) {
        corruptedIds.add(fileId)
      }
    }
    if (corruptedIds.isEmpty) return ArrayUtil.EMPTY_INT_ARRAY

    // root parent == 0
    val liveRecordsToParent = Int2IntOpenHashMap()
    val orderedLiveKeys = IntArrayList()
    processAllRecords { fileId, _, _, parentId, corrupted ->
      if (!corrupted) {
        liveRecordsToParent[fileId] = parentId
        orderedLiveKeys.add(fileId)
      }
    }

    val iter = orderedLiveKeys.intIterator()
    while (iter.hasNext()) {
      val id = iter.nextInt()

      val path = IntArrayList()
      path.add(id)

      var parent = liveRecordsToParent[id]

      while (parent != 0) {
        path.add(parent)
        if (corruptedIds.contains(parent)) {
          corruptedIds.addAll(path)
          break
        }

        parent = liveRecordsToParent[parent]
      }
    }

    return corruptedIds.toIntArray()
  }

  private enum class AccessType {
    READ,
    WRITE,
    WRITE_AND_INCREMENT_MOD_COUNTER
  }

  private inline fun <V> acquireRecord(id: Int, access: AccessType, action: LockFreeRecord.() -> V): V {
    val recordOffset = id.toLong() * RECORD_SIZE
    val storagePage = file.pagedFileStorage.getByteBuffer(recordOffset, false)
    val inPageOffset = file.pagedFileStorage.getOffsetInPage(recordOffset)

    try {
      if (access != AccessType.READ) {
        storagePage.markDirty()
      }

      val buffer = storagePage.buffer
      val recordBuffer = buffer.duplicate().order(buffer.order()).limit(inPageOffset + RECORD_SIZE).position(inPageOffset).mark().slice()
      return action(LockFreeRecord(recordBuffer))
    }
    finally {
      if (access != AccessType.READ) {
        if (access == AccessType.WRITE_AND_INCREMENT_MOD_COUNTER) {
          incGlobalModCount()
        }
        storagePage.fileSizeMayChanged(inPageOffset + RECORD_SIZE)
        file.logicalSize = max(file.logicalSize, recordOffset + RECORD_SIZE)
      }
      storagePage.unlock()
    }
  }

  companion object {
    private const val PARENT_OFFSET = 0
    private const val PARENT_SIZE = 4
    private const val NAME_OFFSET = PARENT_OFFSET + PARENT_SIZE
    private const val NAME_SIZE = 4
    private const val FLAGS_OFFSET = NAME_OFFSET + NAME_SIZE
    private const val FLAGS_SIZE = 4
    private const val ATTR_REF_OFFSET = FLAGS_OFFSET + FLAGS_SIZE
    private const val ATTR_REF_SIZE = 4
    private const val CONTENT_OFFSET = ATTR_REF_OFFSET + ATTR_REF_SIZE
    private const val CONTENT_SIZE = 4
    private const val TIMESTAMP_OFFSET = CONTENT_OFFSET + CONTENT_SIZE
    private const val TIMESTAMP_SIZE = 8
    private const val LENGTH_OFFSET = TIMESTAMP_OFFSET + TIMESTAMP_SIZE
    private const val LENGTH_SIZE = 8
    private const val MOD_COUNT_SIZE = 4
    private const val MOD_COUNT_PRE_OFFSET = LENGTH_OFFSET + LENGTH_SIZE
    private const val MOD_COUNT_AFTER_OFFSET = MOD_COUNT_PRE_OFFSET + MOD_COUNT_SIZE

    const val RECORD_SIZE = MOD_COUNT_AFTER_OFFSET + MOD_COUNT_SIZE
    private val ZEROES = ByteArray(RECORD_SIZE)
  }

  @JvmInline
  private value class LockFreeRecord(val data: ByteBuffer) {
    init {
      val bufferSize = data.limit() - data.position()
      assert(bufferSize == RECORD_SIZE) {
        "buffer size = $bufferSize"
      }
    }

    fun setup(parent: Int, nameId: Int, flags: Int, attrs: Int, content: Int, timestamp: Long, length: Long, overwriteMissed: Boolean) = update {
      putInt(PARENT_OFFSET, parent)
      putInt(NAME_OFFSET, nameId)
      putInt(FLAGS_OFFSET, flags)
      if (overwriteMissed) {
        putInt(ATTR_REF_OFFSET, attrs)
      }
      putInt(CONTENT_OFFSET, content)
      putLong(TIMESTAMP_OFFSET, timestamp)
      putLong(LENGTH_OFFSET, length)
      //TODO RC: probably, better to increment modCount still
    }

    fun parent(): Int = read { getInt(PARENT_OFFSET) }
    fun parent(value: Int) = update { putInt(PARENT_OFFSET, value) }

    fun nameId(): Int = read { getInt(NAME_OFFSET) }
    fun nameId(value: Int) = update { putInt(NAME_OFFSET, value) }

    fun flags(): Int = read { getInt(FLAGS_OFFSET) }
    fun flags(value: Int) = update { putInt(FLAGS_OFFSET, value) }

    fun attrs(): Int = read { getInt(ATTR_REF_OFFSET) }
    fun attrs(value: Int) = update { putInt(ATTR_REF_OFFSET, value) }

    fun content(): Int = read { getInt(CONTENT_OFFSET) }
    fun content(value: Int) = update { putInt(CONTENT_OFFSET, value) }

    fun timeStamp(): Long = read { getLong(TIMESTAMP_OFFSET) }
    fun timeStamp(value: Long) = update { putLong(TIMESTAMP_OFFSET, value) }

    fun length(): Long = read { getLong(LENGTH_OFFSET) }
    fun length(value: Long) = update { putLong(LENGTH_OFFSET, value) }

    fun modCount() = modCountPre()
    fun incModCount() = update { /*No Op*/ }

    fun modCountPre(): Int = data.getInt(MOD_COUNT_PRE_OFFSET)
    fun modCountAfter(): Int = data.getInt(MOD_COUNT_AFTER_OFFSET)

    fun isSaved() = modCountPre() ==  modCountAfter()

    inline fun update(updater: ByteBuffer.() -> Unit) {
      val writeBuffer = DirectByteBufferAllocator.ALLOCATOR.allocate(RECORD_SIZE)
      writeBuffer.order(data.order())

      try {
        while (true) {
          val readModCount = readTo(writeBuffer)

          updater(writeBuffer)
          writeBuffer.rewind()

          if (tryWrite(writeBuffer, readModCount)) {
            return
          }
        }
      }
      finally {
        DirectByteBufferAllocator.ALLOCATOR.release(writeBuffer)
      }
    }

    fun readTo(copy: ByteBuffer): Int {
      while (true) {
        val modCountPre = modCountPre()

        copy.rewind()
        copy.put(data.duplicate().order(data.order()))

        if (modCountAfter() == modCountPre) {
          return modCountPre
        }
      }
    }

    private inline fun <V> read(eval: ByteBuffer.() -> V): V {
      var attempt = 0
      while (true) {
        val modCountPre = modCountPre()

        val result = eval(data)

        val modCountAfter = modCountAfter()
        if (modCountAfter == modCountPre) {
          return result
        }
      }
    }

    fun tryWrite(buffer: ByteBuffer, expectedModCounter: Int): Boolean {
      val newModCounter = expectedModCounter + 1
      if (data.compareAndSwapInt(MOD_COUNT_PRE_OFFSET, expectedModCounter, newModCounter)) {
        data.duplicate().order(data.order()).put(buffer.duplicate().order(data.order()).limit(RECORD_SIZE - 2 * MOD_COUNT_SIZE))
        data.putInt(MOD_COUNT_AFTER_OFFSET, newModCounter)
        return true
      }
      return false
    }

    companion object {
      private fun ByteBuffer.compareAndSwapInt(offset: Int, expected: Int, new: Int): Boolean {
        val address = ByteBufferUtil.getAddress(this)
        val flipOrder = order() == ByteOrder.BIG_ENDIAN // TODO revise
        val _expected = if (flipOrder) Integer.reverseBytes(expected) else expected
        val _new = if (flipOrder) Integer.reverseBytes(new) else new
        return Unsafe.compareAndSwapInt(null, address + offset, _expected, _new)
      }
    }
  }
}