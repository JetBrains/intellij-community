// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.circular

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.util.io.storages.CommonKeyDescriptors
import com.intellij.platform.util.io.storages.DataExternalizerEx
import com.intellij.platform.util.io.storages.KeyDescriptorEx
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory
import com.intellij.platform.util.io.storages.appendonlylog.InvalidRecordIdException
import com.intellij.platform.util.io.storages.circular.CircularBytesBuffer.QueueFullException
import com.intellij.platform.util.io.storages.enumerator.DurableEnumerator
import com.intellij.platform.util.io.storages.enumerator.DurableEnumeratorFactory
import com.intellij.util.ExceptionUtil
import com.intellij.util.SystemProperties.getLongProperty
import com.intellij.util.io.CleanableStorage
import com.intellij.util.io.ClosedStorageException
import com.intellij.util.io.CorruptedException
import com.intellij.util.io.DurableDataEnumerator
import com.intellij.util.io.IOUtil
import com.intellij.util.io.IOUtil.MiB
import com.intellij.util.io.Unmappable
import com.intellij.util.io.blobstorage.ByteBufferWriter
import com.intellij.util.io.writeaheadlog.WriteAheadLog
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections.newSetFromMap
import java.util.WeakHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.absolute


private val LOG = logger<WriteAheadLogOverCircularBuffer>()
private val DEFAULT_FLUSH_PERIOD_MS: Long = getLongProperty("indexes.write-ahead-log.flush-period-ms", 100)


/**
 * WAL implementation over [CircularBytesBuffer].
 * On init, it reads 'unfinished' records and applies them to [channelWriter] provided.
 * If [flusherThreadFactory] is supplied, the instance also runs a background flusher thread -- otherwise it is a
 * responsibility of the called to provide a flushing strategy, or the default synchronoush [flush] will be triggered
 * when the buffer is filled up.
 */
