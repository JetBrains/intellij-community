// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.util.Version
import org.jetbrains.annotations.NonNls
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.security.InvalidParameterException
import kotlin.time.Duration
import kotlin.time.DurationUnit
import org.intellij.lang.annotations.Language as InjectedLanguage

internal object EventFieldIds {
  const val START_TIME_FIELD_ID = "start_time"

  /**
   * Logger merges successive events with identical group id, event id and event data fields except for fields listed here.
   *
   * @see com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.createEventsMergeStrategy
   */
  @JvmField
  val FieldsIgnoredByMerge: List<String> = arrayListOf(START_TIME_FIELD_ID)
}

@Suppress("FunctionName")
object EventFields {

  /**
   * Creates a field that will be validated by global regexp rule.
   * You can find existing regexp rules in the "APM/metadata" repository /global/rules.json.

   * @param name  name of the field
   * @param regexpRef reference to global regexp rule, e.g. "integer" for "{regexp#integer}".
   */
  @JvmStatic
  @JvmOverloads
  fun StringValidatedByRegexpReference(@NonNls name: String, @NonNls regexpRef: String, @NonNls description: String? = null): StringEventField {
    return StringEventField.ValidatedByRegexp(name, regexpRef, description)
  }

  /**
   * Creates a field that will be validated by global enum rule.
   * You can find existing enum rules in the "APM/metadata" repository /global/rules.json.
   *
   * @param name  name of the field
   * @param enumRef reference to global enum, e.g. "os" for "{enum#os}".
   */
  @JvmStatic
  fun StringValidatedByEnum(@NonNls name: String, @NonNls enumRef: String, @NonNls description: String?): StringEventField {
    return StringEventField.ValidatedByEnum(name, enumRef, description)
  }

  @JvmStatic
  fun StringValidatedByEnum(@NonNls name: String, @NonNls enumRef: String): StringEventField {
    return StringValidatedByEnum(name, enumRef, null)
  }

  /**
   * Creates a field that will be validated by [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule]
   * @param name  name of the field
   * @param customValidationRule inheritor of [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule],
   */
  @JvmStatic
  fun StringValidatedByCustomRule(@NonNls @EventFieldName name: String,
                                  customValidationRule: Class<out CustomValidationRule>,
                                  @NonNls description: String?): StringEventField =
    StringEventField.ValidatedByCustomValidationRule(name, customValidationRule, description)

  @JvmStatic
  fun StringValidatedByCustomRule(@NonNls @EventFieldName name: String,
                                  customValidationRule: Class<out CustomValidationRule>): StringEventField =
    StringValidatedByCustomRule(name, customValidationRule, null)

  /**
   * Creates a field that will be validated by [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule].
   * @param name  name of the field
   */
  inline fun <reified T : CustomValidationRule> StringValidatedByCustomRule(@NonNls name: String,
                                                                            @NonNls description: String? = null): StringEventField =
    StringValidatedByCustomRule(name, T::class.java, description)

  /**
   * Creates a field that allows only a specific list of values
   * @param name  name of the field
   * @param allowedValues list of allowed values, e.g [ "bool", "int", "float"]
   */
  @JvmStatic
  fun String(@NonNls @EventFieldName name: String, allowedValues: List<String>, @NonNls description: String?): StringEventField =
    StringEventField.ValidatedByAllowedValues(name, allowedValues, description)

  @JvmStatic
  fun String(@NonNls @EventFieldName name: String, allowedValues: List<String>): StringEventField = String(name, allowedValues, null)

  @JvmStatic
  fun Int(@NonNls @EventFieldName name: String, @NonNls description: String?): IntEventField = IntEventField(name, description)

  @JvmStatic
  fun Int(@NonNls @EventFieldName name: String): IntEventField = Int(name, null)

  /**
   * Creates an int field that will be validated by regexp rule
   * @param name  name of the field
   * @param regexp  regular expression, e.g "-?[0-9]{1,3}"
   * Please choose regexp carefully to avoid reporting any sensitive data.
   */
  @JvmStatic
  @JvmOverloads
  fun RegexpInt(@NonNls @EventFieldName name: String,
                @InjectedLanguage("RegExp") @NonNls regexp: String,
                @NonNls description: String? = null): RegexpIntEventField =
    RegexpIntEventField(name, regexp, description)

