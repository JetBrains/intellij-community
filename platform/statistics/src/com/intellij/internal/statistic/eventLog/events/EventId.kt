// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Consumer
import kotlin.collections.plus
import kotlin.collections.toList

abstract class BaseEventId(val eventId: String, val recorder: String, val description: String?) {
  
  internal fun getLogger(): StatisticsEventLogger = StatisticsEventLogProviderUtil.getEventLogProvider(recorder).logger

  abstract fun getFields(): List<EventField<*>>

  protected fun EventLogGroup.extendFeatureUsageData(fuData: FeatureUsageData) {
    groupData.forEach { (_, appendData) ->
      fuData.appendData()
    }
  }

  protected fun EventLogGroup.extendEventFields(eventFields: List<EventField<*>>): List<EventField<*>> {
    if (groupData.isEmpty()) return eventFields
    return (mutableListOf<EventField<*>>() + groupData.map { it.first } + eventFields).toList()
  }
}

class EventId(
  private val group: EventLogGroup,
  @NonNls @EventIdName eventId: String,
  @NonNls description: String?
) : BaseEventId(eventId, group.recorder, description) {

  fun log() {
    if (group.groupData.isEmpty()) {
      getLogger().logAsync(group, eventId, false)
    } else {
      val data = FeatureUsageData(group.recorder)
      group.extendFeatureUsageData(data)
      getLogger().logAsync(group, eventId, data.build(), false)
    }
  }

  fun log(project: Project?) {
    val data = FeatureUsageData(group.recorder)
    group.extendFeatureUsageData(data)
    getLogger().logAsync(group, eventId, FeatureUsageData(group.recorder).addProject(project).build(), false)
  }

  fun metric(): MetricEvent {
    return if (group.groupData.isEmpty()) {
      MetricEvent(eventId, null, group.recorder)
    } else {
      val data = FeatureUsageData(group.recorder)
      group.extendFeatureUsageData(data)
      MetricEvent(eventId, data, group.recorder)
    }
  }

  override fun getFields(): List<EventField<*>> = group.extendEventFields(emptyList())

  override fun toString(): String {
    return "EventId(eventId='$eventId')"
  }
}

class EventId1<in T>(
  private val group: EventLogGroup,
  @NonNls @EventIdName eventId: String,
  @NonNls description: String?,
  private val field1: EventField<T>,
) : BaseEventId(eventId, group.recorder, description) {

  fun log(value1: T) {
    getLogger().logAsync(group, eventId, buildUsageData(value1).build(), false)
  }

  fun log(project: Project?, value1: T) {
    getLogger().logAsync(group, eventId, buildUsageData(value1).addProject(project).build(), false)
  }

  fun metric(value1: T): MetricEvent {
    return MetricEvent(eventId, buildUsageData(value1), group.recorder)
  }

  private fun buildUsageData(value1: T): FeatureUsageData {
    val data = FeatureUsageData(group.recorder)
    group.extendFeatureUsageData(data)
    field1.addData(data, value1)
    return data
  }

  override fun getFields(): List<EventField<*>> = group.extendEventFields(listOf(field1))

  override fun toString(): String {
    return "EventId1(eventId='$eventId')"
  }
}

class EventId2<in T1, in T2>(
  private val group: EventLogGroup,
  @NonNls @EventIdName eventId: String,
  @NonNls description: String?,
  private val field1: EventField<T1>,
  private val field2: EventField<T2>,
) : BaseEventId(eventId, group.recorder, description) {

  fun log(value1: T1, value2: T2) {
    getLogger().logAsync(group, eventId, buildUsageData(value1, value2).build(), false)
  }

  fun log(project: Project?, value1: T1, value2: T2) {
    getLogger().logAsync(group, eventId, buildUsageData(value1, value2).addProject(project).build(), false)
  }

  fun metric(value1: T1, value2: T2): MetricEvent {
    return MetricEvent(eventId, buildUsageData(value1, value2), group.recorder)
  }

  private fun buildUsageData(value1: T1, value2: T2): FeatureUsageData {
    val data = FeatureUsageData(group.recorder)
    group.extendFeatureUsageData(data)
    field1.addData(data, value1)
    field2.addData(data, value2)
    return data
  }

  override fun getFields(): List<EventField<*>> = group.extendEventFields(listOf(field1, field2))

  override fun toString(): String {
    return "EventId2(eventId='$eventId')"
  }
}

