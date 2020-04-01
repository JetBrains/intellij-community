// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.openapi.project.Project
import java.awt.event.InputEvent

data class InputEventPlace(val inputEvent: InputEvent?, val place: String?)

abstract class EventField<T> {
  abstract fun addData(fuData: FeatureUsageData, value: T)

  infix fun with(data: T): EventPair<T> = EventPair(this, data)
}

data class EventPair<T>(val field: EventField<T>, val data: T)

private data class StringEventField(private val name: String): EventField<String?>() {
  override fun addData(fuData: FeatureUsageData, value: String?) {
    if (value != null) {
      fuData.addData(name, value)
    }
  }
}

object EventFields {
  @JvmStatic
  fun String(name: String): EventField<String?> {
    return StringEventField(name)
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
  fun registerEvent(eventId: String): EventId {
    return EventId(this, eventId)
  }

  fun <T1> registerEvent(eventId: String, eventField1: EventField<T1>): EventId1<T1> {
    return EventId1(this, eventId, eventField1)
  }

  fun <T1, T2> registerEvent(eventId: String, eventField1: EventField<T1>, eventField2: EventField<T2>): EventId2<T1, T2> {
    return EventId2(this, eventId, eventField1, eventField2)
  }

  fun <T1, T2, T3> registerEvent(eventId: String, eventField1: EventField<T1>, eventField2: EventField<T2>, eventField3: EventField<T3>): EventId3<T1, T2, T3> {
    return EventId3(this, eventId, eventField1, eventField2, eventField3)
  }

  companion object {
    @JvmStatic fun byId(id: String): EventLogGroup {
      return FUCounterUsageLogger.getInstance().findRegisteredGroupById(id)
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
    val data = FeatureUsageData()
    field1.addData(data, value1)
    FeatureUsageLogger.log(group, eventId, data.build())
  }
}

class EventId2<T1, T2>(private val group: EventLogGroup, private val eventId: String, private val field1: EventField<T1>, private val field2: EventField<T2>) {
  fun log(value1: T1, value2: T2) {
    val data = FeatureUsageData()
    field1.addData(data, value1)
    field2.addData(data, value2)
    FeatureUsageLogger.log(group, eventId, data.build())
  }
}

class EventId3<T1, T2, T3>(private val group: EventLogGroup, private val eventId: String, private val field1: EventField<T1>, private val field2: EventField<T2>, private val field3: EventField<T3>) {
  fun log(value1: T1, value2: T2, value3: T3) {
    val data = FeatureUsageData()
    field1.addData(data, value1)
    field2.addData(data, value2)
    field3.addData(data, value3)
    FeatureUsageLogger.log(group, eventId, data.build())
  }
}

open class VarargEventId(private val group: EventLogGroup, private val eventId: String, private vararg val fields: EventField<*>) {
  fun log(vararg pairs: EventPair<*>) {
    val data = FeatureUsageData()
    for (pair in pairs) {
      if (pair.field !in fields) throw IllegalArgumentException("Field not in fields for this event ID")
      @Suppress("UNCHECKED_CAST")
      (pair.field as EventField<Any?>).addData(data, pair.data)
    }
    FeatureUsageLogger.log(group, eventId, data.build())
  }
}