  /**
   * Rounds integer value to the next power of two.
   * Use it to anonymize sensitive information like the number of files in a project.
   * @see com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo
   */
  @JvmStatic
  fun RoundedInt(@NonNls @EventFieldName name: String, @NonNls description: String?): RoundedIntEventField = RoundedIntEventField(name, description)

  @JvmStatic
  fun RoundedInt(@NonNls @EventFieldName name: String): RoundedIntEventField = RoundedInt(name, null)

  /**
   * Rounds integer value to the upper bound from provided bounds.
   * If value is greater than upper bound from provided list - upper bound will be returned.
   * Use it to anonymize sensitive information like the number of files in a project.
   * @see com.intellij.internal.statistic.utils.StatisticsUtil.roundToUpperBoundInternal
   *
   * @param bounds non-empty array of unique sorted in ascending order integer values
   * @throws InvalidParameterException if bounds parameter is empty or not sorted in ascending order or contains non-unique values
   */
  @JvmStatic
  fun BoundedInt(@NonNls @EventFieldName name: String, bounds: IntArray, @NonNls description: String?): PrimitiveEventField<Int> =
    BoundedIntEventField(name, bounds, description)

  @JvmStatic
  fun BoundedInt(@NonNls @EventFieldName name: String, bounds: IntArray): PrimitiveEventField<Int> = BoundedInt(name, bounds, null)

  /**
   * Reports values from range and lower or upper bound of range if reported value is out of range.
   * Use it to anonymize sensitive information by avoiding report of extreme values.
   *
   * @param range non-empty range of possible values not bigger than 500 elements
   * @throws InvalidParameterException if range parameter is empty or contains more than 500 values
   * */
  @JvmOverloads
  fun LimitedInt(@NonNls @EventFieldName name: String, range: IntRange, @NonNls description: String? = null): PrimitiveEventField<Int> = LimitedIntEventField(name, range, description)

  /**
   * Rounds values in logarithmic scale.
   * Use it to anonymize sensitive information like the number of files in a project.
   * */
  @JvmOverloads
  fun LogarithmicInt(@NonNls @EventFieldName name: String, @NonNls description: String? = null): PrimitiveEventField<Int> = LogarithmicIntEventField(name, description)

  @JvmStatic
  @JvmOverloads
  fun Long(@NonNls @EventFieldName name: String, @NonNls description: String? = null): LongEventField = LongEventField(name, description)

  /**
   * Rounds long value to the next power of two.
   * Use it to anonymize sensitive information like the number of files in a project.
   * @see com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo
   */
  @JvmStatic
  @JvmOverloads
  fun RoundedLong(@NonNls @EventFieldName name: String, @NonNls description: String? = null): RoundedLongEventField = RoundedLongEventField(name, description)

  /**
   * Rounds long value to the upper bound from provided bounds.
   * If value is greater than upper bound from provided list - upper bound will be returned.
   * Use it to anonymize sensitive information like the number of files in a project.
   * @see com.intellij.internal.statistic.utils.StatisticsUtil.roundToUpperBoundInternal
   *
   * @param bounds non-empty array of unique sorted in ascending order long values
   * @throws InvalidParameterException if bounds parameter is empty or not sorted in ascending order or contains non-unique values
   */
  @JvmStatic
  @JvmOverloads
  fun BoundedLong(@NonNls @EventFieldName name: String, bounds: LongArray, @NonNls description: String? = null): PrimitiveEventField<Long> = BoundedLongEventField(name, bounds, description)

  /**
   * Rounds values in logarithmic scale.
   * Use it to anonymize sensitive information like the number of files in a project.
   * */
  @JvmOverloads
  fun LogarithmicLong(@NonNls @EventFieldName name: String, @NonNls description: String? = null): PrimitiveEventField<Long> = LogarithmicLongEventField(name, description)

  @JvmStatic
  @JvmOverloads
  fun Float(@NonNls @EventFieldName name: String, @NonNls description: String? = null): FloatEventField = FloatEventField(name, description)

  @JvmStatic
  fun Double(@NonNls @EventFieldName name: String, @NonNls description: String?): DoubleEventField = DoubleEventField(name, description)

  @JvmStatic
  fun Double(@NonNls @EventFieldName name: String): DoubleEventField = Double(name, null)

  @JvmStatic
  fun Boolean(@NonNls @EventFieldName name: String, @NonNls description: String?): BooleanEventField = BooleanEventField(name, description)

  @JvmStatic
  fun Boolean(@NonNls @EventFieldName name: String): BooleanEventField = Boolean(name, null)

