// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.internal.statistic.utils.StatisticsUtil.roundLogarithmic
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NonNls
import java.security.InvalidParameterException
import kotlin.reflect.KProperty

// region Base low-level fields

sealed class EventField<T> {
  abstract val name: String
  abstract val description: String?

  open val shouldBeAnonymized: Boolean
    get() = false

  abstract fun addData(fuData: FeatureUsageData, value: T)

  @Contract(pure = true)
  infix fun with(data: T): EventPair<T> = EventPair(this, data)
}

abstract class PrimitiveEventField<T>(@NonNls override val description: String? = null) : EventField<T>() {
  abstract val validationRule: List<String>
}

abstract class ListEventField<T> : EventField<List<T>>() {
  abstract val validationRule: List<String>
}

data class EventPair<T>(val field: EventField<T>, val data: T) {
  fun addData(featureUsageData: FeatureUsageData): Unit = field.addData(featureUsageData, data)
}

// endregion Base low-level fields

abstract class StringEventField(override val name: String) : PrimitiveEventField<String?>() {
  override fun addData(fuData: FeatureUsageData, value: String?) {
    if (value != null) {
      fuData.addData(name, value)
    }
  }

  data class ValidatedByAllowedValues(@NonNls @EventFieldName override val name: String,
                                      val allowedValues: List<String>,
                                      @NonNls override val description: String? = null) : StringEventField(name) {
    constructor(name: String, allowedValues: List<String>) : this(name, allowedValues, null)

    override val validationRule: List<String>
      get() = listOf("{enum:${allowedValues.joinToString("|")}}")
  }

  data class ValidatedByEnum(@NonNls @EventFieldName override val name: String,
                             @NonNls val enumRef: String,
                             @NonNls override val description: String? = null) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{enum#$enumRef}")
  }

  data class ValidatedByCustomValidationRule @JvmOverloads constructor(
    @NonNls @EventFieldName  override val name: String,
    @NonNls val customValidationRule: Class<out CustomValidationRule>,
    @NonNls override val description: String? = null
  ) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{util#${CustomValidationRule.getRuleId(customValidationRule)}}")
  }

  data class ValidatedByRegexp @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                               @NonNls val regexpRef: String,
                               @NonNls override val description: String? = null) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{regexp#$regexpRef}")
  }

  data class ValidatedByInlineRegexp @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                                     @NonNls val regexp: String,
                                     @NonNls override val description: String? = null) : StringEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{regexp:$regexp}")
  }
}

// region Numeric fields

// region Int fields

data class IntEventField(override val name: String, @NonNls override val description: String? = null) : PrimitiveEventField<Int>() {

  constructor(name: String) : this(name, null)

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, value)
  }
}

data class RegexpIntEventField @JvmOverloads constructor(override val name: String,
                               @NonNls val regexp: String,
                               @NonNls override val description: String? = null) : PrimitiveEventField<Int>() {
  override val validationRule: List<String>
    get() = listOf("{regexp:$regexp}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, value)
  }
}

data class RoundedIntEventField(override val name: String,
                                @NonNls override val description: String? = null) : PrimitiveEventField<Int>() {
  constructor(name: String) : this(name, null)

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, StatisticsUtil.roundToPowerOfTwo(value))
  }
}

/**
 * @throws InvalidParameterException if bounds parameter is empty or not sorted in ascending order or contains non-unique values
 * */
internal data class BoundedIntEventField(override val name: String,
                                         val bounds: IntArray,
                                         @NonNls override val description: String? = null) : PrimitiveEventField<Int>() {
  init {
    if (bounds.isEmpty()) throw InvalidParameterException("Bounds array should not be empty")
    if ((1..<bounds.size).any { bounds[it] <= bounds[it - 1] })
      throw InvalidParameterException("Bounds array should be sorted in ascending order and all values should be unique")
  }

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, StatisticsUtil.roundToUpperBoundInternal(value, bounds))
  }
}

/**
 * @throws InvalidParameterException if range parameter is empty or contains more than 500 values
 * */
