// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.events

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.lang.reflect.Type
import kotlin.system.exitProcess

object EventsSchemeBuilder {
  enum class FieldDataType { ARRAY, PRIMITIVE }
  data class FieldDescriptor(val path: String, val value: Set<String>, val dataType: FieldDataType = FieldDataType.PRIMITIVE)
  data class EventDescriptor(val event: String, val fields: Set<FieldDescriptor>)
  data class GroupDescriptor(val id: String,
                             val type: String,
                             val version: Int,
                             val schema: Set<EventDescriptor>,
                             val className: String)
  data class EventsScheme(val commitHash: String?, val scheme: List<GroupDescriptor>)

  private fun fieldSchema(field: EventField<*>, fieldName: String, eventName: String, groupId: String): Set<FieldDescriptor> {
    if (field.name.contains(".")) {
      throw IllegalStateException("Field name should not contains dots, because dots are used to express hierarchy. " +
                                  "Group=$groupId, event=$eventName, field=${field.name}")
    }
    if (field == EventFields.PluginInfo || field == EventFields.PluginInfoFromInstance) {
      return hashSetOf(
        FieldDescriptor("plugin", hashSetOf("{util#plugin}")),
        FieldDescriptor("plugin_type", hashSetOf("{util#plugin_type}")),
        FieldDescriptor("plugin_version", hashSetOf("{util#plugin_version}"))
      )
    }

    return when (field) {
      is ObjectEventField -> buildObjectEvenScheme(fieldName, field.fields, eventName, groupId)
      is ObjectListEventField -> buildObjectEvenScheme(fieldName, field.fields, eventName, groupId)
      is ListEventField<*> -> {
        hashSetOf(FieldDescriptor(fieldName, field.validationRule.toHashSet(), FieldDataType.ARRAY))
      }
      is PrimitiveEventField -> hashSetOf(FieldDescriptor(fieldName, field.validationRule.toHashSet()))
    }
  }

  private fun buildObjectEvenScheme(fieldName: String, fields: Array<out EventField<*>>,
                                    eventName: String, groupId: String): Set<FieldDescriptor> {
    val fieldsDescriptors = hashSetOf<FieldDescriptor>()
    for (eventField in fields) {
      fieldsDescriptors.addAll(fieldSchema(eventField, fieldName + "." + eventField.name, eventName, groupId))
    }
    return fieldsDescriptors
  }

  @JvmStatic
  fun buildEventsScheme(): List<GroupDescriptor> {
    val result = mutableListOf<GroupDescriptor>()
    result.addAll(collectGroupsFromExtensions("counter", FUCounterUsageLogger.instantiateCounterCollectors()))
    result.addAll(collectGroupsFromExtensions("state", ApplicationUsagesCollector.EP_NAME.extensionList))
    result.addAll(collectGroupsFromExtensions("state", ProjectUsagesCollector.EP_NAME.extensionList))
    return result
  }

  fun collectGroupsFromExtensions(groupType: String,
                                  collectors: Collection<FeatureUsagesCollector>): MutableList<GroupDescriptor> {
    val result = mutableListOf<GroupDescriptor>()
    for (collector in collectors) {
      val collectorClass = if (collector.javaClass.enclosingClass != null) collector.javaClass.enclosingClass else collector.javaClass
      validateGroupId(collector)
      val group = collector.group ?: continue
      val eventsDescriptors = group.events.groupBy { it.eventId }
        .map { (eventName, events) -> EventDescriptor(eventName, buildFields(events, eventName, group.id )) }
        .toHashSet()
      val groupDescriptor = GroupDescriptor(group.id, groupType, group.version, eventsDescriptors, collectorClass.name)
      result.add(groupDescriptor)
    }
    return result
  }

  private fun validateGroupId(collector: FeatureUsagesCollector) {
    try {
      // get group id to check that either group or group id is overridden
      collector.groupId
    }
    catch (e: IllegalStateException) {
      throw IllegalStateException(e.message + " in " + collector.javaClass.name)
    }
  }

  private fun buildFields(events: List<BaseEventId>, eventName: String, groupId: String): HashSet<FieldDescriptor> {
    return events.flatMap { it.getFields() }
      .flatMap { field -> fieldSchema(field, field.name, eventName, groupId) }
      .groupBy { it.path }
      .map { (name, values) ->
        val type = defineDataType(values, name, eventName, groupId)
        FieldDescriptor(name, values.flatMap { it.value }.toHashSet(), type)
      }
      .toHashSet()
  }

  private fun defineDataType(values: List<FieldDescriptor>, name: String, eventName: String, groupId: String): FieldDataType {
    val dataType = values.first().dataType
    return if (values.any { it.dataType != dataType })
      throw IllegalStateException("Field couldn't have multiple types (group=$groupId, event=$eventName, field=$name)")
    else {
      dataType
    }
  }
}

class EventsSchemeBuilderAppStarter : ApplicationStarter {
  override fun getCommandName(): String = "buildEventsScheme"

  override fun main(args: List<String>) {
    val outputFile = args.getOrNull(1)
    val pluginsFile = args.getOrNull(2)
    val eventsScheme = EventsSchemeBuilder.EventsScheme(System.getenv("INSTALLER_LAST_COMMIT_HASH"),
                                                        EventsSchemeBuilder.buildEventsScheme())
    val text = GsonBuilder()
      .registerTypeAdapter(EventsSchemeBuilder.FieldDataType::class.java, FieldDataTypeSerializer)
      .setPrettyPrinting()
      .create()
      .toJson(eventsScheme)
    logEnabledPlugins(pluginsFile)
    if (outputFile != null) {
      FileUtil.writeToFile(File(outputFile), text)
    }
    else {
      println(text)
    }
    exitProcess(0)
  }

  private fun logEnabledPlugins(pluginsFile: String?) {
    val text = buildString {
      for (descriptor in PluginManagerCore.getLoadedPlugins()) {
        if (descriptor.isEnabled) {
          appendLine(descriptor.name)
        }
      }
    }
    if (pluginsFile != null) {
      FileUtil.writeToFile(File(pluginsFile), text)
    }
    else {
      println("Enabled plugins:")
      println(text)
    }
  }

  object FieldDataTypeSerializer : JsonSerializer<EventsSchemeBuilder.FieldDataType> {
    override fun serialize(src: EventsSchemeBuilder.FieldDataType?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
      if (src == EventsSchemeBuilder.FieldDataType.PRIMITIVE || src == null) {
        return context!!.serialize(null)
      }
      return context!!.serialize(src.name)
    }
  }
}
