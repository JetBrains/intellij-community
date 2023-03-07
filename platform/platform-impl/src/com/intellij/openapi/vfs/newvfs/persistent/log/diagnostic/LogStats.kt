// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic

import com.intellij.openapi.vfs.newvfs.persistent.log.DescriptorStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private data class Stats(
  var descriptorStorageSize: Long = 0,
  var payloadStorageSize: Long = 0,
  val descriptorCount: AtomicInteger = AtomicInteger(0),
  val nullPayloads: AtomicInteger = AtomicInteger(0),
  val nullEnumeratedString: AtomicInteger = AtomicInteger(0),
  val exceptionResultCount: AtomicInteger = AtomicInteger(0),
  val payloadSizeHist: IntHistogram = IntHistogram(listOf(0, 1, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 1024*16, 1024*128, 1024*1024)),
  val tagsCount: ConcurrentHashMap<VfsOperationTag, Int> = ConcurrentHashMap(VfsOperationTag.values().size),
  val incompleteTagsCount: ConcurrentHashMap<VfsOperationTag, Int> = ConcurrentHashMap(VfsOperationTag.values().size),
  var elapsedTime: Duration = 0.seconds
) {
  val totalSize get() = descriptorStorageSize + payloadStorageSize
  val avgReadSpeedBPS get() = totalSize.toDouble() / elapsedTime.toDouble(DurationUnit.SECONDS)
  val avgDescPS get() = descriptorCount.toDouble() / elapsedTime.toDouble(DurationUnit.SECONDS)
}

@OptIn(ExperimentalTime::class)
private fun calcStats(log: VfsLog): Stats {
  fun <T> incStat(key: T, value: Int?) = if (value != null) { value + 1 } else { 1 }
  val stats = Stats()
  stats.elapsedTime = measureTime {
    runBlocking {
      log.query {
        stats.descriptorStorageSize = descriptorStorage.size()
        stats.payloadStorageSize = payloadStorage.size()

        descriptorStorage.readAll {
          when (it) {
            is DescriptorStorage.DescriptorReadResult.Incomplete -> {
              stats.descriptorCount.incrementAndGet()
              stats.incompleteTagsCount.compute(it.tag, ::incStat)
            }
            is DescriptorStorage.DescriptorReadResult.Valid -> {
              stats.descriptorCount.incrementAndGet()
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
                  } else {
                    stats.payloadSizeHist.add(data.size)
                  }
                }
                else -> {
                  // TODO other content-writing ops, at the moment they don't seem to happen at fresh-start
                  // no-op
                }
              }
            }
            is DescriptorStorage.DescriptorReadResult.Invalid -> {
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


private fun benchmark(log: VfsLog, runs: Int = 25) {
  val statsArr = mutableListOf<Stats>()

  repeat(runs) {
    statsArr += calcStats(log)
  }

  val stats = statsArr[0]

  fun List<Double>.avg() = sum() / size
  fun <T> List<T>.avgOf(body: (T) -> Double) = map(body).avg()

  println(stats)
  println("Benchmark, avg among $runs runs")
  println("calc stats time: ${statsArr.avgOf { it.elapsedTime.toDouble(DurationUnit.SECONDS) }.format("%.1f")} seconds")
  println("avg read speed: ${statsArr.avgOf { it.avgReadSpeedBPS / 1024.0 / 1024.0 }.format("%.1f")} MiB/s")
  println("avg descriptor read speed: ${statsArr.avgOf { it.avgDescPS / 1000.0 }.format("%.1f")} KDesc/s")
}

private fun single(log: VfsLog) {
  val stats = calcStats(log)
  println(stats)
  println("Single run")
  println("calc stats time: ${stats.elapsedTime.toDouble(DurationUnit.SECONDS).format("%.1f")} seconds")
  println("avg read speed: ${(stats.avgReadSpeedBPS / 1024.0 / 1024.0).format("%.1f")} MiB/s")
  println("avg descriptor read speed: ${(stats.avgDescPS / 1000.0).format("%.1f")} KDesc/s")
}

fun main(args: Array<String>) {
  assert(args.size == 1) { "Usage: <LogStats> <path to vfslog folder>" }

  val log = VfsLog(Path.of(args[0]), true)
  single(log)
  //benchmark(log)
}