internal data class LimitedIntEventField(override val name: String,
                                         val range: IntRange,
                                         @NonNls override val description: String? = null) : PrimitiveEventField<Int>() {
  init {
    if (range.isEmpty()) throw InvalidParameterException("Range should not be empty")
    if (range.last - range.first - 1 > 500) throw InvalidParameterException("Range should not contain more than 500 elements")
  }

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    val boundedValue = value.coerceIn(range.first, range.last)
    fuData.addData(name, boundedValue)
  }
}

internal data class LogarithmicIntEventField(override val name: String,
                                             @NonNls override val description: String? = null) : PrimitiveEventField<Int>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, value.roundLogarithmic())
  }
}

// endregion Int fields

// region Long fields

data class LongEventField(@NonNls @EventFieldName override val name: String,
                          @NonNls override val description: String? = null) : PrimitiveEventField<Long>() {
  constructor(name: String) : this(name, null)

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Long) {
    fuData.addData(name, value)
  }
}

data class RoundedLongEventField @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                                 @NonNls override val description: String? = null) : PrimitiveEventField<Long>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Long) {
    fuData.addData(name, StatisticsUtil.roundToPowerOfTwo(value))
  }
}

/**
 * @throws InvalidParameterException if bounds parameter is empty or not sorted in ascending order or contains non-unique values
 * */
internal data class BoundedLongEventField(@NonNls @EventFieldName override val name: String,
                                          val bounds: LongArray,
                                          @NonNls override val description: String? = null) : PrimitiveEventField<Long>() {
  init {
    if (bounds.isEmpty()) throw InvalidParameterException("Bounds array should not be empty")
    if ((1..<bounds.size).any { bounds[it] <= bounds[it - 1] })
      throw InvalidParameterException("Bounds array should be sorted in ascending order and all values should be unique")
  }

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Long) {
    fuData.addData(name, StatisticsUtil.roundToUpperBoundInternal(value, bounds))
  }
}

internal data class LogarithmicLongEventField(override val name: String,
                                              @NonNls override val description: String? = null) : PrimitiveEventField<Long>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Long) {
    fuData.addData(name, value.roundLogarithmic())
  }
}

// endregion Long fields

data class FloatEventField @JvmOverloads constructor(override val name: String,
                           @NonNls override val description: String? = null) : PrimitiveEventField<Float>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#float}")

  override fun addData(fuData: FeatureUsageData, value: Float) {
    fuData.addData(name, value)
  }
}

data class DoubleEventField(@NonNls @EventFieldName override val name: String,
                            @NonNls override val description: String? = null) : PrimitiveEventField<Double>() {
  constructor(name: String) : this(name, null)

  override val validationRule: List<String>
    get() = listOf("{regexp#float}")

  override fun addData(fuData: FeatureUsageData, value: Double) {
    fuData.addData(name, value)
  }
}

// endregion Numeric fields

data class BooleanEventField(@NonNls @EventFieldName override val name: String,
                             @NonNls override val description: String? = null) : PrimitiveEventField<Boolean>() {
  constructor(name: String) : this(name, null)

  override val validationRule: List<String>
    get() = listOf("{enum#boolean}")

  override fun addData(fuData: FeatureUsageData, value: Boolean) {
    fuData.addData(name, value)
  }
}

data class AnonymizedEventField @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                                @NonNls override val description: String? = null) : PrimitiveEventField<String?>() {
  override val shouldBeAnonymized: Boolean = true

  override val validationRule: List<String>
    get() = listOf("{regexp#hash}")

  override fun addData(fuData: FeatureUsageData, value: String?) {
    fuData.addAnonymizedValue(name, value)
  }
}

data class AnonymizedListEventField @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                                    @NonNls override val description: String? = null) : StringListEventField(name) {
  override val shouldBeAnonymized: Boolean
    get() = true

  override val validationRule: List<String>
    get() = listOf("{regexp#hash}")

