// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.openapi.util.Comparing

/**
 * Reports numerical or string value of the setting if it's not default.
 */
fun <T> addIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                     valueFunction: Function1<T, Any>, eventId: String) {
  addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, null)
}

/**
 * Reports numerical or string value of the setting if it's not default.
 */
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
fun <T> addBoolIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                         valueFunction: Function1<T, Boolean>, eventId: String) {
  addBoolIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction, eventId, null)
}

/**
 * Reports the value of boolean setting (i.e. enabled or disabled) if it's not default.
 */
fun <T> addBoolIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                         valueFunction: Function1<T, Boolean>, eventId: String, data: FeatureUsageData?) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newBooleanMetric(eventId, it, data) }
}

/**
 * Adds counter value if count is greater than 0
 */
fun <T> addCounterIfNotZero(set: MutableSet<in MetricEvent>, eventId: String, count: Int) {
  if (count > 0) {
    set.add(newCounterMetric(eventId, count))
  }
}

/**
 * Adds counter value if count is greater than 0
 */
fun <T> addCounterIfNotZero(set: MutableSet<in MetricEvent>, eventId: String, count: Int, data: FeatureUsageData?) {
  if (count > 0) {
    set.add(newCounterMetric(eventId, count, data))
  }
}

fun <T> addCounterIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                            valueFunction: Function1<T, Int>, eventId: String) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterMetric(eventId, it) }
}

fun <T> addCounterIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                            valueFunction: Function1<T, Int>, eventId: String, data: FeatureUsageData?) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterMetric(eventId, it, data) }
}

fun <T> addCounterRangeIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                 valueFunction: Function1<T, Int>, eventId: String, data: FeatureUsageData?) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterRangeMetric(eventId, it, data) }
}

fun <T> addCounterRangeIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                 valueFunction: Function1<T, Int>, eventId: String) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterRangeMetric(eventId, it) }
}

fun <T> addCounterRangeIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                 valueFunction: Function1<T, Int>, featureId: String, steps: List<Int>, data: FeatureUsageData?) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterRangeMetric(featureId, it, steps, data) }
}

fun <T> addCounterRangeIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                 valueFunction: Function1<T, Int>, eventId: String, steps: List<Int>) {
  addMetricIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { newCounterRangeMetric(eventId, it, steps) }
}

fun <T, V> addMetricIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                              valueFunction: (T) -> V, eventIdFunc: (V) -> MetricEvent) {
  val value = valueFunction(settingsBean)
  val defaultValue = valueFunction(defaultSettingsBean)
  if (!Comparing.equal(value, defaultValue)) {
    set.add(eventIdFunc(value))
  }
}