// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.beans

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.util.Comparing
import org.jetbrains.annotations.ApiStatus

/**
 * Reports numerical or string value of the setting if it's not default.
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                     valueFunction: Function1<T, Any>, eventId: String) {
  addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, null)
}

/**
 * Reports numerical or string value of the setting if it's not default.
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                     valueFunction: Function1<T, Any>, eventId: String, data: FeatureUsageData?) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) {
    when (it) {
      is Int -> newMetric(eventId, it, data)
      is Float -> newMetric(eventId, it, data)
      else -> newMetric(eventId, it.toString(), data)
    }
  }
}

/**
 * Reports the value of boolean setting (i.e. enabled or disabled) if it's not default.
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addBoolIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                         valueFunction: Function1<T, Boolean>, eventId: String) {
  addBoolIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, null)
}

/**
 * Reports the value of boolean setting (i.e. enabled or disabled) if it's not default.
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addBoolIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                         valueFunction: Function1<T, Boolean>, eventId: String, data: FeatureUsageData?) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newBooleanMetric(eventId, it, data) }
}

/**
 * Adds counter value if count is greater than 0
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addCounterIfNotZero(set: MutableSet<in MetricEvent>, eventId: String, count: Int) {
  if (count > 0) {
    set.add(newCounterMetric(eventId, count))
  }
}

/**
 * Adds counter value if count is greater than 0
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addCounterIfNotZero(set: MutableSet<in MetricEvent>, eventId: String, count: Int, data: FeatureUsageData?) {
  if (count > 0) {
    set.add(newCounterMetric(eventId, count, data))
  }
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addCounterIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                            valueFunction: Function1<T, Int>, eventId: String) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterMetric(eventId, it) }
}

inline fun <T, V> addMetricIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                     crossinline valueFunction: (T) -> V, crossinline eventIdFunc: (V) -> MetricEvent) {
  val value = valueFunction(settingsBean)
  val defaultValue = valueFunction(defaultSettingsBean)
  if (!Comparing.equal(value, defaultValue)) {
    set.add(eventIdFunc(value))
  }
}

interface MetricDifferenceBuilder<T> {
  fun add(eventId: String, valueFunction: (T) -> Any)
  fun addBool(eventId: String, valueFunction: (T) -> Boolean)
}

@JvmOverloads
inline fun <T> addBoolIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                crossinline valueFunction: (T) -> Boolean, eventId: VarargEventId, data: List<EventPair<*>>? = null) {
  addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, EventFields.Enabled, data)
}

@JvmOverloads
inline fun <T, V> addIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                               crossinline valueFunction: (T) -> V, eventId: VarargEventId, field: EventField<V>, data: List<EventPair<*>>? = null) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) {
    val fields = data?.toMutableList() ?: mutableListOf()
    fields.add(field.with(it))
    eventId.metric(fields)
  }
}

/**
 * Adds counter value if count is greater than 0
 */
fun <T> addCounterIfNotZero(set: MutableSet<in MetricEvent>, eventId: VarargEventId, count: Int) {
  if (count > 0) {
    set.add(eventId.metric(EventFields.Count.with(count)))
  }
}

/**
 * Adds counter value if count is greater than 0
 */
fun <T> addCounterIfNotZero(set: MutableSet<in MetricEvent>, eventId: VarargEventId, count: Int, data: MutableList<EventPair<*>>? = null) {
  if (count > 0) {
    val fields = data ?: mutableListOf()
    fields.add(EventFields.Count.with(count))
    eventId.metric(fields)
  }
}