  @JvmStatic
  fun Class(@NonNls @EventFieldName name: String, @NonNls description: String?): ClassEventField = ClassEventField(name, description)

  @JvmStatic
  fun Class(@NonNls @EventFieldName name: String): ClassEventField = ClassEventField(name)

  @JvmOverloads
  fun ClassList(@NonNls @EventFieldName name: String, @NonNls description: String? = null): ClassListEventField = ClassListEventField(name, description)

  val defaultEnumTransform: (Any) -> String = { it.toString() }

  @JvmStatic
  @JvmOverloads
  fun <T : Enum<*>> Enum(@NonNls @EventFieldName name: String,
                         enumClass: Class<T>,
                         @NonNls description: String,
                         transform: (T) -> String = defaultEnumTransform): EnumEventField<T> =
    EnumEventField(name, enumClass, description, transform)

  @JvmStatic
  @JvmOverloads
  fun <T : Enum<*>> Enum(@NonNls @EventFieldName name: String,
                         enumClass: Class<T>,
                         transform: (T) -> String = defaultEnumTransform): EnumEventField<T> =
    EnumEventField(name, enumClass, null, transform)

  inline fun <reified T : Enum<*>> Enum(@NonNls @EventFieldName name: String,
                                        @NonNls description: String,
                                        noinline transform: (T) -> String = defaultEnumTransform): EnumEventField<T> =
    EnumEventField(name, T::class.java, description, transform)

  inline fun <reified T : Enum<*>> Enum(@NonNls @EventFieldName name: String,
                                        noinline transform: (T) -> String = defaultEnumTransform): EnumEventField<T> =
    EnumEventField(name, T::class.java, null, transform)


  /**
   * Creates a field that allows nullable Enum
   * @param name  name of the field
   * @param enumClass class of Enum
   * @param nullValue if value is null and nullValue isn't null then nullValue is written
   * @param transform function that transforms Enum to String
   */
  @JvmStatic
  fun <T : Enum<*>> NullableEnum(@NonNls @EventFieldName name: String,
                                 enumClass: Class<T>,
                                 nullValue: String? = null,
                                 @NonNls description: String,
                                 transform: (T) -> String = defaultEnumTransform): NullableEnumEventField<T>
  = NullableEnumEventField(name, enumClass, nullValue, description, transform)

  @JvmStatic
  @JvmOverloads
  fun <T : Enum<*>> NullableEnum(@NonNls @EventFieldName name: String,
                                 enumClass: Class<T>,
                                 nullValue: String? = null,
                                 transform: (T) -> String = defaultEnumTransform): NullableEnumEventField<T>
    = NullableEnumEventField(name, enumClass, nullValue, null, transform)

  /**
   * Creates a field that allows nullable Enum
   * @param name  name of the field
   * @param nullValue if value is null and nullValue isn't null then nullValue is written
   * @param transform function that transforms Enum to String
   */
  inline fun <reified T : Enum<*>> NullableEnum(@NonNls @EventFieldName name: String,
                                                nullValue: String? = null,
                                                @NonNls description: String,
                                                noinline transform: (T) -> String = defaultEnumTransform): NullableEnumEventField<T> = NullableEnumEventField(
    name, T::class.java, nullValue, description, transform)

  inline fun <reified T : Enum<*>> NullableEnum(@NonNls @EventFieldName name: String,
                                                nullValue: String? = null,
                                                noinline transform: (T) -> String = defaultEnumTransform): NullableEnumEventField<T> = NullableEnumEventField(
    name, T::class.java, nullValue, null, transform)

  /**
   * Creates a field for a list, each element of which will be validated by [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule]
   * @param name  name of the field
   * @param customValidationRule inheritor of [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule]
   */
  @JvmStatic
  @JvmOverloads
  fun StringListValidatedByCustomRule(@NonNls @EventFieldName name: String,
                                      customValidationRule: Class<out CustomValidationRule>,
                                      @NonNls description: String? = null): StringListEventField =
    StringListEventField.ValidatedByCustomValidationRule(name, customValidationRule, description)

  /**
   * Creates a field for a list, each element of which will be validated by [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule]
   * @param name  name of the field
   */
  inline fun <reified T : CustomValidationRule> StringListValidatedByCustomRule(@NonNls name: String, @NonNls description: String? = null): StringListEventField =
    StringListValidatedByCustomRule(name, T::class.java, description)

