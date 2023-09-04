// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.vfslog

import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsOracle
import com.intellij.openapi.vfs.newvfs.persistent.log.*
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.VFileEventBasedIterator.ReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.forEachContainedOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage.OperationReadResult
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.SinglePassVfsTimeMachine
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.fmap
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.getOrNull
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.State.Companion.mapCases
import com.intellij.openapi.vfs.newvfs.persistent.log.timemachine.VfsSnapshot
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.SimpleStringPersistentEnumerator
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.nio.charset.StandardCharsets
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

// TODO this code is to be refactored into proper tests soon

private data class Stats(
  var operationsStorageSize: Long = 0,
  var payloadStorageSize: Long = 0,
  val operationsCount: AtomicInteger = AtomicInteger(0),
  val notAvailablePayloads: AtomicInteger = AtomicInteger(0),
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
private fun calcStats(log: VfsLogImpl): Stats {
  fun <T> incStat(key: T, value: Int?) = if (value != null) {
    value + 1
  }
  else {
    1
  }

  val stats = Stats()
  stats.elapsedTime = measureTime {
    runBlocking {
      log.getContextImpl().run {
        stats.operationsStorageSize = operationLogStorage.size()
        stats.payloadStorageSize = payloadStorage.size()
        //val attrCount: MutableMap<String, Int> = mutableMapOf<String, Int>()

        operationLogStorage.readAll {
          when (it) {
            is OperationReadResult.Incomplete -> {
              stats.operationsCount.incrementAndGet()
              stats.incompleteTagsCount.compute(it.tag, ::incStat)
            }
            is OperationReadResult.Complete -> {
              stats.operationsCount.incrementAndGet()
              if (!it.operation.result.hasValue) stats.exceptionResultCount.incrementAndGet()
              stats.tagsCount.compute(it.operation.tag, ::incStat)
              when (val op = it.operation) {
                is VfsOperation.AttributesOperation.WriteAttribute -> {
                  val attr = deenumerateAttribute(op.enumeratedAttribute)
                  if (attr == null) stats.nullEnumeratedString.incrementAndGet()
                  //else {
                  //  attrCount[attributeId] = attrCount.getOrDefault(attributeId, 0) + 1
                  //}
                  val data = payloadReader(op.dataRef)
                  data.mapCases({ stats.notAvailablePayloads.incrementAndGet() }) {
                    stats.payloadSizeHist.add(it.size)
                  }
                }
                is VfsOperation.ContentsOperation.WriteBytes -> {
                  val data = payloadReader(op.dataRef)
                  data.mapCases({ stats.notAvailablePayloads.incrementAndGet() }) {
                    stats.payloadSizeHist.add(it.size)
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
            else -> throw IllegalStateException("shouldn't happen") // smart cast of public api from different module is not permitted
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


private fun benchmark(log: VfsLogImpl, runs: Int = 30, heatRuns: Int = 20) {
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

private fun single(log: VfsLogImpl) {
  val stats = calcStats(log)
  println(stats)
  println("Single run")
  println("calc stats time: ${stats.elapsedTime.toDouble(DurationUnit.SECONDS).format("%.1f")} seconds")
  println("avg read speed: ${(stats.avgReadSpeedBPS / 1024.0 / 1024.0).format("%.1f")} MiB/s")
  println("avg descriptor read speed: ${(stats.avgDescPS / 1000.0).format("%.1f")} KDesc/s")
}


private typealias Diff = Pair<List<String>, List<String>>

@OptIn(ExperimentalTime::class)
private fun vfsRecoveryDraft(queryContext: VfsLogQueryContext,
                             attributeEnumerator: SimpleStringPersistentEnumerator,
                             fsRecordsOracle: FSRecordsOracle) {
  var singleOp = 0
  var vfileEvents = 0
  var vfileEventContentOps = 0

  val singlePassVTM = SinglePassVfsTimeMachine(
    queryContext,
    nameByNameId = fsRecordsOracle::getNameByNameId,
    getAttributeEnumerator = { attributeEnumerator }
  )
  val vfsTimeMachine = singlePassVTM
  //.withOracle(fsRecordsOracle)

  fun VfsSnapshot.VirtualFileSnapshot.represent(): String =
    "file: name=${getName()} parent=$parentId id=$fileId ts=$timestamp len=$length flags=$flags contentId=$contentRecordId attrId=$attributesRecordId"

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

  /*
  // TODO move this to tests
  // attributes can only be read from inside the Application as storages require access to ProgressManager. But it works if there are no locks:

  // copy-pasted from [com.intellij.openapi.vfs.newvfs.persistent.PersistentFSTreeAccessor] and slightly modified for testing purposes
  fun VfsSnapshot.VirtualFileSnapshot.readChildAttr(): State.DefinedState<List<Int>> =
    readAttribute(PersistentFSTreeAccessor.CHILDREN_ATTR).fmap { input ->
      val count = if (input == null) 0 else DataInputOutputUtil.readINT(input)
      val children: MutableList<Int> = if (count == 0) mutableListOf()
      else ArrayList<Int>(count)
      var prevId: Int = fileId
      for (i in 0 until count) {
        val childId: Int = DataInputOutputUtil.readINT(input!!) + prevId
        prevId = childId
        children.add(childId)
      }
      children
    }

  // from com.intellij.psi.stubs.StubTreeLoaderImpl
  val INDEXED_STAMP = FileAttribute("stubIndexStamp", 3, true)

  data class StubIndexStampData(val fileStamp: Long, val byteLength: Long, val charLength: Int, val isBinary: Boolean)

  fun VfsSnapshot.VirtualFileSnapshot.readStubIndexStampAttr(): State.DefinedState<StubIndexStampData?> =
    readAttribute(INDEXED_STAMP).fmap { stream ->
      if (stream == null || stream.available() <= 0) {
        return@fmap null
      }
      val stamp: Long = DataInputOutputUtil.readTIME(stream)
      val byteLength = DataInputOutputUtil.readLONG(stream)

      val flags: Byte = stream.readByte()
      val isBinary = BitUtil.isSet(flags, 1)
      val readOnlyOneLength = BitUtil.isSet(flags, 2)

      val charLength: Int
      if (isBinary) {
        charLength = -1
      }
      else if (readOnlyOneLength) {
        charLength = byteLength.toInt()
      }
      else {
        charLength = DataInputOutputUtil.readINT(stream)
      }
      check(stream.available() == 0)
      StubIndexStampData(stamp, byteLength, charLength, isBinary)
    }
  */

  fun Diff.represent() =
    if (first.isEmpty() && second.isEmpty()) "No diff"
    else "Diff:\n" + first.joinToString("\n", postfix = "\n") { "- $it" } + second.joinToString("\n") { "+ $it" }

  val time = measureTime {
    queryContext.run {
      val iter = IteratorUtils.VFileEventBasedIterator(begin())
      while (iter.hasNext()) {
        when (val rec = iter.next()) {
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
                  (rec.begin().next() as OperationReadResult.Complete).operation as VfsOperation.VFileEventOperation.EventStart.Move
                val file = snapshotBefore.getFileById(startOp.fileId)
                println(file.represent())
                //println("stub index stamp data: ${file.readStubIndexStampAttr()}")
                val oldParent = snapshotBefore.getFileById(startOp.oldParentId)
                val oldParentAfter = snapshotAfter.getFileById(startOp.oldParentId)
                val newParent = snapshotBefore.getFileById(startOp.newParentId)
                println("MOVE FROM PARENT ${oldParent.getName()} to ${newParent.getName()}")
                //println("old parent's children ids before: ${oldParent.readChildAttr()}")
                //println("old parent's children ids after: ${oldParentAfter.readChildAttr()}")
                println("old parent's children ids before: ${
                  snapshotBefore.getChildrenIdsOf(oldParent.fileId).fmap { it.map { snapshotBefore.getFileById(it).getName() } }
                }")
                println("old parent's children ids after: ${
                  snapshotAfter.getChildrenIdsOf(oldParentAfter.fileId).fmap { it.map { snapshotAfter.getFileById(it).getName() } }
                }")
              }
              VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE -> {
                val startOp =
                  (rec.begin().next() as OperationReadResult.Complete).operation as VfsOperation.VFileEventOperation.EventStart.ContentChange
                val fileBefore = snapshotBefore.getFileById(startOp.fileId)
                if (fileBefore.getName().getOrNull()?.endsWith(".kt") != true) continue
                val fileAfter = snapshotAfter.getFileById(startOp.fileId)
                println(fileBefore.represent())
                //println("stub index stamp data: ${fileBefore.readStubIndexStampAttr()}")
                val contentBefore = fileBefore.getContent().fmap { it.toString(StandardCharsets.UTF_8) }
                val contentAfter = fileAfter.getContent().fmap { it.toString(StandardCharsets.UTF_8) }
                if (contentBefore is State.Ready && contentAfter is State.Ready) {
                  val textBefore = contentBefore.value
                  val textAfter = contentAfter.value
                  println(buildDiff(textBefore, textAfter).represent())
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
              if (it is OperationReadResult.Complete) {
                println(it.operation)
              }
              else println(it)
              check(it !is OperationReadResult.Invalid)
              vfileEventContentOps++
            }
          }
          else -> throw IllegalStateException("shouldn't happen") // smart cast of public api from different module is not permitted
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
  val log = VfsLogImpl.open(logPath, true)

  //val names = PersistentStringEnumerator(logPath.parent / "names.dat", true)::valueOf
  val attributeEnumerator = SimpleStringPersistentEnumerator(logPath.parent / "attributes_enums.dat")

  //single(log)
  //benchmark(log, 30)
  //return

  val queryContext = log.query()
  queryContext.use {
    val fsRecordsOracle = FSRecordsOracle(logPath.parent,
                                          FSRecordsImpl.ErrorHandler { records, error ->
                                            ExceptionUtil.rethrow(error)
                                          },
                                          queryContext)

    Closeable {
      fsRecordsOracle.disposeConnection()
      AppExecutorUtil.shutdownApplicationScheduledExecutorService()
    }.use {
      vfsRecoveryDraft(queryContext, attributeEnumerator, fsRecordsOracle)
    }
  }
}