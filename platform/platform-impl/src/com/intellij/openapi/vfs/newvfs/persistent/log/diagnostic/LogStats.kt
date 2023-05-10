// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic

import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.VFileEventBasedIterator.ReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.forEachContainedOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.FSRecordsOracle
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsSnapshot.VirtualFileSnapshot.Property.State
import com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic.timemachine.VfsTimeMachine
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
        //val attrCount: MutableMap<String, Int> = mutableMapOf<String, Int>()

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
                  //else {
                  //  attrCount[attributeId] = attrCount.getOrDefault(attributeId, 0) + 1
                  //}
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

        //println(attrCount.toList().sortedByDescending { it.second })
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


private typealias Diff = Pair<List<String>, List<String>>

@OptIn(ExperimentalTime::class)
private fun vFileEventIterCheck(log: VfsLog,
                                id2name: (Int) -> String?,
                                fsRecordsOracle: FSRecordsOracle? = null) {
  var singleOp = 0
  var vfileEvents = 0
  var vfileEventContentOps = 0

  val vfsTimeMachine = VfsTimeMachine(log.query { operationLogStorage.begin() },
                                      id2name = id2name,
                                      payloadReader = log.query { payloadStorage::readAt },
                                      oracle = fsRecordsOracle?.let { it::getSnapshot })

  fun VfsSnapshot.VirtualFileSnapshot.represent(): String =
    "file: name=$name parent=$parentId id=$fileId ts=$timestamp len=$length flags=$flags contentId=$contentRecordId attrId=$attributesRecordId"

  fun buildDiff(textBefore: String, textAfter: String): Diff {
    val linesBefore = textBefore.strip().split("\n").toMutableList()
    val linesAfter = textAfter.strip().split("\n").toMutableList()
    while (linesBefore.isNotEmpty() && linesAfter.isNotEmpty() && linesBefore[0] == linesAfter[0]) {
      linesBefore.removeAt(0)
      linesAfter.removeAt(0)
    }
    while (linesBefore.isNotEmpty() && linesAfter.isNotEmpty() && linesBefore.last() == linesAfter.last()) {
      linesBefore.removeAt(linesBefore.size - 1)
      linesAfter.removeAt(linesAfter.size - 1)
    }
    return linesBefore to linesAfter
  }

  fun Diff.represent() = if (first.isEmpty() && second.isEmpty()) "No diff" else
    "Diff:\n" + first.joinToString("\n") { "- $it" } + "\n" + second.joinToString("\n") { "+ $it" }

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
            val snapshotBefore = vfsTimeMachine.getSnapshot(rec.begin())
            val snapshotAfter = vfsTimeMachine.getSnapshot(rec.end())
            vfileEvents++
            println()
            println(rec.startTag)
            when (rec.startTag) {
              VfsOperationTag.VFILE_EVENT_MOVE -> {
                val startOp =
                  (rec.begin().next() as OperationReadResult.Valid).operation as VfsOperation.VFileEventOperation.EventStart.Move
                val file = snapshotBefore.getFileById(startOp.fileId)
                println(file.represent())
                val oldParent = snapshotBefore.getFileById(startOp.oldParentId)
                val newParent = snapshotBefore.getFileById(startOp.newParentId)
                println("MOVE FROM PARENT ${oldParent.name} to ${newParent.name}")
              }
              VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE -> {
                val startOp =
                  (rec.begin().next() as OperationReadResult.Valid).operation as VfsOperation.VFileEventOperation.EventStart.ContentChange
                val fileBefore = snapshotBefore.getFileById(startOp.fileId)
                val fileAfter = snapshotAfter.getFileById(startOp.fileId)
                println(fileBefore.represent())
                val contentBefore = fileBefore.getContent()
                val contentAfter = fileAfter.getContent()
                if (contentBefore is State.Ready && contentAfter is State.Ready) {
                  val bytesBefore = contentBefore.value ?: ByteArray(0)
                  val bytesAfter = contentAfter.value ?: ByteArray(0)
                  println(buildDiff(bytesBefore.toString(StandardCharsets.UTF_8), bytesAfter.toString(StandardCharsets.UTF_8)).represent())
                }
                else {
                  println("content before:")
                  println(contentBefore)
                  println("content after:")
                  println(contentAfter)
                }
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

  //single(log)
  //benchmark(log, 30)
  //return

  val fsRecords = FSRecordsImpl.connect(logPath.parent,
                                        log,
                                        FSRecordsImpl.ErrorHandler { records, error -> ExceptionUtil.rethrow(error) })
  //val names = PersistentStringEnumerator(logPath.parent / "names.dat", true)::valueOf
  val names = { id: Int -> fsRecords.getNameByNameId(id)?.toString() }
  val oracle = FSRecordsOracle(fsRecords, log)

  vFileEventIterCheck(log, names, oracle)

  fsRecords.dispose()
}