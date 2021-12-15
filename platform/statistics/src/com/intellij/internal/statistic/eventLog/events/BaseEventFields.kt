// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.internal.statistic.utils.getPluginInfo
import org.jetbrains.annotations.NonNls
import kotlin.reflect.KProperty

sealed class EventField<T> {
  abstract val name: String
  abstract fun addData(fuData: FeatureUsageData, value: T)

  infix fun with(data: T): EventPair<T> = EventPair(this, data)
}

abstract class PrimitiveEventField<T> : EventField<T>() {
  abstract val validationRule: List<String>
}

abstract class ListEventField<T> : EventField<List<T>>() {
  abstract val validationRule: List<String>
}

data class EventPair<T>(val field: EventField<T>, val data: T) {
  fun addData(featureUsageData: FeatureUsageData) = field.addData(featureUsageData, data)
}

abstract class StringEventField(override val name: String) : PrimitiveEventField<String?>() {
  override fun addData(fuData: FeatureUsageData, value: String?) {
    if (value != null) {
      fuData.addData(name, value)
    }
  }

  data class ValidatedByAllowedValues(@NonNls override val name: String,
                                      val allowedValues: List<String>) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{enum:${allowedValues.joinToString("|")}}")
  }

  data class ValidatedByEnum(@NonNls override val name: String, @NonNls val enumRef: String) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{enum#$enumRef}")
  }

  data class ValidatedByCustomRule(@NonNls override val name: String,
                                   @NonNls val customRuleId: String) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{util#$customRuleId}")
  }

  data class ValidatedByRegexp(@NonNls override val name: String, @NonNls val regexpRef: String) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{regexp#$regexpRef}")
  }

  data class ValidatedByInlineRegexp(@NonNls override val name: String, @NonNls val regexp: String) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{regexp:$regexp}")
  }
}

data class IntEventField(override val name: String) : PrimitiveEventField<Int>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, value)
  }
}

data class RegexpIntEventField(override val name: String, @NonNls val regexp: String) : PrimitiveEventField<Int>() {
  override val validationRule: List<String>
    get() = listOf("{regexp:$regexp}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, value)
  }
}

data class RoundedIntEventField(override val name: String) : PrimitiveEventField<Int>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, StatisticsUtil.roundToPowerOfTwo(value))
  }
}

data class LongEventField(override val name: String): PrimitiveEventField<Long>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Long) {
    fuData.addData(name, value)
  }
}

data class RoundedLongEventField(override val name: String): PrimitiveEventField<Long>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Long) {
    fuData.addData(name, StatisticsUtil.roundToPowerOfTwo(value))
  }
}

data class FloatEventField(override val name: String): PrimitiveEventField<Float>() {
  override val validationRule: List<String>
    get() =  listOf("{regexp#float}")

  override fun addData(fuData: FeatureUsageData, value: Float) {
    fuData.addData(name, value)
  }
}

data class DoubleEventField(override val name: String): PrimitiveEventField<Double>() {
  override val validationRule: List<String>
    get() =  listOf("{regexp#float}")

  override fun addData(fuData: FeatureUsageData, value: Double) {
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

data class AnonymizedEventField(override val name: String): PrimitiveEventField<String?>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#hash}")

  override fun addData(fuData: FeatureUsageData, value: String?) {
    fuData.addAnonymizedValue(name, value)
  }
}

data class EnumEventField<T : Enum<*>>(override val name: String,
                                       private val enumClass: Class<T>,
                                       private val transform: (T) -> String): PrimitiveEventField<T>() {
  override fun addData(fuData: FeatureUsageData, value: T) {
    fuData.addData(name, transform(value))
  }

  override val validationRule: List<String>
    get() = listOf("{enum:${enumClass.enumConstants.joinToString("|", transform = transform)}}")
}

data class LongListEventField(override val name: String): ListEventField<Long>() {
  override val validationRule: List<String>
    get() =  listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: List<Long>) {
    fuData.addListLongData(name, value)
  }
}

abstract class StringListEventField(override val name: String) : ListEventField<String>() {
  override fun addData(fuData: FeatureUsageData, value: List<String>) {
    fuData.addData(name, value)
  }

  data class ValidatedByAllowedValues(@NonNls override val name: String,
                                      val allowedValues: List<String>) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{enum:${allowedValues.joinToString("|")}}")
  }

  data class ValidatedByEnum(@NonNls override val name: String, @NonNls val enumRef: String) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{enum#$enumRef}")
  }

  data class ValidatedByCustomRule(@NonNls override val name: String,
                                   @NonNls val customRuleId: String) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{util#$customRuleId}")
  }

  data class ValidatedByRegexp(@NonNls override val name: String, @NonNls val regexpRef: String) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{regexp#$regexpRef}")
  }

  data class ValidatedByInlineRegexp(@NonNls override val name: String, @NonNls val regexp: String) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{regexp:$regexp}")
  }
}

data class ClassEventField(override val name: String) : PrimitiveEventField<Class<*>>() {

  override fun addData(fuData: FeatureUsageData, value: Class<*>) {
    val pluginInfo = getPluginInfo(value)
    fuData.addData(name, if (pluginInfo.isSafeToReport()) value.name else "third.party")
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

class ObjectEventData(private val values: List<EventPair<*>>) {

  constructor(vararg values: EventPair<*>) : this(listOf(*values))

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

