// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans

import com.intellij.internal.statistic.eventLog.FeatureUsageData

import java.util.Objects

/**
 * Used to write measurements to event log, override one of these methods to use it: <br/>
 * [com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector.getMetrics]<br/>
 * [com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector.getMetrics]
 *
 * You can either create it manually or with factory methods [com.intellij.internal.statistic.beans.MetricEventFactoryKt]
 *
 * @see newMetric
 * @see newBooleanMetric
 * @see newCounterMetric
 */
class MetricEvent @JvmOverloads constructor(val eventId: String, data: FeatureUsageData? = null) {
  val data: FeatureUsageData = data ?: FeatureUsageData()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val event = other as MetricEvent?
    return eventId == event!!.eventId && data == event.data
  }

  override fun hashCode(): Int {
    return Objects.hash(eventId, data)
  }

  override fun toString(): String {
    return "$eventId: {$data}"
  }
}
