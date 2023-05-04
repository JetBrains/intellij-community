// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic

import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.VFileEventBasedIterator.ReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.forEachContainedOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshotUtils.fullPath
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsTimeMachine
import com.intellij.util.io.PersistentStringEnumerator
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.div
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private data class Stats(
  var operationsStorageSize: Long = 0,
  var payloadStorageSize: Long = 0,
  val operationsCount: AtomicInteger = AtomicInteger(0),
  val nullPayloads: AtomicInteger = AtomicInteger(0),
  val nullEnumeratedString: AtomicInteger = AtomicInteger(0),
  val exceptionResultCount: AtomicInteger = AtomicInteger(0),
  val payloadSizeHist: IntHistogram = IntHistogram(listOf(0, 1, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 1024 * 16, 1024 * 128, 1024 * 1024)),
  val tagsCount: ConcurrentHashMap<VfsOperationTag, Int> = ConcurrentHashMap(VfsOperationTag.values().size),
  val incompleteTagsCount: ConcurrentHashMap<VfsOperationTag, Int> = ConcurrentHashMap(VfsOperationTag.values().size),
  var elapsedTime: Duration = 0.seconds
) {
  val totalSize get() = operationsStorageSize + payloadStorageSize
  val avgReadSpeedBPS get() = totalSize.toDouble() / elapsedTime.toDouble(DurationUnit.SECONDS)
  val avgDescPS get() = operationsCount.toDouble() / elapsedTime.toDouble(DurationUnit.SECONDS)
}

@OptIn(ExperimentalTime::class)
private fun calcStats(log: VfsLog): Stats {
  fun <T> incStat(key: T, value: Int?) = if (value != null) {
    value + 1
  }
  else {
    1
  }

  val stats = Stats()
  stats.elapsedTime = measureTime {
    runBlocking {
      log.query {
        stats.operationsStorageSize = operationLogStorage.size()
        stats.payloadStorageSize = payloadStorage.size()

        operationLogStorage.readAll {
          when (it) {
            is OperationReadResult.Incomplete -> {
              stats.operationsCount.incrementAndGet()
              stats.incompleteTagsCount.compute(it.tag, ::incStat)
            }
            is OperationReadResult.Valid -> {
              stats.operationsCount.incrementAndGet()
              if (!it.operation.result.hasValue) stats.exceptionResultCount.incrementAndGet()
              stats.tagsCount.compute(it.operation.tag, ::incStat)
              when (it.operation) {
                is VfsOperation.AttributesOperation.WriteAttribute -> {
                  val attributeId = stringEnumerator.valueOf(it.operation.attributeIdEnumerated)
                  if (attributeId == null) stats.nullEnumeratedString.incrementAndGet()
                  val data = payloadStorage.readAt(it.operation.attrDataPayloadRef)
                  if (data == null) {
                    stats.nullPayloads.incrementAndGet()
                  }
                  else {
                    stats.payloadSizeHist.add(data.size)
                  }
                }
                is VfsOperation.ContentsOperation.WriteBytes -> {
                  val data = payloadStorage.readAt(it.operation.dataPayloadRef)
                  if (data == null) {
                    stats.nullPayloads.incrementAndGet()
                  }
                  else {
                    stats.payloadSizeHist.add(data.size)
                  }
                }
                else -> {
                  // TODO other content-writing ops, at the moment they don't seem to happen at fresh-start
                  // no-op
                }
              }
            }
            is OperationReadResult.Invalid -> {
              throw it.cause
            }
          }
          true
        }
      }
    }
  }
  return stats
}

private fun Double.format(fmt: String) = String.format(fmt, this)


