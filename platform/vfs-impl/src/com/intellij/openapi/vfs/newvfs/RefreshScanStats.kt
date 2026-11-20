// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeMark
import kotlin.time.TimeSource


/** Holds per-worker counters for one refresh scan. Each worker writes only its own counters. */
internal class RefreshScanStats {
  companion object {
    private val SLOW_SCAN_THRESHOLD = 1.seconds

    /** Starts a measurement. Pass the mark to [WorkerStats.fsCall] or [WorkerStats.readLockWaited] when the measured action ends. */
    @JvmStatic
    fun markNow(): TimeMark = TimeSource.Monotonic.markNow()
  }

  /** The kind of an FS call. Each kind has its own counters. */
  enum class FsKind(val label: String) {
    LIST("list"),
    ATTRS("attrs"),
    SYMLINK("symlink"),
    CANONICAL("canonical"),
    HAS_CHILDREN("has-children"),
    FSYNC("fsync"),
    SCAN_CHILDREN("scan-children"),
  }

  private val startMark = TimeSource.Monotonic.markNow()

  @JvmField
  val workers = CopyOnWriteArrayList<WorkerStats>()

  var duration: Duration = Duration.ZERO
    private set

  fun newWorker(): WorkerStats {
    val worker = WorkerStats()
    workers.add(worker)
    return worker
  }

  /** Ends the scan. The duration is the time since the creation of the object. */
  fun finished() {
    finished(startMark.elapsedNow())
  }

  fun finished(duration: Duration) {
    this.duration = duration
  }

  fun isSlow(): Boolean = duration > SLOW_SCAN_THRESHOLD

  fun fullScans(): Int = workers.sumOf { it.fullScans }

  fun partialScans(): Int = workers.sumOf { it.partialScans }

  fun dirsDone(): Int = workers.sumOf { it.dirsDone }

  fun dirRescans(): Int = workers.sumOf { it.dirRescans }

  fun busy(): Duration = sumOfDuration { it.busy }

  fun idle(): Duration = sumOfDuration { it.idle }

  fun wasted(): Duration = sumOfDuration { it.wasted }

  fun io(): Duration = sumOfDuration { it.io }

  fun total(): Duration = sumOfDuration { it.total }

  fun readLockWaiting(): Duration = sumOfDuration { it.readLockWaiting }

  fun fsCalls(kind: FsKind): Long = workers.sumOf { it.fsCalls[kind.ordinal] }

  fun fsTime(kind: FsKind): Duration = sumOfDuration { it.fsTimes[kind.ordinal] }

  /** The share of the thread time in which the threads did not idle while they waited for tasks.
   * The share includes wasted and IO operations.
   */
  fun threadUtilization(): Double = fraction(busy(), total())

  /** The share of the busy time that the threads spend on IO file system operations. */
  fun ioUtilization(): Double = fraction(io(), busy())

  /** The share of the busy time that the threads spend on a scan that a changed directory made useless. The scan result is discarded. */
  fun wastedUtilization(): Double = fraction(wasted(), busy())

  /** The share of the pool time that the threads were alive. A low value means that some threads finished early and could do other tasks. */
  fun threadPoolUtilization(): Double = fraction(total(), duration * workers.size)

  /** The share of the busy time that the threads spend while they wait for the read lock. */
  fun readLockWaitingUtilization(): Double = fraction(readLockWaiting(), busy())

  fun retries(): Int = fullScans() + partialScans() - dirsDone()

  fun durationMs(): Long = duration.inWholeMilliseconds

  fun format(): String {
    val sb = StringBuilder(640)
    sb.append("refresh scan: ").append(duration.inWholeMilliseconds).append(" ms")
      .append(" | workers: ").append(workers.size)
      .append(", thread utilization: ").append(threadUtilization().asPercentage())
      .append(", io utilization: ").append(ioUtilization().asPercentage())
      .append(", wasted utilization: ").append(wastedUtilization().asPercentage())
      .append(", thread pool utilization: ").append(threadPoolUtilization().asPercentage())
      .append(", read lock wait utilization: ").append(readLockWaitingUtilization().asPercentage())
      .append("| dirs full: ").append(fullScans())
      .append(", partial: ").append(partialScans())
      .append(", done: ").append(dirsDone())
      .append(", rescans: ").append(dirRescans())
    for (kind in FsKind.entries) {
      val calls = fsCalls(kind)
      if (calls > 0) {
        sb.append(' ').append(kind.label).append(' ').append(calls)
          .append('/').append(fsTime(kind).inWholeMilliseconds).append(" ms")
      }
    }
    return sb.toString()
  }

  private inline fun sumOfDuration(selector: (WorkerStats) -> Duration): Duration =
    workers.fold(Duration.ZERO) { sum, worker -> sum + selector(worker) }


  private fun fraction(value: Duration, total: Duration): Double =
    if (total == Duration.ZERO) 0.0 else value.toDouble(DurationUnit.NANOSECONDS) / total.toDouble(DurationUnit.NANOSECONDS)

  /** The counters of one worker thread. The scan reads them after the worker finishes. */
  class WorkerStats {
    @JvmField
    var dirsDone: Int = 0

    @JvmField
    var fullScans: Int = 0

    @JvmField
    var partialScans: Int = 0

    @JvmField
    var dirRescans: Int = 0

    var busy: Duration = Duration.ZERO
    var idle: Duration = Duration.ZERO
    var wasted: Duration = Duration.ZERO
    var io: Duration = Duration.ZERO
    var readLockWaiting: Duration = Duration.ZERO
    var total: Duration = Duration.ZERO

    @JvmField
    val fsCalls = LongArray(FsKind.entries.size)
    val fsTimes = Array(FsKind.entries.size) { Duration.ZERO }

    private var workerMark: TimeMark? = null
    private var loopMark: TimeMark? = null
    private var dirRefreshMark: TimeMark? = null

    /** The worker thread starts. [workerFinished] sets the total time from this moment. */
    fun workerStarted() {
      workerMark = markNow()
    }

    fun workerFinished() {
      workerMark?.let { total = it.elapsedNow() }
    }

    /** One pass of the worker loop starts. */
    fun loopIterationStarted() {
      loopMark = markNow()
    }

    /** The pass found no directory to scan. */
    fun loopIdled() {
      loopMark?.let { idle += it.elapsedNow() }
    }

    /** The pass scanned a directory, or skipped it. */
    fun loopFinished() {
      loopMark?.let { busy += it.elapsedNow() }
    }

    fun dirRefreshStarted() {
      dirRefreshMark = markNow()
    }

    /** The VFS changed during the scan of the directory: the time since [dirRefreshStarted] is wasted. */
    fun dirRescanned() {
      dirRescans++
      dirRefreshMark?.let { wasted += it.elapsedNow() }
    }

    /** Records the wait for the read lock, which started at [start]. */
    fun readLockWaited(start: TimeMark) {
      val waited = start.elapsedNow()
      readLockWaiting += waited
    }

    /** Records one FS call of the [kind], which started at [start]. The time counts as IO time. */
    fun fsCall(kind: FsKind, start: TimeMark) {
      val elapsed = start.elapsedNow()
      fsCalls[kind.ordinal]++
      fsTimes[kind.ordinal] += elapsed
      io += elapsed
    }
  }

  private fun Double.asPercentage(): String = "%.2f%%".format(this * 100)
}
