// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.project.Project
import java.awt.event.InputEvent

data class InputEventPlace(val inputEvent: InputEvent?, val place: String?)

abstract class EventField<T> {
  abstract fun addData(fuData: FeatureUsageData, value: T)

  infix fun with(data: T): EventPair<T> = EventPair(this, data)
}

data class EventPair<T>(val field: EventField<T>, val data: T)

data class StringEventField(private val name: String): EventField<String?>() {
  private var customRuleId: String? = null

  override fun addData(fuData: FeatureUsageData, value: String?) {
    if (value != null) {
      fuData.addData(name, value)
    }
  }

  fun withCustomRule(id: String): StringEventField {
    customRuleId = id
    return this
  }
}

data class IntEventField(private val name: String): EventField<Int>() {
  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, value)
  }
}

data class EnumEventField<T : Enum<*>>(private val name: String, private val enumClass: Class<T>): EventField<T>() {
  override fun addData(fuData: FeatureUsageData, value: T) {
    fuData.addData(name, value.toString())
  }
}

object EventFields {
  @JvmStatic
  fun String(name: String): StringEventField {
    return StringEventField(name)
  }

  @JvmStatic
  fun Int(name: String): IntEventField {
    return IntEventField(name)
  }

  @JvmStatic
  fun <T : Enum<*>> Enum(name: String, enumClass: Class<T>): EnumEventField<T> {
    return EnumEventField(name, enumClass)
  }

  inline fun <reified T : Enum<*>> Enum(name: String): EnumEventField<T> {
    return EnumEventField(name, T::class.java)
  }

  @JvmField
  val Project = object : EventField<Project?>() {
    override fun addData(fuData: FeatureUsageData, value: Project?) {
      fuData.addProject(value)
    }
  }

  @JvmField
  val InputEvent = object : EventField<InputEventPlace>() {
    override fun addData(fuData: FeatureUsageData, value: InputEventPlace) {
      fuData.addInputEvent(value.inputEvent, value.place)
    }
  }

  @JvmField
  val PluginInfo = object : EventField<PluginInfo>() {
    override fun addData(fuData: FeatureUsageData, value: PluginInfo) {
      fuData.addPluginInfo(value)
    }
  }

  @JvmField
  val PluginInfoFromInstance = object : EventField<Any>() {
    override fun addData(fuData: FeatureUsageData, value: Any) {
      fuData.addPluginInfo(getPluginInfo(value::class.java))
    }
  }

  @JvmField
  val AnonymizedPath = object : EventField<String?>() {
    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addAnonymizedPath(value)
    }
  }
}

/**
 * Best practices:
 * - Prefer a bigger group with many (related) event types to many small groups of 1-2 events each.
 * - Prefer shorter group names; avoid common prefixes (such as "statistics.").
 */
class EventLogGroup(val id: String, val version: Int) {
  private val registeredEventIds = mutableSetOf<String>()

  internal fun registerEventId(eventId: String) {
    registeredEventIds.add(eventId)
  }

  fun registerEvent(eventId: String): EventId {
    registeredEventIds.add(eventId)
    return EventId(this, eventId)
  }

  fun <T1> registerEvent(eventId: String, eventField1: EventField<T1>): EventId1<T1> {
    registeredEventIds.add(eventId)
    return EventId1(this, eventId, eventField1)
  }

  fun <T1, T2> registerEvent(eventId: String, eventField1: EventField<T1>, eventField2: EventField<T2>): EventId2<T1, T2> {
    registeredEventIds.add(eventId)
    return EventId2(this, eventId, eventField1, eventField2)
  }

  fun <T1, T2, T3> registerEvent(eventId: String, eventField1: EventField<T1>, eventField2: EventField<T2>, eventField3: EventField<T3>): EventId3<T1, T2, T3> {
    registeredEventIds.add(eventId)
    return EventId3(this, eventId, eventField1, eventField2, eventField3)
  }

  internal fun validateEventId(eventId: String) {
    if (registeredEventIds.isNotEmpty() && eventId !in registeredEventIds) {
      throw IllegalArgumentException("Trying to report unregistered event ID $eventId to group $id")
    }
  }

  companion object {
    @JvmStatic fun counter(id: String): EventLogGroup {
      return FUCounterUsageLogger.getInstance().findRegisteredGroupById(id)
    }

    @JvmStatic fun project(collector: ProjectUsagesCollector): EventLogGroup {
      return EventLogGroup(collector.groupId, collector.version)
    }
  }
}

class EventId(private val group: EventLogGroup, private val eventId: String) {
  fun log() {
    FeatureUsageLogger.log(group, eventId)
  }
}

class EventId1<T>(private val group: EventLogGroup, private val eventId: String, private val field1: EventField<T>) {
  fun log(value1: T) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1).build())
  }

  fun metric(value1: T): MetricEvent {
    return MetricEvent(eventId, buildUsageData(value1))
  }

  private fun buildUsageData(value1: T): FeatureUsageData {
    val data = FeatureUsageData()
    field1.addData(data, value1)
    return data
  }
}

class EventId2<T1, T2>(private val group: EventLogGroup, private val eventId: String, private val field1: EventField<T1>, private val field2: EventField<T2>) {
  fun log(value1: T1, value2: T2) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1, value2).build())
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
}

class EventId3<T1, T2, T3>(private val group: EventLogGroup, private val eventId: String, private val field1: EventField<T1>, private val field2: EventField<T2>, private val field3: EventField<T3>) {
  fun log(value1: T1, value2: T2, value3: T3) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1, value2, value3).build())
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
}

open class VarargEventId(private val group: EventLogGroup, private val eventId: String, private vararg val fields: EventField<*>) {
  init {
    group.registerEventId(eventId)
  }

  fun log(vararg pairs: EventPair<*>) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(*pairs).build())
  }

  fun metric(vararg pairs: EventPair<*>): MetricEvent {
    return MetricEvent(eventId, buildUsageData(*pairs))
  }

  private fun buildUsageData(vararg pairs: EventPair<*>): FeatureUsageData {
    val data = FeatureUsageData()
    for (pair in pairs) {
      if (pair.field !in fields) throw IllegalArgumentException("Field not in fields for this event ID")
      @Suppress("UNCHECKED_CAST")
      (pair.field as EventField<Any?>).addData(data, pair.data)
    }
    return data
  }
}
