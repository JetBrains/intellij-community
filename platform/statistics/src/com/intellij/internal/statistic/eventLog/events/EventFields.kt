// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.Version
import org.jetbrains.annotations.NonNls
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

@Suppress("FunctionName")
object EventFields {
  /**
   * Creates a field that will be validated by global regexp rule
   * @param name  name of the field
   * @param regexpRef reference to global regexp, e.g "integer" for "{regexp#integer}"
   */
  @JvmStatic
  fun StringValidatedByRegexp(@NonNls name: String, @NonNls regexpRef: String): StringEventField =
    StringEventField.ValidatedByRegexp(name, regexpRef)

  /**
   * Creates a field that will be validated by global enum rule
   * @param name  name of the field
   * @param enumRef reference to global enum, e.g "os" for "{enum#os}"
   */
  @JvmStatic
  fun StringValidatedByEnum(@NonNls name: String, @NonNls enumRef: String): StringEventField =
    StringEventField.ValidatedByEnum(name, enumRef)

  /**
   * Creates a field that will be validated by [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule]
   * @param name  name of the field
   * @param customRuleId ruleId that is accepted by [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule.acceptRuleId],
   * e.g "class_name" for "{util#class_name}"
   */
  @JvmStatic
  fun StringValidatedByCustomRule(@NonNls name: String, @NonNls customRuleId: String): StringEventField =
    StringEventField.ValidatedByCustomRule(name, customRuleId)

  /**
   * Creates a field that allows only a specific list of values
   * @param name  name of the field
   * @param allowedValues list of allowed values, e.g [ "bool", "int", "float"]
   */
  @JvmStatic
  fun String(@NonNls name: String, allowedValues: List<String>): StringEventField =
    StringEventField.ValidatedByAllowedValues(name, allowedValues)

  @JvmStatic
  fun Int(@NonNls name: String): IntEventField = IntEventField(name)

  /**
   * Creates an int field that will be validated by regexp rule
   * @param name  name of the field
   * @param regexp  regular expression, e.g "-?[0-9]{1,3}"
   * Please choose regexp carefully to avoid reporting any sensitive data.
   */
  @JvmStatic
  fun RegexpInt(@NonNls name: String, @NonNls regexp: String): RegexpIntEventField =
    RegexpIntEventField(name, regexp)

  /**
   * Rounds integer value to the next power of two.
   * Use it to anonymize sensitive information like the number of files in a project.
   * @see com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo
   */
  @JvmStatic
  fun RoundedInt(@NonNls name: String): RoundedIntEventField = RoundedIntEventField(name)

  @JvmStatic
  fun Long(@NonNls name: String): LongEventField = LongEventField(name)

  /**
   * Rounds long value to the next power of two.
   * Use it to anonymize sensitive information like the number of files in a project.
   * @see com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo
   */
  @JvmStatic
  fun RoundedLong(@NonNls name: String): RoundedLongEventField = RoundedLongEventField(name)

  @JvmStatic
  fun Float(@NonNls name: String): FloatEventField = FloatEventField(name)

  @JvmStatic
  fun Double(@NonNls name: String): DoubleEventField = DoubleEventField(name)

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

  /**
   * Creates a field for a list, each element of which will be validated by [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule]
   * @param name  name of the field
   * @param customRuleId ruleId that is accepted by [com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule.acceptRuleId],
   * e.g "class_name" for "{util#class_name}"
   */
  @JvmStatic
  fun StringListValidatedByCustomRule(@NonNls name: String, @NonNls customRuleId: String): StringListEventField =
    StringListEventField.ValidatedByCustomRule(name, customRuleId)

  /**
   * Creates a field for a list, each element of which will be validated by global enum rule
   * @param name  name of the field
   * @param enumRef reference to global enum, e.g "os" for "{enum#os}"
   */
  @JvmStatic
  fun StringListValidatedByEnum(@NonNls name: String, @NonNls enumRef: String): StringListEventField =
    StringListEventField.ValidatedByEnum(name, enumRef)

  /**
   * Creates a field for a list, each element of which will be validated by global regexp
   * @param name  name of the field
   * @param regexpRef reference to global regexp, e.g "integer" for "{regexp#integer}"
   */
  @JvmStatic
  fun StringListValidatedByRegexp(@NonNls name: String, @NonNls regexpRef: String): StringListEventField =
    StringListEventField.ValidatedByRegexp(name, regexpRef)

  /**
   * Creates a field for a list in which only a specific values are allowed
   * @param name  name of the field
   * @param allowedValues list of allowed values, e.g [ "bool", "int", "float"]
   */
  @JvmStatic
  fun StringList(@NonNls name: String, allowedValues: List<String>): StringListEventField =
    StringListEventField.ValidatedByAllowedValues(name, allowedValues)

  @JvmStatic
  fun LongList(@NonNls name: String): LongListEventField = LongListEventField(name)

  /**
   * Please choose regexp carefully to avoid reporting any sensitive data.
   */
  @JvmStatic
  fun StringValidatedByInlineRegexp(@NonNls name: String, @NonNls regexp: String): StringEventField =
    StringEventField.ValidatedByInlineRegexp(name, regexp)

  /**
   * Please choose regexp carefully to avoid reporting any sensitive data.
   */
  @JvmStatic
  fun StringListValidatedByInlineRegexp(@NonNls name: String, @NonNls regexp: String): StringListEventField =
    StringListEventField.ValidatedByInlineRegexp(name, regexp)

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
    override val validationRule: List<String>
      get() = listOf("{regexp#hash}")

    override val name = "file_path"
    override fun addData(fuData: FeatureUsageData, value: String?) {
      fuData.addAnonymizedPath(value)
    }
  }

  @JvmField
  val AnonymizedId = object : PrimitiveEventField<String?>() {
    override val validationRule: List<String>
      get() = listOf("{regexp#hash}")

    override val name = "anonymous_id"
    override fun addData(fuData: FeatureUsageData, value: String?) {
      value?.let {
        fuData.addAnonymizedId(value)
      }
    }
  }

  @JvmField
  val CodeWithMeClientId = object : PrimitiveEventField<String?>() {
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
  val TimeToShowMs = LongEventField("time_to_show")

  @JvmField
  val StartTime = LongEventField("start_time")

  /**
   * Logger merges successive events with identical group id, event id and event data fields except for fields listed here.
   *
   * @see com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.createEventsMergeStrategy
   */
  @JvmField
  val FieldsIgnoredByMerge: List<EventField<*>> = arrayListOf(StartTime)

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
