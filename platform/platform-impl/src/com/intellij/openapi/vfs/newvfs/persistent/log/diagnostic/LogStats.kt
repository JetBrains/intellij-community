// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.openapi.vfs.newvfs.persistent.log.diagnostic

import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperation
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsOperationTag
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

data class Stats(
  var descriptorStorageSize: Long = 0,
  var payloadStorageSize: Long = 0,
  val descriptorCount: AtomicInteger = AtomicInteger(0),
  val nullPayloads: AtomicInteger = AtomicInteger(0),
  val nullEnumeratedString: AtomicInteger = AtomicInteger(0),
  val exceptionResultCount: AtomicInteger = AtomicInteger(0),
  val payloadSizeHist: IntHistogram = IntHistogram(listOf(0, 1, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 1024*16, 1024*128, 1024*1024)),
  val tagsCount: ConcurrentHashMap<VfsOperationTag, Int> = ConcurrentHashMap(VfsOperationTag.values().size),
  var elapsedTime: Duration = 0.seconds
) {
  val totalSize get() = descriptorStorageSize + payloadStorageSize
  val avgReadSpeedBPS get() = totalSize.toDouble() / elapsedTime.toDouble(DurationUnit.SECONDS)
  val avgDescPS get() = descriptorCount.toDouble() / elapsedTime.toDouble(DurationUnit.SECONDS)
}

@OptIn(DelicateCoroutinesApi::class, ExperimentalTime::class)
fun main(args: Array<String>) {
  assert(args.size == 1) { "Usage: <LogStats> <path to vfslog folder>" }

  val log = VfsLog(Path.of(args[0]), true)

  val stats = Stats()
  fun <T> incStat(key: T, value: Int?) = if (value != null) {
    value + 1
  }
  else {
    1
  }
  fun Double.format(fmt: String) = String.format(fmt, this)

  stats.elapsedTime = measureTime {
    runBlocking {
      log.query {
        stats.descriptorStorageSize = descriptorStorage.size()
        stats.payloadStorageSize = payloadStorage.size()

        descriptorStorage.readAll {
          val descCount = stats.descriptorCount.incrementAndGet()
          if (!it.result.hasValue) stats.exceptionResultCount.incrementAndGet()
          stats.tagsCount.compute(it.tag, ::incStat)
          when (it) {
            is VfsOperation.AttributesOperation.WriteAttribute -> {
              val attributeId = stringEnumerator.valueOf(it.attributeIdEnumerated)
              if (attributeId == null) stats.nullEnumeratedString.incrementAndGet()
              val data = payloadStorage.readAt(it.attrDataPayloadRef)
              if (data == null) {
                stats.nullPayloads.incrementAndGet()
              }
              else {
                stats.payloadSizeHist.add(data.size)
              }
            }
            is VfsOperation.ContentsOperation.WriteBytes -> {
              val data = payloadStorage.readAt(it.dataPayloadRef)
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
          true
        }
      }
    }
  }
  print("\r")
  println(stats)
  println("Calculated in ${stats.elapsedTime.toDouble(DurationUnit.SECONDS).format("%.1f")} seconds")
  println("avg read speed: ${(stats.avgReadSpeedBPS / 1024.0 / 1024.0).format("%.1f")} MiB/s")
  println("avg descriptor read speed: ${(stats.avgDescPS / 1000.0).format("%.1f")} KDesc/s")
}