@ApiStatus.Internal
class WriteAheadLogOverCircularBuffer(
  private val circularBytesBuffer: CircularBytesBuffer,
  private val pathEnumerator: DurableDataEnumerator<Path>,
  private val channelWriter: WriteAheadLog.ToFileWriter,
  flusherThreadFactory: ThreadFactory? = null,
  private val defaultFlushPeriodMs: Long = DEFAULT_FLUSH_PERIOD_MS,
) : WriteAheadLog, Unmappable, CleanableStorage {

  /** pathId -> #{pending (not yet consumed) records for this path} */
  //@GuardedBy(pendingRecordsLock)
  private val pendingRecordsByPathId = Int2IntOpenHashMap()
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  private val pendingRecordsLock = Object()

  private var flusherThread: Thread? = null
  @Volatile
  private var flusherThreadClosed: Boolean = false

  //<editor-fold name="statistics counters"> =====================================

  /** total bytes queued in the buffer */
  private val bytesQueued = AtomicLong()

  /** total # of [flush] forced because queue is full during [append] */
  private val flushesForcedByOverflow = AtomicLong()

  /** total # of entries flushed, i.e., written into a [channelWriter] */
  private val totalEntriesFlushed = AtomicLong()

  /** total # of bytes copied by [WriteAheadLog.PerFileWriter.applyUnfinished] */
  private val bytesCopiedByApplyUnfinished = AtomicLong()

  val statistics: WriteAheadLogStatistics
    get() = WriteAheadLogStatistics(
      bytesQueued = bytesQueued.get(),
      flushesForcedByOverflow = flushesForcedByOverflow.get(),
      entriesFlushed = totalEntriesFlushed.get(),
      bytesCopiedByApplyUnfinished = bytesCopiedByApplyUnfinished.get(),
    )

  //</editor-fold>  ============================================================

  init {
    pendingRecordsByPathId.defaultReturnValue(0)

    if (circularBytesBuffer.hasUnprocessedRecords()) {
      //restore pendingRecordsByPathId (without it, future processing in flush() leads to negative counts):
      circularBytesBuffer.read { entryDataBuffer ->
        val record = readRecord(entryDataBuffer)
        remember(record.pathId)

        //check consistency: pathId could be resolved:
        pathById(record.pathId)

        false /* do not consume the record! */
      }

      //apply records that were not applied at the end of previous session (if any):
      val flushedEntries = flush()
      //Currently this is a normal scenario, since we don't explicitly close/stop WAL -- so, it is quite likely some entries
      // haven't been flushed in previous session
      LOG.info("Unfinished entries left from previous session (=$flushedEntries) -> applied")
    }

    synchronized(walInstancesForStatistics) {
      walInstancesForStatistics.add(this)
    }

    flusherThread = flusherThreadFactory?.newThread { flushInBackground() }?.also { it.start() }
  }

  override fun openFor(path: Path): WriteAheadLog.PerFileWriter {
    val pathId = pathEnumerator.enumerate(path.absolute())
    return PerFileWriterImpl(pathId)
  }

  override fun hasUnfinished(): Boolean = circularBytesBuffer.hasUnprocessedRecords()

  override fun flush(): Int {
    val flushedEntries = circularBytesBuffer.read { entryDataBuffer ->
      val record = readRecord(entryDataBuffer)
      val path = pathById(record.pathId)
      //RC: we don't need synchronization here as long, as circularBytesBuffer guarantees an entry could be 'consumed'
      //    once and only once (which it does?).
      //    I.e., it is responsibility of circularBytesBuffer to ensure the synchronization needed
      channelWriter.write(path, record.offsetInFile, record.data)
      forget(record.pathId)
      true //consume
    }
    totalEntriesFlushed.addAndGet(flushedEntries.toLong())
    return flushedEntries
  }

  override fun close() {
    stopFlusherThread()
    try {
      flush()
      ExceptionUtil.runAllAndCollectExceptions(
        { pathEnumerator.close() },
        { circularBytesBuffer.close() }
      ).also { errors ->
        if (errors.isNotEmpty()) {
          val ioe = IOException("Error during closing")
          errors.forEach { ioe.addSuppressed(it) }
          throw ioe
        }
      }
    }
    finally {
      synchronized(walInstancesForStatistics) {
        walInstancesForStatistics.remove(this)
      }
    }
  }

  override fun closeAndClean() {
    close()
    (pathEnumerator as? CleanableStorage)?.closeAndClean()
    (circularBytesBuffer as? CleanableStorage)?.closeAndClean()
  }

  override fun closeAndUnsafelyUnmap() {
    close()
    (pathEnumerator as? Unmappable)?.closeAndUnsafelyUnmap()
    (circularBytesBuffer as? Unmappable)?.closeAndUnsafelyUnmap()
  }

  //MAYBE RC: in theory, we could apply the writes in >1 thread -- improving the throughput significantly, especially
  //          on modern SSDs/NVMs. But this requires a redesign of .flush(), because changes to the _same_ file can't
  //          be applied in parallel -- so the pending changes must be first grouped by 'target file', and only then
  //          changes for different files could be applied in parallel. Additional research is needed to confirm the
  //          improvements do worth the hassle.
  private fun flushInBackground() {
    while (!flusherThreadClosed) {
      try {
        synchronized(pendingRecordsLock) {
          pendingRecordsLock.wait(defaultFlushPeriodMs)
        }

        do {
          val entriesFlushed = flush()
        }
        while(entriesFlushed>0)
      }
      catch (e: ClosedStorageException) {
        val message = "WAL is closed -> flusher thread is exiting"
        if (!flusherThreadClosed) {
          LOG.warn(message, e)
        }
        else {
          LOG.info(message, e)
        }
        return
      }
      catch (e: InterruptedException) {
        val message = "WAL flusher thread is interrupted -> exiting"
        if (!flusherThreadClosed) {
          LOG.warn(message, e)
        }
        else {
          LOG.info(message, e)
        }
        Thread.currentThread().interrupt()
        return
      }
      catch (e: Throwable) {
        LOG.warn("WAL flush error", e)
        //Do not stop, but pray for better:
        // 1) the error may be transient, and on the next (?) iteration the operation succeeds
        // 2) if the error is not transient -- what else could we do? only quit the IDE
      }
    }
  }

  private fun stopFlusherThread() {
    flusherThreadClosed = true
    val thread = flusherThread ?: return
    thread.interrupt()
    if (Thread.currentThread() == thread) {
      return
    }

    var interrupted = false
    while (thread.isAlive) {
      try {
        thread.join()
      }
      catch (_: InterruptedException) {
        interrupted = true
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt()
    }
  }

  /** drain only records for a given pathId */
  private fun flush(pathId: Int): Int {
    val path = pathById(pathId)
    val flushedEntries = circularBytesBuffer.read { entryDataBuffer ->
      val record = readRecord(entryDataBuffer)
      if (pathId == record.pathId) {
        channelWriter.write(path, record.offsetInFile, record.data)
        forget(record.pathId)
        true
      }
      else {
        false // consume only writes for given pathId
      }
    }
    totalEntriesFlushed.addAndGet(flushedEntries.toLong())
    return flushedEntries
  }

  private fun append(pathId: Int, fileOffset: Long, writer: ByteBufferWriter, recordSize: Int) {
    val entrySize = Int.SIZE_BYTES + Long.SIZE_BYTES + recordSize
    val maxEntrySize = circularBytesBuffer.maxEntrySize()
    if (entrySize > maxEntrySize) {
      throw IllegalArgumentException(
        "WAL record entrySize(=$entrySize) exceeds circular buffer maxEntrySize(=$maxEntrySize): recordSize=$recordSize"
      )
    }
    while (true) {
      try {
        circularBytesBuffer.append(
          {
            it.putInt(pathId).putLong(fileOffset)
            writer.write(it)
          },
          entrySize
        )
        if (recordSize > 0) {
          bytesQueued.addAndGet(recordSize.toLong())
        }
        remember(pathId)
        return
      }
      catch (_: QueueFullException) {
        flushesForcedByOverflow.incrementAndGet()
        flush()
      }
    }
  }

  private fun pathById(pathId: Int): Path {
    try {
      return pathEnumerator.valueOf(pathId)
             ?: throw CorruptedException("Unknown pathId(=$pathId) -- WAL is likely corrupted")
    }
    catch (e: InvalidRecordIdException) {
      throw CorruptedException("Unknown pathId=$pathId -- WAL is likely corrupted", e)
    }
  }

  private fun remember(pathId: Int) {
    synchronized(pendingRecordsLock) {
      pendingRecordsByPathId.addTo(pathId, 1)
      pendingRecordsLock.notify()
    }
  }

  private fun forget(pathId: Int) {
    synchronized(pendingRecordsLock) {
      val countBefore = pendingRecordsByPathId.addTo(pathId, -1)
      if (countBefore == 1) {
        pendingRecordsByPathId.remove(pathId)
      }
    }
  }

  private inner class PerFileWriterImpl(private val pathId: Int) : WriteAheadLog.PerFileWriter {
    override fun write(fileOffset: Long, writer: ByteBufferWriter, recordSize: Int) {
      append(pathId, fileOffset, writer, recordSize)
    }

    override fun applyUnfinished(offsetInFile: Long, length: Int, targetBuffer: ByteBuffer, offsetInBuffer: Int) {
      this@WriteAheadLogOverCircularBuffer.applyUnfinished(pathId, offsetInFile, length, targetBuffer, offsetInBuffer)
    }

    override fun hasUnfinished(): Boolean {
      return this@WriteAheadLogOverCircularBuffer.hasUnfinished(pathId)
    }

    override fun maxUnfinishedWriteOffset(): Long {
      return this@WriteAheadLogOverCircularBuffer.maxUnfinishedWriteOffset(pathId)
    }

    override fun flush(): Int = flush(pathId)
  }

  private fun maxUnfinishedWriteOffset(pathId: Int): Long {
    var maxOffset = -1L
    circularBytesBuffer.read { entryData ->
      val record = readRecord(entryData)
      if (record.pathId == pathId) {
        maxOffset = maxOf(maxOffset, record.offsetInFile + record.data.remaining())
      }
      false //do not consume
    }
    return maxOffset
  }

  private fun applyUnfinished(pathId: Int, offsetInFile: Long, length: Int, targetBuffer: ByteBuffer, offsetInBuffer: Int) {
    synchronized(pendingRecordsLock) {
      if (!pendingRecordsByPathId.containsKey(pathId)) {
        return@synchronized
      }
      circularBytesBuffer.read { entryData ->
        val record = readRecord(entryData)
        if (record.pathId == pathId) {
          val bytesCopied = applyToBuffer(record, offsetInFile, length, targetBuffer, offsetInBuffer)
          bytesCopiedByApplyUnfinished.addAndGet(bytesCopied.toLong())
        }
        false //do not consume
      }
    }
  }

  private fun hasUnfinished(pathId: Int): Boolean = synchronized(pendingRecordsLock) {
    pendingRecordsByPathId.containsKey(pathId)
  }

  private data class Record(val pathId: Int, val offsetInFile: Long, val data: ByteBuffer)

  companion object {
    /** @GuardedBy(walInstancesForStatistics) */
    private val walInstancesForStatistics = newSetFromMap(WeakHashMap<WriteAheadLogOverCircularBuffer, Boolean>())

    private fun readRecord(entryData: ByteBuffer): Record {
      val pathId = entryData.getInt()
      val fileOffset = entryData.getLong()

      //MAYBE RC: just entryData is enough? -- the buffer is only used just above, so it is relatively safe, and
      //          cheaper than slice()-ing
      val recordData = entryData.slice().order(entryData.order())
      return Record(pathId, fileOffset, recordData)
    }

    /**
     * Copy record's data into the targetBuffer
     * @return # of bytes copied
     */
    private fun applyToBuffer(record: Record, offsetInFile: Long, length: Int, targetBuffer: ByteBuffer, offsetInBuffer: Int): Int {
      val targetRangeStart = offsetInFile
      val targetRangeEnd = offsetInFile + length

      val recordRangeStart = record.offsetInFile
      val recordRangeEnd = record.offsetInFile + record.data.remaining()

      val overlapStart = maxOf(targetRangeStart, recordRangeStart)
      val overlapEnd = minOf(targetRangeEnd, recordRangeEnd)
      val bytesOverlapped = (overlapEnd - overlapStart).toInt()
      if (bytesOverlapped <= 0) {
        return 0
      }

      targetBuffer.put(
        offsetInBuffer + (overlapStart - targetRangeStart).toInt(),
        record.data,
        (overlapStart - recordRangeStart).toInt(),
        bytesOverlapped
      )

      return bytesOverlapped
    }

    @JvmStatic
    val CANONICAL_PATH_DESCRIPTOR: KeyDescriptorEx<Path> = object : KeyDescriptorEx<Path> {
      override fun getHashCode(value: Path): Int = value.hashCode()

      override fun isEqual(key1: Path, key2: Path): Boolean = (key1 == key2)

      override fun read(input: ByteBuffer): Path {
        val path = CommonKeyDescriptors.stringAsUTF8().read(input)
        return Paths.get(path)
      }

      override fun writerFor(value: Path): DataExternalizerEx.KnownSizeRecordWriter {
        val canonicalPath = value.toCanonicalPath()
        return CommonKeyDescriptors.stringAsUTF8().writerFor(canonicalPath)
      }
    }

    @JvmStatic
    fun openDefaultWAL(
      bufferPath: Path,
      enumeratorPath: Path,
      walCapacityBytes: Int,
      flusherThreadFactory: ThreadFactory? = null,
      flushPeriodMs: Long = DEFAULT_FLUSH_PERIOD_MS,
      toFileWriter: WriteAheadLog.ToFileWriter,
    ): WriteAheadLogOverCircularBuffer {
      return CircularBytesBufferOverMMappedFile.Factory
        .withCapacityAtLeast(walCapacityBytes)
        .cleanIfFileIncompatible()
        .compose { circularBuffer ->
          if (!circularBuffer.wasClosedProperly()) {
            //Currently it should be a normal thing -- we do not close the WAL at all
            LOG.info("WAL wasn't closed properly: sad but normal these days")
          }

          if (!circularBuffer.hasUnprocessedRecords()) {
            //if circularBuffer is empty (==all records consumed/processed)
            //   => create pathsEnumerator from 0, not from existing data:
            LOG.info("No unprocessed records left => old pathsEnumerator is useless => drop it")
            IOUtil.deleteAllFilesStartingWith(enumeratorPath)
          }


          DurableEnumeratorFactory.defaultWithInMemoryMap(CANONICAL_PATH_DESCRIPTOR)
            .valuesLogFactory(
              AppendOnlyLogFactory.withDefaults()
                .pageSize(1 * MiB)//don't need large enumerator for 100-1000s paths
                .cleanIfFileIncompatible()
                .failIfDataFormatVersionNotMatch(DurableEnumerator.DATA_FORMAT_VERSION)
            ).compose { pathsEnumerator ->
              WriteAheadLogOverCircularBuffer(circularBuffer, pathsEnumerator, toFileWriter, flusherThreadFactory, flushPeriodMs)
            }.open(enumeratorPath)
        }.open(bufferPath)
    }

    @JvmStatic
    fun getAggregatedStatistics(): WriteAheadLogStatistics {
      synchronized(walInstancesForStatistics) {
        var cumulativeStats = WriteAheadLogStatistics.EMPTY
        for (instance in walInstancesForStatistics) {
          cumulativeStats += instance.statistics
        }
        return cumulativeStats
      }
    }
  }

  @ApiStatus.Internal
  data class WriteAheadLogStatistics(
    /** total bytes submitted in WAL */
    val bytesQueued: Long,
    /** total # of flushes forced because buffer is full during append */
    val flushesForcedByOverflow: Long,
    val entriesFlushed: Long,
    /** total # of bytes copied by [WriteAheadLog.PerFileWriter.applyUnfinished] */
    val bytesCopiedByApplyUnfinished: Long,
  ) {

    operator fun plus(other: WriteAheadLogStatistics): WriteAheadLogStatistics = WriteAheadLogStatistics(
      bytesQueued + other.bytesQueued,
      flushesForcedByOverflow + other.flushesForcedByOverflow,
      entriesFlushed + other.entriesFlushed,
      bytesCopiedByApplyUnfinished + other.bytesCopiedByApplyUnfinished,
    )

    companion object {
      val EMPTY: WriteAheadLogStatistics = WriteAheadLogStatistics(
        bytesQueued = 0,
        flushesForcedByOverflow = 0,
        entriesFlushed = 0,
        bytesCopiedByApplyUnfinished = 0,
      )
    }
  }
}