// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.awt.event.InputEvent

data class FusInputEvent(val inputEvent: InputEvent?, val place: String?) {
  companion object {
    @JvmStatic
    fun from(actionEvent: AnActionEvent?): FusInputEvent? = actionEvent?.let { FusInputEvent(it.inputEvent, it.place) }
  }
}

abstract class EventField<T> {
  abstract val name: String
  abstract fun addData(fuData: FeatureUsageData, value: T)

  infix fun with(data: T): EventPair<T> = EventPair(this, data)
}

data class EventPair<T>(val field: EventField<T>, val data: T)

data class StringEventField(override val name: String): EventField<String?>() {
  var customRuleId: String? = null
    private set
  var customEnumId: String? = null
    private set

  override fun addData(fuData: FeatureUsageData, value: String?) {
    if (value != null) {
      fuData.addData(name, value)
    }
  }

  fun withCustomRule(id: String): StringEventField {
    customRuleId = id
    return this
  }

  fun withCustomEnum(id: String): StringEventField {
    customEnumId = id
    return this
  }
}

data class IntEventField(override val name: String): EventField<Int>() {
  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, value)
  }
}

data class LongEventField(override val name: String): EventField<Long>() {
  override fun addData(fuData: FeatureUsageData, value: Long) {
    fuData.addData(name, value)
  }
}

data class BooleanEventField(override val name: String): EventField<Boolean>() {
  override fun addData(fuData: FeatureUsageData, value: Boolean) {
    fuData.addData(name, value)
  }
}

data class EnumEventField<T : Enum<*>>(override val name: String,
                                       private val enumClass: Class<T>,
                                       private val transform: (T) -> String): EventField<T>() {
  override fun addData(fuData: FeatureUsageData, value: T) {
    fuData.addData(name, transform(value))
  }

  fun transformAllEnumConstants(): List<String> = enumClass.enumConstants.map(transform)
}

data class StringListEventField(override val name: String): EventField<List<String>>() {
  var customRuleId: String? = null
    private set

  override fun addData(fuData: FeatureUsageData, value: List<String>) {
    fuData.addData(name, value)
  }

  fun withCustomRule(id: String): StringListEventField {
    customRuleId = id
    return this
  }
}

object EventFields {
  @JvmStatic
  fun String(name: String): StringEventField = StringEventField(name)

  @JvmStatic
  fun Int(name: String): IntEventField = IntEventField(name)

  @JvmStatic
  fun Long(name: String): LongEventField = LongEventField(name)

  @JvmStatic
  fun Boolean(name: String): BooleanEventField = BooleanEventField(name)

  @JvmStatic
  @JvmOverloads
  fun <T : Enum<*>> Enum(name: String, enumClass: Class<T>, transform: (T) -> String = { it.toString() }): EnumEventField<T> =
    EnumEventField(name, enumClass, transform)

  inline fun <reified T : Enum<*>> Enum(name: String, noinline transform: (T) -> String = { it.toString() }): EnumEventField<T> =
    EnumEventField(name, T::class.java, transform)

  @JvmStatic
  fun StringList(name: String): StringListEventField = StringListEventField(name)

  @JvmField
  val InputEvent = object : EventField<FusInputEvent?>() {
    override val name = "input_event"
    override fun addData(fuData: FeatureUsageData, value: FusInputEvent?) {
      if (value != null) {
        fuData.addInputEvent(value.inputEvent, value.place)
      }
    }
  }

