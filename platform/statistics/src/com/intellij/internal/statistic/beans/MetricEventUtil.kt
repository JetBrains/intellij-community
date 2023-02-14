// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.beans

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.util.Comparing

inline fun <T, V> addMetricIfDiffers(set: MutableSet<in MetricEvent>, settingsBean: T, defaultSettingsBean: T,
                                     crossinline valueFunction: (T) -> V, crossinline eventIdFunc: (V) -> MetricEvent) {
  val value = valueFunction(settingsBean)
  val defaultValue = valueFunction(defaultSettingsBean)
  if (!Comparing.equal(value, defaultValue)) {
    set.add(eventIdFunc(value))
  }
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