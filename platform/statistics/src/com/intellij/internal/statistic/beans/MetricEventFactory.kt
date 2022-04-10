// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.*

/**
 * Creates a metric without any event data, e.g.
 *
 * eventId="has.dockerfile"
 *
 * This type of metric is not recommended, before using it consider:
 * * merging this metric with another one, e.g. eventId="has.config.file", eventData={"type":"dockerfile"} or eventData={"type":"docker-compose.yml"};
 * * adding more information about this metric, e.g. eventId="has.dockerfile", eventData={"version":"2.3", "location":"project.root"};
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String): MetricEvent {
  return MetricEvent(eventId, null)
}

/**
 * Creates a multi-dimensional metric or a metric with a single but common event data, e.g.
 *
 * eventId="breakpoint", eventData={"type":"line", "lang":"Java", "count":5}
 * eventId="gradle", eventData={"version":"2.3.1"}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, data: FeatureUsageData?): MetricEvent {
  return MetricEvent(eventId, data)
}

/**
 * Creates a enum-like string metrics, e.g.
 *
 * eventId="upload.files", eventData={"value":"ON_SAVE"}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: String): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a enum-like string metrics, e.g.
 *
 * eventId="upload.files", eventData={"value":"ON_SAVE"}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: String, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addValue(value))
}

/**
 * Creates a metric with enum value, e.g.
 *
 * eventId="upload.files", eventData={"value":"ON_SAVE"}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: Enum<*>?): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a metric with enum value, e.g.
 *
 * eventId="upload.files", eventData={"value":"ON_SAVE"}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: Enum<*>?, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  val newValue = value?.name?.toLowerCase(Locale.ENGLISH) ?: "unknown"
  return MetricEvent(eventId, newData.addValue(newValue))
}

/**
 * Creates a metric with numerical value, e.g.
 *
 * eventId="allowed.connections", eventData={"value":3}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: Int): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a metric with numerical value, e.g.
 *
 * eventId="allowed.connections", eventData={"value":3}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: Int, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addValue(value))
}

/**
 *  Creates a metric with numerical value, e.g.
 *
 * eventId="line.spacing", eventData={"value":1.2}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: Float): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a metric with numerical value, e.g.
 *
 * eventId="line.spacing", eventData={"value":1.2}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: Float, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addValue(value))
}

/**
 * Creates a metric with information about a boolean setting, if it's enabled or disabled, e.g.
 *
 * eventId="font.ligatures", eventData={"enabled":true}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newBooleanMetric(@NonNls eventId: String, enabled: Boolean): MetricEvent {
  return newBooleanMetric(eventId, enabled, null)
}

/**
 * Creates a metric with information about a boolean setting, if it's enabled or disabled, e.g.
 *
 * eventId="font.ligatures", eventData={"enabled":true}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newBooleanMetric(@NonNls eventId: String, enabled: Boolean, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addEnabled(enabled))
}

/**
 * Creates a general boolean metric, e.g.
 *
 * eventId="tool.is.under.project.root", eventData={"value":true}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: Boolean): MetricEvent {
  return newMetric(eventId, value, null)
}

/**
 * Creates a general boolean metric, e.g.
 *
 * eventId="tool.is.under.project.root", eventData={"value":true}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: Boolean, data: FeatureUsageData? = null): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addValue(value))
}

/**
 * Creates a metric with information about the number of elements in the group, e.g.
 *
 * eventId="source_roots", eventData={"count":3}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newCounterMetric(@NonNls eventId: String, count: Int): MetricEvent {
  return newCounterMetric(eventId, count, null)
}

/**
 * Creates a metric with information about the number of elements in the group, e.g.
 *
 * eventId="source_roots", eventData={"count":3}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newCounterMetric(@NonNls eventId: String, count: Int, data: FeatureUsageData?): MetricEvent {
  val newData = data?.copy() ?: FeatureUsageData()
  return MetricEvent(eventId, newData.addCount(count))
}
