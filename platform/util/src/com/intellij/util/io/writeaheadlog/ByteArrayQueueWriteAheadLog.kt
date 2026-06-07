// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.writeaheadlog

import com.intellij.util.io.blobstorage.ByteBufferWriter
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import kotlin.io.path.absolute

/** Primitive (prototype) implementation: keeps writes in a bounded queue */
@ApiStatus.Internal
class ByteArrayQueueWriteAheadLog(
  private val channelWriter: WriteAheadLog.ToFileWriter,
) : WriteAheadLog {

  private val pendingRecords = ArrayBlockingQueue<Record>(4 shl 10)

  //@GuardedBy(this)
  private val pendingRecordsByPath = HashMap<Path, Int>()

  override fun openFor(path: Path): WriteAheadLog.PerFileWriter {
    return PerFileWriterImpl(path.absolute())
  }

  override fun hasUnfinished(): Boolean = pendingRecords.isNotEmpty()

  @Synchronized
  override fun flush(): Int {
    var flushedEntries = 0
    while (!pendingRecords.isEmpty()) {
      val record = pendingRecords.poll() ?: return flushedEntries
      channelWriter.write(record.path, record.offsetInFile, ByteBuffer.wrap(record.data))
      flushedEntries++
      forget(record.path)
    }
    return flushedEntries
  }

  override fun close() {
    flush()
  }

  @Synchronized
  private fun append(path: Path, fileOffset: Long, writer: ByteBufferWriter, recordSize: Int) {
    require(fileOffset >= 0) { "fileOffset must be non-negative: $fileOffset" }
    require(recordSize >= 0) { "recordSize must be non-negative: $recordSize" }

    val recordData = ByteArray(recordSize)
    val dataAsByteBuffer = ByteBuffer.wrap(recordData)
    val written = writer.write(dataAsByteBuffer)
    assert(written == dataAsByteBuffer) { "writer must return the same buffer" }

    pendingRecords.add(Record(path, fileOffset, recordData))
    remember(path)
  }

  //@GuardedBy(this)
  private fun remember(path: Path) {
    pendingRecordsByPath[path] = pendingRecordsByPath.getOrDefault(path, 0) + 1
  }

  //@GuardedBy(this)
  private fun forget(path: Path) {
    val countBefore = pendingRecordsByPath.getValue(path)
    if (countBefore == 1) {
      pendingRecordsByPath.remove(path)
    }
    else {
      pendingRecordsByPath[path] = countBefore - 1
    }
  }

  private inner class PerFileWriterImpl(private val path: Path) : WriteAheadLog.PerFileWriter {
    override fun write(fileOffset: Long, writer: ByteBufferWriter, recordSize: Int) {
      append(path, fileOffset, writer, recordSize)
    }

    override fun applyUnfinished(offsetInFile: Long, length: Int, targetBuffer: ByteBuffer, offsetInBuffer: Int) {
      this@ByteArrayQueueWriteAheadLog.applyUnfinished(path, offsetInFile, length, targetBuffer, offsetInBuffer)
    }

    override fun hasUnfinished(): Boolean {
      return this@ByteArrayQueueWriteAheadLog.hasUnfinished(path)
    }

    override fun maxUnfinishedWriteOffset(): Long {
      return this@ByteArrayQueueWriteAheadLog.maxUnfinishedWriteEndOffset(path)
    }

    override fun flush(): Int {
      return this@ByteArrayQueueWriteAheadLog.flush()
    }
  }

  @Synchronized
  private fun maxUnfinishedWriteEndOffset(path: Path): Long {
    if (!hasUnfinished(path)) {
      return -1
    }

    var maxEndOffset = -1L
    for (record in pendingRecords) {
      if (record.path == path) {
        maxEndOffset = maxOf(maxEndOffset, record.offsetInFile + record.data.size)
      }
    }
    return maxEndOffset
  }

  @Synchronized
  private fun applyUnfinished(path: Path, offsetInFile: Long, length: Int, buffer: ByteBuffer, offsetInBuffer: Int) {
    if (!hasUnfinished(path)) {
      return
    }
    for (record in pendingRecords) {
      if (record.path == path) {
        applyToBuffer(record, offsetInFile, length, buffer, offsetInBuffer)
      }
    }
  }

  @Synchronized
  private fun hasUnfinished(path: Path): Boolean = pendingRecordsByPath.containsKey(path)

  private companion object {
    private fun applyToBuffer(record: Record, offsetInFile: Long, length: Int, targetBuffer: ByteBuffer, offsetInBuffer: Int) {
      val requestedEnd = offsetInFile + length
      val recordStart = record.offsetInFile
      val recordEnd = record.offsetInFile + record.data.size
      val overlapStart = maxOf(offsetInFile, recordStart)
      val overlapEnd = minOf(requestedEnd, recordEnd)
      if (overlapStart >= overlapEnd) {
        return
      }

      val source = ByteBuffer.wrap(record.data)
      source.position((overlapStart - recordStart).toInt())
      source.limit((overlapEnd - recordStart).toInt())

      val target = targetBuffer.duplicate()
      target.position(offsetInBuffer + (overlapStart - offsetInFile).toInt())
      target.put(source)
    }
  }

  private class Record(val path: Path, val offsetInFile: Long, val data: ByteArray)
}