  /**
   * Creates a field for a list, each element of which will be validated by global enum rule
   * @param name  name of the field
   * @param enumRef reference to global enum, e.g "os" for "{enum#os}"
   */
  @JvmStatic
  @JvmOverloads
  fun StringListValidatedByEnum(@NonNls @EventFieldName name: String, @NonNls enumRef: String, @NonNls description: String? = null): StringListEventField =
    StringListEventField.ValidatedByEnum(name, enumRef, description)

  /**
   * Creates a field for a list, each element of which will be validated by global regexp
   * @param name  name of the field
   * @param regexpRef reference to global regexp, e.g "integer" for "{regexp#integer}"
   */
  @JvmStatic
  @JvmOverloads
  fun StringListValidatedByRegexp(@NonNls @EventFieldName name: String, @NonNls regexpRef: String, @NonNls description: String? = null): StringListEventField =
    StringListEventField.ValidatedByRegexp(name, regexpRef, description)

  /**
   * Creates a field for a list in which only a specific values are allowed
   * @param name  name of the field
   * @param allowedValues list of allowed values, e.g [ "bool", "int", "float"]
   */
  @JvmStatic
  fun StringList(@NonNls @EventFieldName name: String, allowedValues: List<String>, @NonNls description: String?): StringListEventField =
    StringListEventField.ValidatedByAllowedValues(name, allowedValues, description)

  @JvmStatic
  fun StringList(@NonNls @EventFieldName name: String, allowedValues: List<String>): StringListEventField =
    StringList(name, allowedValues, null)


  @JvmStatic
  @JvmOverloads
  fun LongList(@NonNls @EventFieldName name: String, @NonNls description: String? = null): LongListEventField = LongListEventField(name, description)

  @JvmStatic
  @JvmOverloads
  fun IntList(@NonNls @EventFieldName name: String, @NonNls description: String? = null): IntListEventField = IntListEventField(name, description)

  @JvmStatic
  @JvmOverloads
  fun LanguagesList(@NonNls @EventFieldName name: String, @NonNls description: String? = null): PrimitiveEventField<Collection<Language>> =
    object : PrimitiveEventField<Collection<Language>>() {
    override val name = name
    override val description = description
    override val validationRule: List<String>
      get() = listOf("{util#lang}")

    override fun addData(fuData: FeatureUsageData, value: Collection<Language>) {
      fuData.addData(this.name, value.map { it.id })
    }
  }

  @JvmStatic
  @JvmOverloads
  inline fun <reified T : Enum<*>> EnumList(@NonNls @EventFieldName name: String, @NonNls description: String? = null, noinline transform: (T) -> String =
    defaultEnumTransform): EnumListEventField<T> = EnumListEventField(name, description, T::class.java, transform)

  /**
   * Please choose regexp carefully to avoid reporting any sensitive data.
   */
  @JvmStatic
  fun StringValidatedByInlineRegexp(@NonNls @EventFieldName name: String,
                                    @InjectedLanguage("RegExp") @NonNls regexp: String, @NonNls description: String?): StringEventField =
    StringEventField.ValidatedByInlineRegexp(name, regexp, description)

  @JvmStatic
  fun StringValidatedByInlineRegexp(@NonNls @EventFieldName name: String,
                                    @InjectedLanguage("RegExp") @NonNls regexp: String): StringEventField =
    StringValidatedByInlineRegexp(name, regexp, null)

  /**
   * Please choose regexp carefully to avoid reporting any sensitive data.
   */
  @JvmStatic
  @JvmOverloads
  fun StringListValidatedByInlineRegexp(@NonNls @EventFieldName name: String,
                                        @InjectedLanguage("RegExp") @NonNls regexp: String, @NonNls description: String? = null): StringListEventField =
    StringListEventField.ValidatedByInlineRegexp(name, regexp, description)

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
  val InputEventByAnAction = object : PrimitiveEventField<AnActionEvent?>() {
    override val name = "input_event"
    override val validationRule: List<String>
      get() = listOf("{util#shortcut}")

    override fun addData(fuData: FeatureUsageData, value: AnActionEvent?) {
      if (value != null) {
        fuData.addInputEvent(value)
      }
    }
  }

  @JvmField
  val InputEventByKeyEvent = object : PrimitiveEventField<KeyEvent?>() {
    override val name = "input_event"
    override val validationRule: List<String>
      get() = listOf("{util#shortcut}")

    override fun addData(fuData: FeatureUsageData, value: KeyEvent?) {
      if (value != null) {
        fuData.addInputEvent(value)
      }
    }
  }

