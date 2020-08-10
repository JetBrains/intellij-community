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
import org.jetbrains.annotations.NonNls
import java.awt.event.InputEvent
import kotlin.reflect.KProperty

data class FusInputEvent(val inputEvent: InputEvent?, val place: String?) {
  companion object {
    @JvmStatic
    fun from(actionEvent: AnActionEvent?): FusInputEvent? = actionEvent?.let { FusInputEvent(it.inputEvent, it.place) }
  }
}

sealed class EventField<T> {
  abstract val name: String
  abstract fun addData(fuData: FeatureUsageData, value: T)

  infix fun with(data: T): EventPair<T> = EventPair(this, data)
}

abstract class PrimitiveEventField<T> : EventField<T>() {
  abstract val validationRule: List<String>
}

data class EventPair<T>(val field: EventField<T>, val data: T) {
  fun addData(featureUsageData: FeatureUsageData) = field.addData(featureUsageData, data)
}

data class StringEventField(override val name: String): PrimitiveEventField<String?>() {
  var customRuleId: String? = null
    private set
  var customEnumId: String? = null
    private set

  override fun addData(fuData: FeatureUsageData, value: String?) {
    if (value != null) {
      fuData.addData(name, value)
    }
  }

  fun withCustomRule(@NonNls id: String): StringEventField {
    customRuleId = id
    return this
  }

  fun withCustomEnum(@NonNls id: String): StringEventField {
    customEnumId = id
    return this
  }

  override val validationRule: List<String>
    get() = if (customRuleId != null)
      listOf("{util#${customRuleId}}")
    else if (customEnumId != null)
      listOf("{enum#${customEnumId}}")
    else
      emptyList()
}

data class IntEventField(override val name: String): PrimitiveEventField<Int>() {
  override val validationRule: List<String>
    get() =  listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, value)
  }
}

data class LongEventField(override val name: String): PrimitiveEventField<Long>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Long) {
    fuData.addData(name, value)
  }
}

data class BooleanEventField(override val name: String): PrimitiveEventField<Boolean>() {
  override val validationRule: List<String>
    get() = listOf("{enum#boolean}")

  override fun addData(fuData: FeatureUsageData, value: Boolean) {
    fuData.addData(name, value)
  }
}

data class EnumEventField<T : Enum<*>>(override val name: String,
                                       private val enumClass: Class<T>,
                                       private val transform: (T) -> String): PrimitiveEventField<T>() {
  override fun addData(fuData: FeatureUsageData, value: T) {
    fuData.addData(name, transform(value))
  }

  override val validationRule: List<String>
    get() = enumClass.enumConstants.map(transform)
}

data class StringListEventField(override val name: String): PrimitiveEventField<List<String>>() {
  var customRuleId: String? = null
    private set

  override fun addData(fuData: FeatureUsageData, value: List<String>) {
    fuData.addData(name, value)
  }

  fun withCustomRule(id: String): StringListEventField {
    customRuleId = id
    return this
  }

  override val validationRule: List<String>
    get() = if (customRuleId != null) listOf("{util#${customRuleId}}") else emptyList()
}

data class ClassEventField(override val name: String): PrimitiveEventField<Class<*>?>() {
  override fun addData(fuData: FeatureUsageData, value: Class<*>?) {
    if (value != null) {
      val pluginInfo = getPluginInfo(value)
      fuData.addData(name, if (pluginInfo.isSafeToReport()) value.name else "third.party")
    }
  }

  override val validationRule: List<String>
    get() = listOf("{util#class_name}")
}

class ObjectEventField(override val name: String, vararg val fields: EventField<*>) : EventField<ObjectEventData>() {
  constructor(name: String, description: ObjectDescription) : this(name, *description.getFields())

  override fun addData(fuData: FeatureUsageData, value: ObjectEventData) {
    fuData.addObjectData(name, value.buildObjectData(fields))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ObjectEventField

    if (name != other.name) return false
    if (!fields.contentEquals(other.fields)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + fields.contentHashCode()
    return result
  }
}

abstract class ObjectDescription {
  private val eventDelegates = ArrayList<EventFieldDelegate<*>>()

  fun <T> field(eventField: EventField<T>): EventFieldDelegate<T> {
    val delegate = EventFieldDelegate(eventField)
    eventDelegates.add(delegate)
    return delegate
  }

  fun getPairs(): Array<EventPair<*>> {
    return eventDelegates.mapNotNull { it.getPair() }.toTypedArray()
  }

  fun getFields(): Array<EventField<*>> {
    return eventDelegates.map { it.eventField }.toTypedArray()
  }

  companion object {
    inline fun <O : ObjectDescription> build(creator: () -> O, filler: O.() -> Unit): ObjectEventData {
      val obj = creator()
      filler(obj)
      return ObjectEventData(*obj.getPairs())
    }
  }
}

class ObjectEventData(private vararg val values: EventPair<*>) {
  fun buildObjectData(allowedFields: Array<out EventField<*>>): Map<String, Any> {
    val data = FeatureUsageData()
    for (eventPair in values) {
      val eventField = eventPair.field
      if (eventField !in allowedFields) throw IllegalArgumentException("Field ${eventField.name} is not in allowed object fields")
      eventPair.addData(data)
    }
    return data.build()
  }
}

class EventFieldDelegate<T>(val eventField: EventField<T>) {
  private var fieldValue: T? = null

