// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import java.util.*

/**
 * Creates a metric without any event data, e.g.
 *
 * "has.dockerfile"
 *
 * This type of metric is not recommended, before using it consider:
 * * merging this metric with another one, e.g. "has.config.file" -> "type":"dockerfile" or "type":"docker-compose.yml";
 * * adding more information about this metric, e.g. "has.dockerfile" -> "version":"2.3", "location":"project.root";
 */
fun newMetric(eventId: String): MetricEvent {
  return MetricEvent(eventId, null)
}

/**
 * Creates a multi-dimensional metric or a metric with a single but common event data, e.g.
 *
 * "breakpoint" -> "type":"line", "lang":"Java", "count":5,
 * "gradle" -> "version":"2.3.1"
 */
fun newMetric(eventId: String, data: FeatureUsageData?): MetricEvent {
  return MetricEvent(eventId, data)
}

/**
 * Creates a enum-like string metrics, e.g.
 *
 * "upload.files" -> "value":"ON_SAVE"
 */
fun newMetric(eventId: String, value: String): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a enum-like string metrics, e.g.
 *
 * "upload.files" -> "value":"ON_SAVE"
 */
fun newMetric(eventId: String, value: String, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addValue(value))
}

/**
 * Creates a metric with enum value, e.g.
 *
 * "upload.files" -> "value":"ON_SAVE"
 */
fun newMetric(eventId: String, value: Enum<*>?): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a metric with enum value, e.g.
 *
 * "upload.files" -> "value":"ON_SAVE"
 */
fun newMetric(eventId: String, value: Enum<*>?, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  val newValue = value?.name?.toLowerCase(Locale.ENGLISH) ?: "unknown"
  return MetricEvent(eventId, newData.addValue(newValue))
}

/**
 * Creates a metric with numerical value, e.g.
 *
 * "allowed.connections" -> "value":3
 */
fun newMetric(eventId: String, value: Int): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a metric with numerical value, e.g.
 *
 * "allowed.connections" -> "value":3
 */
fun newMetric(eventId: String, value: Int, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addValue(value))
}

/**
 *  Creates a metric with numerical value, e.g.
 *
 * "line.spacing" -> "value":1.2
 */
fun newMetric(eventId: String, value: Float): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a metric with numerical value, e.g.
 *
 * "line.spacing" -> "value":1.2
 */
fun newMetric(eventId: String, value: Float, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addValue(value))
}

/**
 * Creates a metric with information about a boolean setting, if it's enabled or disabled, e.g.
 *
 * "font.ligatures" -> "enabled":true
 */
fun newBooleanMetric(eventId: String, enabled: Boolean): MetricEvent {
  return newBooleanMetric(eventId, enabled, null)
}

/**
 * Creates a metric with information about a boolean setting, if it's enabled or disabled, e.g.
 *
 * "font.ligatures" -> "enabled":true
 */
fun newBooleanMetric(eventId: String, enabled: Boolean, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addEnabled(enabled))
}

/**
 * Creates a general boolean metric, e.g.
 *
 * "tool.is.under.project.root" -> "value":true
 */
fun newMetric(eventId: String, value: Boolean): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a general boolean metric, e.g.
 *
 * "tool.is.under.project.root" -> "value":true
 */
fun newMetric(eventId: String, value: Boolean, data: FeatureUsageData? = null): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addValue(value))
}

/**
 * Creates a metric with information about the number of elements in the group, e.g.
 *
 * "source_roots" -> "count":3
 */
fun newCounterMetric(eventId: String, count: Int): MetricEvent {
  return newCounterMetric(eventId, count, null)
}

/**
 * Creates a metric with information about the number of elements in the group, e.g.
 *
 * "source_roots" -> "count":3
 */
fun newCounterMetric(eventId: String, count: Int, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addCount(count))
}

/**
 * @deprecated will be deleted in 2019.3
 *
 * This method should be used only for a transition period for existing counter metrics.
 * New metrics should report absolute counter value by
 * @see newCounterMetric(java.lang.String, int)
 */
@Deprecated("Only for existing counter metrics, new metrics should report absolute counter value")
fun newCounterRangeMetric(eventId: String, count: Int, data: FeatureUsageData? = null): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  newData.addCount(count).addData("count_group", getCountingUsage(count))
  return MetricEvent(eventId, newData)
}

/**
 * @deprecated will be deleted in 2019.3
 *
 * This method should be used only for a transition period for existing counter metrics.
 * New metrics should report absolute counter value by
 * @see newCounterMetric(java.lang.String, int)
 */
@Deprecated("Only for existing counter metrics, new metrics should report absolute counter value")
fun newCounterRangeMetric(eventId: String, count: Int, steps: List<Int>, data: FeatureUsageData? = null): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  newData.addCount(count).addData("count_group", getCountingUsage(count, steps))
  return MetricEvent(eventId, newData)
}

/**
 * @deprecated
 * This method should be used only for a transition period for existing counter metrics.
 * New metrics should report absolute counter value by
 * @see newCounterMetric(java.lang.String, int)
 *
 * Constructs a count range by absolute value.
 * NB:
 * (1) the list of steps must be sorted ascendingly; If it is not, the result is undefined.
 * (2) the value should lay somewhere inside steps ranges. If it is below the first step, the following usage will be reported:
 * `git.commit.count.<1`.
 *
 * @count Count to be checked among the given ranges.
 * @steps Limits of the ranges. Each value represents the start of the next range. The list must be sorted ascendingly.
 */
@Deprecated("Only for existing counter metrics, new metrics should report absolute counter value")
private fun getCountingUsage(count: Int, steps: List<Int>): String {
  if (steps.isEmpty()) return "$count"
  if (count < steps[0]) return "<${steps[0]}"

  var stepIndex = 0
  while (stepIndex < steps.size - 1) {
    if (count < steps[stepIndex + 1]) break
    stepIndex++
  }

  val step = steps[stepIndex]
  val addPlus = stepIndex == steps.size - 1 || steps[stepIndex + 1] != step + 1
  val stepName = humanize(step) + if (addPlus) "+" else ""
  return stepName
}

/**
 * @deprecated
 * This method should be used only for a transition period for existing counter metrics.
 * New metrics should report absolute counter value
 * @see newCounterMetric(java.lang.String, int)
 *
 * [getCountingUsage] with steps (0, 1, 2, 3, 5, 10, 15, 30, 50, 100, 500, 1000, 5000, 10000, ...)
 */
@Deprecated("Only for existing counter metrics, new metrics should report absolute counter value")
private fun getCountingUsage(value: Int): String {
  if (value > Int.MAX_VALUE / 10) return "MANY"
  if (value < 0) return "<0"
  if (value < 3) return "$value"

  val fixedSteps = listOf(3, 5, 10, 15, 30, 50)

  var step = fixedSteps.last { it <= value }
  while (true) {
    if (value < step * 2) break
    step *= 2
    if (value < step * 5) break
    step *= 5
  }

  val stepName = humanize(step)
  return "$stepName+"
}

private const val kilo = 1000
private val mega = kilo * kilo

private fun humanize(number: Int): String {
  if (number == 0) return "0"
  val m = number / mega
  val k = (number % mega) / kilo
  val r = (number % kilo)
  val ms = if (m > 0) "${m}M" else ""
  val ks = if (k > 0) "${k}K" else ""
  val rs = if (r > 0) "${r}" else ""
  return ms + ks + rs
}