  @JvmField
  val InputEventByMouseEvent = object : PrimitiveEventField<MouseEvent?>() {
    override val name = "input_event"
    override val validationRule: List<String>
      get() = listOf("{util#shortcut}")

    override fun addData(fuData: FeatureUsageData, value: MouseEvent?) {
      if (value != null) {
        fuData.addInputEvent(value)
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
  val PluginInfo = object : PrimitiveEventField<PluginInfo?>() {

    override val name: String
      get() = "plugin_type"

    override val validationRule: List<String>
      get() = listOf("plugin_info")

    override fun addData(
      fuData: FeatureUsageData,
      value: PluginInfo?,
    ) {
      fuData.addPluginInfo(value)
    }
  }

  @JvmField
  val AutomatedPluginVersion = object : PrimitiveEventField<PluginInfo?>() {
    override val name: String
      get() = "automated_plugin_version"

    override val validationRule: List<String>
      get() = listOf("{regexp#version}")

    override fun addData(
      fuData: FeatureUsageData,
      value: PluginInfo?,
    ) {
      fuData.addAutomatedPluginVersion(value)
    }
  }

  @JvmField
  val PluginInfoByDescriptor = object : PrimitiveEventField<IdeaPluginDescriptor>() {

    private val delegate
      get() = PluginInfo

    override val name: String
      get() = delegate.name

    override val validationRule: List<String>
      get() = delegate.validationRule

    override fun addData(
      fuData: FeatureUsageData,
      value: IdeaPluginDescriptor,
    ) {
      delegate.addData(fuData, getPluginInfoByDescriptor(value))
    }
  }

  //will be replaced with ObjectEventField in the future
  @JvmField
  val PluginInfoFromInstance = object : PrimitiveEventField<Any>() {

    private val delegate
      get() = PluginInfo

    override val name: String
      get() = delegate.name

    override val validationRule: List<String>
      get() = delegate.validationRule

    override fun addData(
      fuData: FeatureUsageData,
      value: Any,
    ) {
      delegate.addData(fuData, getPluginInfo(value::class.java))
    }
  }

  @JvmField
  val AnonymizedPath = object : PrimitiveEventField<String?>() {
    override val shouldBeAnonymized: Boolean = true

    override val validationRule: List<String>
      get() = listOf("{regexp#hash}")

    override val name = "file_path"
    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addAnonymizedPath(value)
    }
  }

  @JvmField
  val AnonymizedId = object : PrimitiveEventField<String?>() {
    override val shouldBeAnonymized: Boolean = true

    override val validationRule: List<String>
      get() = listOf("{regexp#hash}")

    override val name = "anonymous_id"
    override fun addData(fuData: FeatureUsageData, value: String?) {
      value?.let {
        fuData.addAnonymizedId(value)
      }
    }
  }

  /**
   * Can be used to report unique identifiers safely by anonymizing them using hash function and local salt
   * */
  @JvmStatic
  @JvmOverloads
  fun AnonymizedField(@NonNls @EventFieldName name: String, @NonNls description: String? = null): EventField<String?> = AnonymizedEventField(name, description)

  /**
   * Can be used to report unique identifiers safely by anonymizing them using hash function and local salt
   * */
  @JvmStatic
  @JvmOverloads
  fun AnonymizedList(@NonNls @EventFieldName name: String, @NonNls description: String? = null): AnonymizedListEventField = AnonymizedListEventField(name, description)

  /**
   * Can be used to report unique identifiers safely by anonymizing them using hash function and local salt
   *
   * Intended for ids which are unique inside some context (during one IDE run, during some process)
   *
   * Reduces amount of data reported from user, but increases probability of collisions
   * */
  @JvmStatic
  @JvmOverloads
  fun ShortAnonymizedField(@NonNls @EventFieldName name: String, @NonNls description: String? = null): EventField<String?> = ShortAnonymizedEventField(name, description)

  /**
   * Can be used to report unique identifiers safely by anonymizing them using hash function and local salt
   *
   * Intended for ids which are unique inside some context (during one IDE run, during some process),
   * but leaves ability to track id creation date
   *
   * Reduces amount of data reported from user, but increases probability of collisions
   *
   * @param dateAndValueProvider extracts timestamp and value to hash from reporting object
   * */
  @JvmStatic
  @JvmOverloads
  fun <T> DatedShortAnonymizedField(@NonNls @EventFieldName name: String,
                                    @NonNls description: String? = null,
                                    dateAndValueProvider: (T) -> Pair<Long, String?>): EventField<T> =
    DatedShortAnonymizedEventField(name, description, dateAndValueProvider)

  @JvmField
  val CodeWithMeClientId = object : PrimitiveEventField<String?>() {
    override val shouldBeAnonymized: Boolean = true

    override val validationRule: List<String>
      get() = listOf("{regexp#hash}")

    override val name: String = "client_id"
    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addClientId(value)
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

  @JvmStatic
  @JvmOverloads
  fun Language(@NonNls @EventFieldName name: String, @NonNls description: String? = null): PrimitiveEventField<Language?> {
    return object: PrimitiveEventField<Language?>() {
      override val name = name
      override val description = description
      override val validationRule: List<String>
        get() = listOf("{util#lang}")

      override fun addData(fuData: FeatureUsageData, value: Language?) {
        fuData.addLanguage(this.name, value)
      }
    }
  }


  @JvmField
  val LanguageById = object : PrimitiveEventField<String?>() {
    override val name = "lang"
    override val validationRule: List<String>
      get() = listOf("{util#lang}")

    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addLanguage(value)
    }
  }

  @JvmField
  val FileType = object : PrimitiveEventField<FileType?>() {
    override val name = "file_type"
    override val validationRule: List<String>
      get() = listOf("{util#file_type}")

    override fun addData(fuData: FeatureUsageData, value: FileType?) {
      value?.let {
        val type = getPluginInfo(it.javaClass)
        if (type.isSafeToReport()) {
          fuData.addData("file_type", it.name)
        }
        else {
          fuData.addData("file_type", "third.party")
        }
      }
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

  @JvmField
  val VersionByObject = object : PrimitiveEventField<Version?>() {
    override val name: String = "version"
    override val validationRule: List<String>
      get() = listOf("{regexp#version}")

    override fun addData(fuData: FeatureUsageData, value: Version?) {
      fuData.addVersion(value)
    }
  }

  @JvmField
  val Count = Int("count")

  @JvmField
  val Enabled = Boolean("enabled")

  @JvmField
  val DurationMs = LongEventField("duration_ms")

  @JvmField
  val Size = Int("size")

  @JvmField
  val IdeActivityIdField = object : PrimitiveEventField<StructuredIdeActivity>() {
    override val name: String = "ide_activity_id"

    override fun addData(fuData: FeatureUsageData, value: StructuredIdeActivity) {
      fuData.addData(name, value.id)
    }

    override val validationRule: List<String>
      get() = listOf("{regexp#integer}")
  }

  @JvmField
  val TimeToShowMs = LongEventField("time_to_show")

  @JvmField
  val StartTime = LongEventField(EventFieldIds.START_TIME_FIELD_ID)

  @JvmField
  val Dumb: BooleanEventField = Boolean("dumb")

  @JvmStatic
  @JvmOverloads
  fun createAdditionalDataField(groupId: String, eventId: String, @NonNls description: String? = null): ObjectEventField {
    val additionalFields = mutableListOf<EventField<*>>()

    val extensions = Cancellation.withNonCancelableSection().use {
      // Non-cancellable section, because this function is often used in
      // static initializer code of `object`, and any exception (namely, CancellationException)
      // breaks the object with ExceptionInInitializerError, and subsequent NoClassDefFoundError
      FeatureUsageCollectorExtension.EP_NAME.extensionsIfPointIsRegistered
    }
    for (ext in extensions) {
      if (ext.groupId == groupId && ext.eventId == eventId) {
        for (field in ext.extensionFields) {
          if (field != null) {
            additionalFields.add(field)
          }
        }
      }
    }
    return ObjectEventField("additional", description, *additionalFields.toTypedArray())
  }

  @JvmStatic
  @JvmOverloads
  fun createDurationField(unit: DurationUnit, fieldName: String, @NonNls description: String? = null): PrimitiveEventField<Duration> {
    return object : PrimitiveEventField<Duration>() {
      override val description: String? = description
      override val validationRule: List<String>
        get() = listOf("{regexp#integer}")

      override val name: String = fieldName
      override fun addData(fuData: FeatureUsageData, value: Duration) {
        fuData.addData(name, value.toInt(unit))
      }
    }
  }
}