  override fun addData(fuData: FeatureUsageData, value: List<String>) {
    fuData.addAnonymizedValue(name, value)
  }
}

internal data class ShortAnonymizedEventField(@NonNls @EventFieldName override val name: String,
                                              @NonNls override val description: String? = null) : PrimitiveEventField<String?>() {
  override val shouldBeAnonymized: Boolean = true

  override val validationRule: List<String>
    get() = listOf("{regexp#short_hash}")

  override fun addData(fuData: FeatureUsageData, value: String?) {
    fuData.addAnonymizedValue(name, value, true)
  }
}

internal data class DatedShortAnonymizedEventField<T>(@NonNls @EventFieldName override val name: String,
                                                      @NonNls override val description: String? = null,
                                                      val dateAndValueProvider: (T) -> Pair<Long, String?>) : PrimitiveEventField<T>() {
  override val shouldBeAnonymized: Boolean = true

  override val validationRule: List<String>
    get() = listOf("{regexp#date_short_hash}")

  override fun addData(fuData: FeatureUsageData, value: T) {
    val (timestamp, toHash) = dateAndValueProvider.invoke(value)
    fuData.addDatedShortAnonymizedValue(name, timestamp, toHash)
  }
}

data class EnumEventField<T : Enum<*>>(@NonNls @EventFieldName override val name: String,
                                       private val enumClass: Class<T>,
                                       @NonNls override val description: String? = null,
                                       private val transform: (T) -> String) : PrimitiveEventField<T>() {
  constructor(name: String,
              enumClass: Class<T>,
              transform: (T) -> String) : this(name, enumClass, null, transform)

  override fun addData(fuData: FeatureUsageData, value: T) {
    fuData.addData(name, transform(value))
  }

  override val validationRule: List<String>
    get() = listOf("{enum:${enumClass.enumConstants.joinToString("|", transform = transform)}}")
}

data class NullableEnumEventField<T : Enum<*>>(@NonNls @EventFieldName override val name: String,
                                               private val enumClass: Class<T>,
                                               private val nullValue: String?,
                                               @NonNls override val description: String? = null,
                                               private val transform: (T) -> String) : PrimitiveEventField<T?>() {
  override fun addData(fuData: FeatureUsageData, value: T?) {
    if (value == null) {
      if (nullValue != null) fuData.addData(name, nullValue)
    }
    else {
      fuData.addData(name, transform(value))
    }
  }

  override val validationRule: List<String>
    get() {
      val enumValues = enumClass.enumConstants.joinToString("|", transform = transform)
      if (nullValue != null) return listOf("{enum:$enumValues|$nullValue}")
      return listOf("{enum:$enumValues}")
    }
}

data class LongListEventField @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                              @NonNls override val description: String? = null) : ListEventField<Long>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: List<Long>) {
    fuData.addListLongData(name, value)
  }
}

data class IntListEventField @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                             @NonNls override val description: String? = null) : ListEventField<Int>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: List<Int>) {
    fuData.addListNumberData(name, value)
  }
}

data class FloatListEventField @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                                                         @NonNls override val description: String? = null) : ListEventField<Float>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#float}")

  override fun addData(fuData: FeatureUsageData, value: List<Float>) {
    fuData.addListNumberData(name, value)
  }
}

data class EnumListEventField<T : Enum<*>>(@NonNls @EventFieldName override val name: String,
                                            @NonNls override val description: String? = null,
                                            private val enumClass: Class<T>,
                                            private val transform: (T) -> String) : ListEventField<T>() {
  override val validationRule: List<String>
    get() = listOf("{enum:${enumClass.enumConstants.joinToString("|", transform = transform)}}")

  override fun addData(fuData: FeatureUsageData, value: List<T>) {
    fuData.addData(name, value.map(transform))
  }
}

abstract class StringListEventField(@NonNls @EventFieldName override val name: String) : ListEventField<String>() {
  override fun addData(fuData: FeatureUsageData, value: List<String>) {
    fuData.addData(name, value)
  }