  operator fun getValue(thisRef: ObjectDescription, property: KProperty<*>): T? = fieldValue

  operator fun setValue(thisRef: ObjectDescription, property: KProperty<*>, value: T?) {
    fieldValue = value
  }

  fun getPair(): EventPair<T>? {
    val v = fieldValue
    if (v != null) {
      return EventPair(eventField, v)
    }
    else {
      return null
    }
  }
}

class ObjectListEventField(override val name: String, vararg val fields: EventField<*>) : EventField<List<ObjectEventData>>() {
  constructor(name: String, description: ObjectDescription) : this(name, *description.getFields())

  override fun addData(fuData: FeatureUsageData, value: List<ObjectEventData>) {
    fuData.addListObjectData(name, value.map { it.buildObjectData(fields) })
  }
}

object EventFields {
  @JvmStatic
  fun String(@NonNls name: String): StringEventField = StringEventField(name)

  @JvmStatic
  fun Int(@NonNls name: String): IntEventField = IntEventField(name)

  @JvmStatic
  fun Long(@NonNls name: String): LongEventField = LongEventField(name)

  @JvmStatic
  fun Boolean(@NonNls name: String): BooleanEventField = BooleanEventField(name)

  @JvmStatic
  fun Class(@NonNls name: String): ClassEventField = ClassEventField(name)

  @JvmStatic
  @JvmOverloads
  fun <T : Enum<*>> Enum(@NonNls name: String, enumClass: Class<T>, transform: (T) -> String = { it.toString() }): EnumEventField<T> =
    EnumEventField(name, enumClass, transform)

  inline fun <reified T : Enum<*>> Enum(@NonNls name: String, noinline transform: (T) -> String = { it.toString() }): EnumEventField<T> =
    EnumEventField(name, T::class.java, transform)

  @JvmStatic
  fun StringList(@NonNls name: String): StringListEventField = StringListEventField(name)

  @JvmField
  val InputEvent = object : PrimitiveEventField<FusInputEvent?>() {
    override val name = "input_event"
    override val validationRule: List<String>
      get() = listOf("{util#shortcut}")

    override fun addData(fuData: FeatureUsageData, value: FusInputEvent?) {
      if (value != null) {
        fuData.addInputEvent(value.inputEvent, value.place)
      }
    }
  }

  @JvmField
  val ActionPlace = object : PrimitiveEventField<String?>() {
    override val name: String = "place"
    override val validationRule: List<String>
      get() = listOf("{util#place}")

    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addPlace(value)
    }
  }

  //will be replaced with ObjectEventField in the future
  @JvmField
  val PluginInfo = object : PrimitiveEventField<PluginInfo>() {
    override val name = "plugin_type"
    override val validationRule: List<String>
      get() = listOf("plugin_info")

    override fun addData(fuData: FeatureUsageData, value: PluginInfo) {
      fuData.addPluginInfo(value)
    }
  }

  //will be replaced with ObjectEventField in the future
  @JvmField
  val PluginInfoFromInstance = object : PrimitiveEventField<Any>() {
    override val name = "plugin_type"
    override val validationRule: List<String>
      get() = listOf("plugin_info")

    override fun addData(fuData: FeatureUsageData, value: Any) {
      fuData.addPluginInfo(getPluginInfo(value::class.java))
    }
  }

  @JvmField
  val AnonymizedPath = object : PrimitiveEventField<String?>() {
    override val validationRule: List<String>
      get() = listOf("{util#hash}")

    override val name = "file_path"
    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addAnonymizedPath(value)
    }
  }

  @JvmField
  val Language = object : PrimitiveEventField<Language?>() {
    override val name = "lang"
    override val validationRule: List<String>
      get() = listOf("{util#lang}")

    override fun addData(fuData: FeatureUsageData, value: Language?) {
      fuData.addLanguage(value)
    }
  }

  @JvmField
  val CurrentFile = object : PrimitiveEventField<Language?>() {
    override val name = "current_file"
    override val validationRule: List<String>
      get() = listOf("{util#current_file}")

    override fun addData(fuData: FeatureUsageData, value: Language?) {
      fuData.addCurrentFile(value)
    }
  }

  @JvmField
  val Version = object : PrimitiveEventField<String?>() {
    override val name: String = "version"
    override val validationRule: List<String>
      get() = listOf("{regexp#version}")

    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addVersionByString(value)
    }
  }

  @JvmStatic
  fun createAdditionalDataField(groupId: String, eventId: String): ObjectEventField {
    val additionalFields = mutableListOf<EventField<*>>()
    for (ext in FeatureUsageCollectorExtension.EP_NAME.extensions) {
      if (ext.groupId == groupId && ext.eventId == eventId) {
        for (field in ext.extensionFields) {
          if (field != null) {
            additionalFields.add(field)
          }
        }
      }
    }
    return ObjectEventField("additional", *additionalFields.toTypedArray())
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
