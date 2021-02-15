// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.openapi.util.Comparing

/**
 * Reports numerical or string value of the setting if it's not default.
 */
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                     valueFunction: Function1<T, Any>, eventId: String) {
  addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, null)
}

/**
 * Reports numerical or string value of the setting if it's not default.
 */
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
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addBoolIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                         valueFunction: Function1<T, Boolean>, eventId: String) {
  addBoolIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, null)
}

/**
 * Reports the value of boolean setting (i.e. enabled or disabled) if it's not default.
 */
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addBoolIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                         valueFunction: Function1<T, Boolean>, eventId: String, data: FeatureUsageData?) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newBooleanMetric(eventId, it, data) }
}

/**
 * Adds counter value if count is greater than 0
 */
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addCounterIfNotZero(set: MutableSet<in MetricEvent>, eventId: String, count: Int) {
  if (count > 0) {
    set.add(newCounterMetric(eventId, count))
  }
}

/**
 * Adds counter value if count is greater than 0
 */
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addCounterIfNotZero(set: MutableSet<in MetricEvent>, eventId: String, count: Int, data: FeatureUsageData?) {
  if (count > 0) {
    set.add(newCounterMetric(eventId, count, data))
  }
}

@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addCounterIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                            valueFunction: Function1<T, Int>, eventId: String) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterMetric(eventId, it) }
}

@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addCounterIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                            valueFunction: Function1<T, Int>, eventId: String, data: FeatureUsageData?) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterMetric(eventId, it, data) }
}

@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T, V : Enum<*>> addEnumIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                      valueFunction: Function1<T, V>, eventId: String) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newMetric(eventId, it, null) }
}

@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T, V> addMetricIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                              valueFunction: (T) -> V, eventIdFunc: (V) -> MetricEvent) {
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

@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun <T> addMetricsIfDiffers(set: MutableSet<in MetricEvent>,
                            settingsBean: T,
                            defaultSettingsBean: T,
                            data: FeatureUsageData,
                            callback: MetricDifferenceBuilder<T>.() -> Unit) {
  callback(object : MetricDifferenceBuilder<T> {
    override fun add(eventId: String, valueFunction: (T) -> Any) {
      addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, data)
    }

    override fun addBool(eventId: String, valueFunction: (T) -> Boolean) {
      addBoolIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, data)
    }
  })
}
