// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.function.Consumer

abstract class BaseEventId @JvmOverloads constructor(val eventId: String, val recorder: String = "FUS") {
  internal fun getLogger(): StatisticsEventLogger = StatisticsEventLogProviderUtil.getEventLogProvider(recorder).logger

  abstract fun getFields(): List<EventField<*>>
}

class EventId(
  private val group: EventLogGroup,
  eventId: String,
) : BaseEventId(eventId, group.recorder) {

  fun log() {
    getLogger().logAsync(group, eventId, false)
  }

  fun log(project: Project?) {
    getLogger().logAsync(group, eventId, FeatureUsageData().addProject(project).build(), false)
  }

  fun metric(): MetricEvent {
    return MetricEvent(eventId, null)
  }

  override fun getFields(): List<EventField<*>> = emptyList()
}

class EventId1<in T>(
  private val group: EventLogGroup,
  eventId: String,
  private val field1: EventField<T>,
) : BaseEventId(eventId, group.recorder) {

  fun log(value1: T) {
    getLogger().logAsync(group, eventId, buildUsageData(value1).build(), false)
  }

  fun log(project: Project?, value1: T) {
    getLogger().logAsync(group, eventId, buildUsageData(value1).addProject(project).build(), false)
  }

  fun metric(value1: T): MetricEvent {
    return MetricEvent(eventId, buildUsageData(value1))
  }

  private fun buildUsageData(value1: T): FeatureUsageData {
    val data = FeatureUsageData()
    field1.addData(data, value1)
    return data
  }

  override fun getFields(): List<EventField<*>> = listOf(field1)
}

class EventId2<in T1, in T2>(
  private val group: EventLogGroup,
  eventId: String,
  private val field1: EventField<T1>,
  private val field2: EventField<T2>,
) : BaseEventId(eventId, group.recorder) {

  fun log(value1: T1, value2: T2) {
    getLogger().logAsync(group, eventId, buildUsageData(value1, value2).build(), false)
  }

  fun log(project: Project?, value1: T1, value2: T2) {
    getLogger().logAsync(group, eventId, buildUsageData(value1, value2).addProject(project).build(), false)
  }

  fun metric(value1: T1, value2: T2): MetricEvent {
    return MetricEvent(eventId, buildUsageData(value1, value2))
  }

  private fun buildUsageData(value1: T1, value2: T2): FeatureUsageData {
    val data = FeatureUsageData()
    field1.addData(data, value1)
    field2.addData(data, value2)
    return data
  }

  override fun getFields(): List<EventField<*>> = listOf(field1, field2)
}

class EventId3<in T1, in T2, in T3>(
  private val group: EventLogGroup,
  eventId: String,
  private val field1: EventField<T1>,
  private val field2: EventField<T2>,
  private val field3: EventField<T3>,
) : BaseEventId(eventId, group.recorder) {

  fun log(value1: T1, value2: T2, value3: T3) {
    getLogger().logAsync(group, eventId, buildUsageData(value1, value2, value3).build(), false)
  }

  fun log(project: Project?, value1: T1, value2: T2, value3: T3) {
    getLogger().logAsync(group, eventId, buildUsageData(value1, value2, value3).addProject(project).build(), false)
  }

  fun metric(value1: T1, value2: T2, value3: T3): MetricEvent {
    return MetricEvent(eventId, buildUsageData(value1, value2, value3))
  }

  private fun buildUsageData(value1: T1, value2: T2, value3: T3): FeatureUsageData {
    val data = FeatureUsageData()
    field1.addData(data, value1)
    field2.addData(data, value2)
    field3.addData(data, value3)
    return data
  }

  override fun getFields(): List<EventField<*>> = listOf(field1, field2, field3)
}

class EventDataCollector : ArrayList<EventPair<*>>() {
  var skipped = false

  fun skip() {
    skipped = true
  }
}

class VarargEventId internal constructor(
  private val group: EventLogGroup,
  eventId: String,
  vararg fields: EventField<*>,
) : BaseEventId(eventId, group.recorder) {

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
  fun log(project: Project?, dataBuilder: Consumer<List<EventPair<*>>>) {
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

  fun metric(vararg pairs: EventPair<*>): MetricEvent {
    return metric(listOf(*pairs))
  }

  fun metric(pairs: List<EventPair<*>>): MetricEvent {
    return MetricEvent(eventId, buildUsageData(pairs))
  }

  private fun buildUsageData(pairs: List<EventPair<*>>): FeatureUsageData {
    val data = FeatureUsageData()
    for (pair in pairs) {
      if (pair.field !in fields) throw IllegalArgumentException("Field ${pair.field.name} not in fields for event ID $eventId")
      @Suppress("UNCHECKED_CAST")
      (pair.field as EventField<Any?>).addData(data, pair.data)
    }
    return data
  }

  override fun getFields(): List<EventField<*>> = fields.toList()

  companion object {
    val LOG = Logger.getInstance(VarargEventId::class.java)
  }
}
