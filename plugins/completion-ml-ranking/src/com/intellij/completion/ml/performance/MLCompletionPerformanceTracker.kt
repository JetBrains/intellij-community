// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.performance

import com.intellij.diagnostic.telemetry.TraceManager
import io.opentelemetry.api.metrics.LongCounter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder


class MLCompletionPerformanceTracker {
  private val tracker: MeasuredTracker = MeasuredTracker(OTelTracker())
  private val elementProvidersMeasurer: ConcurrentHashMap<String, LongAdder> = ConcurrentHashMap()

  private var sortingCount = 0
  private var totalMlContribution: Long = 0L

  fun totalMLTimeContribution(): Long = totalMlContribution

  fun sortingPerformed(itemsCount: Int, totalTime: Long) {
    addByKey("sorting.items.$sortingCount", itemsCount.toLong())
    addByKey("sorting.time.$sortingCount", totalTime)
    totalMlContribution += totalTime
    sortingCount += 1
  }

  fun eventLogged(eventType: String, timeSpent: Long) {
    addByKey("log.$eventType", timeSpent)
    addByKey("log.total", timeSpent)
  }

  fun contextFeaturesCalculated(providerName: String, timeSpent: Long) {
    addByKey("context.features.$providerName", timeSpent)
  }

  fun recommendersFeaturesCalculated(providerName: String, timeSpent: Long) {
    addByKey("recommenders.features.$providerName", timeSpent)
  }

  fun itemsScored(itemsCount: Int, timeSpent: Long) {
    addByKey("model.items.$sortingCount", itemsCount.toLong())
    addByKey("model.time.$sortingCount", timeSpent)
  }

  fun reorderedByML() {
    addByKey("reordered.by.ml", 1)
  }

  fun <T> trackElementFeaturesCalculation(providerName: String, compute: () -> T): T {
    val started = System.nanoTime()
    val result = compute()
    elementProvidersMeasurer.computeIfAbsent(providerName) { LongAdder() }.add(System.nanoTime() - started)
    return result
  }

  fun measurements(): Map<String, Long> {
    flushElementProvidersContribution()
    return tracker.measurements()
  }

  private fun flushElementProvidersContribution() {
    elementProvidersMeasurer.forEach { addByKey("element.features.${it.key}", TimeUnit.NANOSECONDS.toMillis(it.value.toLong())) }
    elementProvidersMeasurer.clear()
  }

  private fun addByKey(key: String, timeMs: Long) {
    if (timeMs > 0) {
      tracker.addByKey(key, timeMs)
    }
  }

  private interface PerfTracker {
    fun addByKey(key: String, timeMs: Long)
  }

  private class OTelTracker : PerfTracker {
    private val meter = TraceManager.getMeter("completion.ranking.ml")
    private val key2counter: MutableMap<String, LongCounter> = mutableMapOf()
    override fun addByKey(key: String, timeMs: Long) {
      key2counter.computeIfAbsent(key) {
        meter.counterBuilder(key).build()
      }.add(timeMs)
    }
  }

  private class MeasuredTracker(private val delegate: PerfTracker) : PerfTracker {
    private val measurements: ConcurrentHashMap<String, LongAdder> = ConcurrentHashMap()
    override fun addByKey(key: String, timeMs: Long) {
      delegate.addByKey(key, timeMs)
      measurements.computeIfAbsent(key) { LongAdder() }.add(timeMs)
    }

    fun measurements(): Map<String, Long> {
      return measurements.mapValues { it.value.toLong() }
    }
  }

}