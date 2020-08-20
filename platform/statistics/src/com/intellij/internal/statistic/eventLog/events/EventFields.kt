// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.lang.Language
import org.jetbrains.annotations.NonNls

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