  data class ValidatedByAllowedValues @JvmOverloads constructor(@NonNls @EventFieldName  override val name: String,
                                      val allowedValues: List<String>,
                                      @NonNls override val description: String? = null) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{enum:${allowedValues.joinToString("|")}}")
  }

  data class ValidatedByEnum @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                             @NonNls val enumRef: String,
                             @NonNls override val description: String? = null) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{enum#$enumRef}")
  }

  data class ValidatedByCustomValidationRule @JvmOverloads constructor(
    @NonNls @EventFieldName  override val name: String,
    @NonNls val customValidationRule: Class<out CustomValidationRule>,
    @NonNls override val description: String? = null
  ) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{util#${CustomValidationRule.getRuleId(customValidationRule)}}")
  }

  data class ValidatedByRegexp @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                               @NonNls val regexpRef: String,
                               @NonNls override val description: String? = null) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{regexp#$regexpRef}")
  }

  data class ValidatedByInlineRegexp @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                                     @NonNls val regexp: String,
                                     @NonNls override val description: String? = null) : StringListEventField(name) {
    override val validationRule: List<String>
      get() = listOf("{regexp:$regexp}")
  }
}

private val classCheckAndTransform: (Class<*>) -> String = {
  if (getPluginInfo(it).isSafeToReport()) StringUtil.substringBeforeLast(it.name, "$\$Lambda", true) else "third.party"
}

data class ClassEventField @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                                                     @NonNls override val description: String? = null) : PrimitiveEventField<Class<*>?>() {

  override fun addData(fuData: FeatureUsageData, value: Class<*>?) {
    if (value == null) {
      return
    }
    fuData.addData(name, classCheckAndTransform(value))
  }

  override val validationRule: List<String>
    get() = listOf("{util#class_name}")
}

data class ClassListEventField @JvmOverloads constructor(override val name: String,
                                                         @NonNls override val description: String? = null) : ListEventField<Class<*>?>() {

  override fun addData(fuData: FeatureUsageData, value: List<Class<*>?>) {
    val classList = value.filterNotNull()
    if (classList.isEmpty()) {
      return
    }
    fuData.addData(name, classList.map(classCheckAndTransform))
  }

  override val validationRule: List<String>
    get() = listOf("{util#class_name}")
}

class ObjectEventField(@NonNls @EventFieldName override val name: String,
                       @NonNls override val description: String? = null,
                       vararg val fields: EventField<*>) : EventField<ObjectEventData>() {
  constructor(name: String, description: String? = null, structureDescription: ObjectDescription) : this(name, description, *structureDescription.getFields())
  constructor(name: String, structureDescription: ObjectDescription) : this(name, null, *structureDescription.getFields())
  constructor(name: String, vararg fields: EventField<*>) : this(name, null, *fields)

  override fun addData(fuData: FeatureUsageData, value: ObjectEventData) {
    fuData.addObjectData(name, value.buildObjectData(fuData.recorderId, fields))
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

  fun buildObjectData(recorderId: String, allowedFields: Array<out EventField<*>>): Map<String, Any> {
    val data = FeatureUsageData(recorderId)
    for (eventPair in values) {
      val eventField = eventPair.field
      if (eventField !in allowedFields) throw IllegalArgumentException("Field ${eventField.name} is not in allowed object fields")
      eventPair.addData(data)
    }
    return data.build()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ObjectEventData

    return values == other.values
  }

  override fun hashCode(): Int {
    return values.hashCode()
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

class ObjectListEventField @JvmOverloads constructor(@NonNls @EventFieldName override val name: String,
                           vararg val fields: EventField<*>,
                           @NonNls override val description: String? = null) : EventField<List<ObjectEventData>>() {
  constructor(name: String, description: ObjectDescription) : this(name, *description.getFields())

  override fun addData(fuData: FeatureUsageData, value: List<ObjectEventData>) {
    fuData.addListObjectData(name, value.map { it.buildObjectData(fuData.recorderId, fields) })
  }
}
