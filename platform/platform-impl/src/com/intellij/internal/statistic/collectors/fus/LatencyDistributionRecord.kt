// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus

import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class LatencyDistributionRecordKey(val name: String) {
  var details: String? = null
}

@ApiStatus.Internal
class LatencyDistributionRecord(val key: LatencyDistributionRecordKey) {
  val totalLatency: LatencyRecord = LatencyRecord()
  val actionLatencyRecords: MutableMap<String, LatencyRecord> = mutableMapOf()

  fun update(action: String, latencyInMS: Int) {
    totalLatency.update(latencyInMS)
    actionLatencyRecords.getOrPut(action) { LatencyRecord() }.update(latencyInMS)
  }
}

@ApiStatus.Internal
class LatencyRecord {
  var totalLatency: Long = 0L
  var maxLatency: Int = 0
  val samples: IntArrayList = IntArrayList()
  private var samplesSorted = false

  fun update(latencyInMS: Int) {
    samplesSorted = false
    samples.add(latencyInMS)
    totalLatency += latencyInMS
    if (latencyInMS > maxLatency) {
      maxLatency = latencyInMS
    }
  }

  val averageLatency: Long
    get() = totalLatency / samples.size

  fun percentile(n: Int): Int {
    if (!samplesSorted) {
      samples.sort()
      samplesSorted = true
    }
    val index = (samples.size * n / 100).coerceAtMost(samples.size - 1)
    return samples.getInt(index)
  }
}