  @JvmField
  val ActionPlace = object : EventField<String?>() {
    override val name: String = "place"
    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addPlace(value)
    }
  }

  @JvmField
  val PluginInfo = object : EventField<PluginInfo>() {
    override val name = "plugin_type"
    override fun addData(fuData: FeatureUsageData, value: PluginInfo) {
      fuData.addPluginInfo(value)
    }
  }

  @JvmField
  val PluginInfoFromInstance = object : EventField<Any>() {
    override val name = "plugin_type"
    override fun addData(fuData: FeatureUsageData, value: Any) {
      fuData.addPluginInfo(getPluginInfo(value::class.java))
    }
  }

  @JvmField
  val AnonymizedPath = object : EventField<String?>() {
    override val name = "file_path"
    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addAnonymizedPath(value)
    }
  }

  @JvmField
  val CurrentFile = object : EventField<Language?>() {
    override val name = "current_file"
    override fun addData(fuData: FeatureUsageData, value: Language?) {
      fuData.addCurrentFile(value)
    }
  }

  @JvmField
  val Version = object : EventField<String?>() {
    override val name: String = "version"

    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addVersionByString(value)
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
  private val registeredEvents = mutableListOf<BaseEventId>()

  val events: List<BaseEventId> get() = registeredEvents

  private fun addToRegisteredEvents(eventId: BaseEventId) {
    registeredEvents.add(eventId)
    registeredEventIds.add(eventId.eventId)
  }

  fun registerEvent(eventId: String): EventId {
    return EventId(this, eventId).also { addToRegisteredEvents(it) }
  }

  fun <T1> registerEvent(eventId: String, eventField1: EventField<T1>): EventId1<T1> {
    return EventId1(this, eventId, eventField1).also { addToRegisteredEvents(it) }
  }

  fun <T1, T2> registerEvent(eventId: String, eventField1: EventField<T1>, eventField2: EventField<T2>): EventId2<T1, T2> {
    return EventId2(this, eventId, eventField1, eventField2).also { addToRegisteredEvents(it) }
  }

  fun <T1, T2, T3> registerEvent(eventId: String, eventField1: EventField<T1>, eventField2: EventField<T2>, eventField3: EventField<T3>): EventId3<T1, T2, T3> {
    return EventId3(this, eventId, eventField1, eventField2, eventField3).also { addToRegisteredEvents(it) }
  }

  fun registerVarargEvent(eventId: String, vararg fields: EventField<*>): VarargEventId {
    return VarargEventId(this, eventId, *fields).also { addToRegisteredEvents(it) }
  }

  internal fun validateEventId(eventId: String) {
    if (registeredEventIds.isNotEmpty() && eventId !in registeredEventIds) {
      throw IllegalArgumentException("Trying to report unregistered event ID $eventId to group $id")
    }
  }
}

abstract class BaseEventId(val eventId: String) {
  abstract fun getFields(): List<EventField<*>>
}

class EventId(private val group: EventLogGroup, eventId: String) : BaseEventId(eventId) {
  fun log() {
    FeatureUsageLogger.log(group, eventId)
  }

  fun log(project: Project?) {
    FeatureUsageLogger.log(group, eventId, FeatureUsageData().addProject(project).build())
  }

  override fun getFields(): List<EventField<*>> = emptyList()
}

class EventId1<T>(private val group: EventLogGroup, eventId: String, private val field1: EventField<T>) : BaseEventId(eventId) {
  fun log(value1: T) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1).build())
  }

  fun log(project: Project?, value1: T) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1).addProject(project).build())
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

class EventId2<T1, T2>(private val group: EventLogGroup, eventId: String, private val field1: EventField<T1>, private val field2: EventField<T2>) : BaseEventId(eventId) {
  fun log(value1: T1, value2: T2) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1, value2).build())
  }

  fun log(project: Project?, value1: T1, value2: T2) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1, value2).addProject(project).build())
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

class EventId3<T1, T2, T3>(private val group: EventLogGroup, eventId: String, private val field1: EventField<T1>, private val field2: EventField<T2>, private val field3: EventField<T3>) : BaseEventId(eventId) {
  fun log(value1: T1, value2: T2, value3: T3) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1, value2, value3).build())
  }

  fun log(project: Project?, value1: T1, value2: T2, value3: T3) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(value1, value2, value3).addProject(project).build())
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

class VarargEventId internal constructor(private val group: EventLogGroup, eventId: String, vararg fields: EventField<*>) : BaseEventId(eventId) {
  private val fields = fields.toMutableList()

  init {
    for (ext in FeatureUsageCollectorExtension.EP_NAME.extensions) {
      if (ext.groupId == group.id && ext.eventId == eventId) {
        for (field in ext.extensionFields) {
          if (field == null) {
            LOG.info("Null extension field returned from ${ext.javaClass.name}")
          }
          else {
            this.fields.add(field)
          }
        }
      }
    }
  }

  fun log(vararg pairs: EventPair<*>) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(*pairs).build())
  }

  fun log(project: Project?, vararg pairs: EventPair<*>) {
    FeatureUsageLogger.log(group, eventId, buildUsageData(*pairs).addProject(project).build())
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

  override fun getFields(): List<EventField<*>> = fields.toList()

  companion object {
    val LOG = Logger.getInstance(VarargEventId::class.java)
  }
}