class EventId3<in T1, in T2, in T3>(
  private val group: EventLogGroup,
  @NonNls @EventIdName eventId: String,
  @NonNls description: String?,
  private val field1: EventField<T1>,
  private val field2: EventField<T2>,
  private val field3: EventField<T3>,
) : BaseEventId(eventId, group.recorder, description) {

  fun log(value1: T1, value2: T2, value3: T3) {
    getLogger().logAsync(group, eventId, buildUsageData(value1, value2, value3).build(), false)
  }

  fun log(project: Project?, value1: T1, value2: T2, value3: T3) {
    getLogger().logAsync(group, eventId, buildUsageData(value1, value2, value3).addProject(project).build(), false)
  }

  fun metric(value1: T1, value2: T2, value3: T3): MetricEvent {
    return MetricEvent(eventId, buildUsageData(value1, value2, value3), group.recorder)
  }

  private fun buildUsageData(value1: T1, value2: T2, value3: T3): FeatureUsageData {
    val data = FeatureUsageData(group.recorder)
    group.extendFeatureUsageData(data)
    field1.addData(data, value1)
    field2.addData(data, value2)
    field3.addData(data, value3)
    return data
  }

  override fun getFields(): List<EventField<*>> = group.extendEventFields(listOf(field1, field2, field3))

  override fun toString(): String {
    return "EventId3(eventId='$eventId')"
  }
}

class EventDataCollector : ArrayList<EventPair<*>>() {
  var skipped: Boolean = false

  fun skip() {
    skipped = true
  }
}

class VarargEventId internal constructor(
  private val group: EventLogGroup,
  @NonNls @EventIdName eventId: String,
  @NonNls description: String?,
  vararg fields: EventField<*>,
) : BaseEventId(eventId, group.recorder, description) {

  private val fields = fields.toMutableList()

  fun log(vararg pairs: EventPair<*>) {
    log(listOf(*pairs))
  }

  fun log(pairs: List<EventPair<*>>) {
    getLogger().logAsync(group, eventId, buildUsageData(pairs).build(), false)
  }

  fun log(project: Project?, vararg pairs: EventPair<*>) {
    log(project, listOf(*pairs))
  }

  fun log(project: Project?, pairs: List<EventPair<*>>) {
    getLogger().logAsync(group, eventId, buildUsageData(pairs).addProject(project).build(), false)
  }

  /**
   * Introduced to simplify usage from java
   * */
  fun log(project: Project?, dataBuilder: Consumer<MutableList<EventPair<*>>>) {
    log(project, dataBuilder::accept)
  }

  fun log(project: Project?, dataBuilder: EventDataCollector.() -> Unit) {
    getLogger().logAsync(group, eventId, {
      val list = EventDataCollector()
      list.dataBuilder()
      if (!list.skipped) {
        buildUsageData(list).addProject(project).build()
      }
      else {
        null
      }
    }, false)
  }

  fun logState(project: Project?, pairs: List<EventPair<*>>) {
    getLogger().logAsync(group, eventId, buildUsageData(pairs).addProject(project).build(), true)
  }

  fun metric(vararg pairs: EventPair<*>): MetricEvent {
    return metric(listOf(*pairs))
  }

  fun metric(pairs: List<EventPair<*>>): MetricEvent {
    return MetricEvent(eventId, buildUsageData(pairs), group.recorder)
  }

  private fun buildUsageData(pairs: List<EventPair<*>>): FeatureUsageData {
    val data = FeatureUsageData(group.recorder)
    group.extendFeatureUsageData(data)
    for (pair in pairs) {
      if (pair.field !in fields) throw IllegalArgumentException("Field ${pair.field.name} not in fields for event ID $eventId")
      @Suppress("UNCHECKED_CAST")
      (pair.field as EventField<Any?>).addData(data, pair.data)
    }
    return data
  }

  override fun getFields(): List<EventField<*>> = group.extendEventFields(fields)

  override fun toString(): String {
    return "VarargEventId(eventId='$eventId')"
  }
}