private fun benchmark(log: VfsLog, runs: Int = 30, heatRuns: Int = 20) {
  val statsArr = mutableListOf<Stats>()

  repeat(heatRuns + runs) {
    val stat = calcStats(log)
    if (it >= heatRuns) {
      statsArr += stat
    }
  }

  val stats = statsArr[0]

  fun List<Double>.avg() = sum() / size
  fun List<Double>.std(): Double {
    val avg = avg()
    return sqrt(sumOf { (it - avg) * (it - avg) } / size)
  }

  fun <T> List<T>.avgOf(body: (T) -> Double) = map(body).avg()
  fun <T> List<T>.deviationOf(body: (T) -> Double) = map(body).std()
  data class MeanDev(val mean: Double, val dev: Double) {
    fun represent(doubleFormat: String = "%.2f") = "${mean.format(doubleFormat)}±${dev.format(doubleFormat)}"
    override fun toString(): String {
      return represent()
    }
  }

  fun <T> List<T>.meanDev(body: (T) -> Double) = MeanDev(avgOf(body), deviationOf(body))

  println(stats)
  println()
  println("Benchmark, $runs runs, $heatRuns preheat runs")
  println("calc stats time: ${statsArr.meanDev { it.elapsedTime.toDouble(DurationUnit.SECONDS) }} seconds")
  println("read speed: ${statsArr.meanDev { it.avgReadSpeedBPS / 1024.0 / 1024.0 }} MiB/s")
  println("descriptor read speed: ${statsArr.meanDev { it.avgDescPS / 1000.0 }} KDesc/s")
}

private fun single(log: VfsLog) {
  val stats = calcStats(log)
  println(stats)
  println("Single run")
  println("calc stats time: ${stats.elapsedTime.toDouble(DurationUnit.SECONDS).format("%.1f")} seconds")
  println("avg read speed: ${(stats.avgReadSpeedBPS / 1024.0 / 1024.0).format("%.1f")} MiB/s")
  println("avg descriptor read speed: ${(stats.avgDescPS / 1000.0).format("%.1f")} KDesc/s")
}



@OptIn(ExperimentalTime::class)
private fun vFileEventIterCheck(log: VfsLog, names: PersistentStringEnumerator) {
  var singleOp = 0
  var vfileEvents = 0
  var vfileEventContentOps = 0

  val vfsTimeMachine = VfsTimeMachine(log.query { operationLogStorage.begin() }, id2name = names::valueOf)

  val time = measureTime {
    log.query {
      val iter = IteratorUtils.VFileEventBasedIterator(operationLogStorage.begin())
      while (iter.hasNext()) {
        val rec = iter.next()
        when (rec) {
          is ReadResult.Invalid -> throw rec.cause
          is ReadResult.SingleOperation -> {
            singleOp++
            rec.iterator().next() // read it
          }
          is ReadResult.VFileEventRange -> {
            val snapshot = vfsTimeMachine.getSnapshot(rec.begin())
            vfileEvents++
            println()
            println(rec.startTag)
            when (rec.startTag) {
              VfsOperationTag.VFILE_EVENT_MOVE -> {
                val startOp =
                  (rec.begin().next() as OperationReadResult.Valid).operation as VfsOperation.VFileEventOperation.EventStart.Move
                val file = snapshot.getFileById(startOp.fileId)
                println("PATH: ${file.fullPath()}")
                val oldParent = snapshot.getFileById(startOp.oldParentId)
                val newParent = snapshot.getFileById(startOp.newParentId)
                println("MOVE FROM PARENT ${oldParent.name} to ${newParent.name}")
              }
              VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE -> {
                val startOp =
                  (rec.begin().next() as OperationReadResult.Valid).operation as VfsOperation.VFileEventOperation.EventStart.ContentChange
                val file = snapshot.getFileById(startOp.fileId)
                println("PATH: ${file.fullPath()}")
              }
              else -> {}
            }

            rec.forEachContainedOperation {
              if (it is OperationReadResult.Valid) {
                println(it.operation)
              }
              else println(it)
              check(it !is OperationReadResult.Invalid)
              vfileEventContentOps++
            }
          }
        }
      }
    }
  }

  println("singleOp: $singleOp")
  println("vfileEvents: $vfileEvents")
  println("vfileEventContentOps: $vfileEventContentOps")
  println("time: $time")
}

fun main(args: Array<String>) {
  assert(args.size == 1) { "Usage: <LogStats> <path to vfslog folder>" }

  val logPath = Path.of(args[0])
  val log = VfsLog(logPath, true)
  val names = PersistentStringEnumerator(logPath.parent / "names.dat", true)
  //single(log)
  //benchmark(log, 100)
  vFileEventIterCheck(log, names)
}