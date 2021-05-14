// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.events

import com.google.gson.GsonBuilder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.lang.IllegalStateException
import kotlin.system.exitProcess

object EventsSchemeBuilder {
  data class FieldDescriptor(val path: String, val value: Set<String>)
  data class EventDescriptor(val event: String, val fields: Set<FieldDescriptor>)
  data class GroupDescriptor(val id: String,
                             val type: String,
                             val version: Int,
                             val schema: Set<EventDescriptor>,
                             val className: String)
  data class EventsScheme(val commitHash: String?, val scheme: List<GroupDescriptor>)

  private fun fieldSchema(field: EventField<*>, fieldName: String): Set<FieldDescriptor> {
    if (field == EventFields.PluginInfo || field == EventFields.PluginInfoFromInstance) {
      return hashSetOf(
        FieldDescriptor("plugin", hashSetOf("{util#plugin}")),
        FieldDescriptor("plugin_type", hashSetOf("{util#plugin_type}")),
        FieldDescriptor("plugin_version", hashSetOf("{util#plugin_version}"))
      )
    }

    return when (field) {
      is ObjectEventField -> buildObjectEvenScheme(fieldName, field.fields)
      is ObjectListEventField -> buildObjectEvenScheme(fieldName, field.fields)
      is PrimitiveEventField -> hashSetOf(FieldDescriptor(fieldName, field.validationRule.toHashSet()))
    }
  }

  private fun buildObjectEvenScheme(fieldName: String, fields: Array<out EventField<*>>): Set<FieldDescriptor> {
    val fieldsDescriptors = hashSetOf<FieldDescriptor>()
    for (eventField in fields) {
      fieldsDescriptors.addAll(fieldSchema(eventField, fieldName + "." + eventField.name))
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
        .map { (name, events) -> EventDescriptor(name, buildFields(events)) }
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

  private fun buildFields(events: List<BaseEventId>): HashSet<FieldDescriptor> {
    return events.flatMap { it.getFields() }
      .flatMap { field -> fieldSchema(field, field.name) }
      .groupBy { it.path }
      .map { (name, values) -> FieldDescriptor(name, values.flatMap { it.value }.toHashSet()) }
      .toHashSet()
  }
}

class EventsSchemeBuilderAppStarter : ApplicationStarter {
  override fun getCommandName(): String = "buildEventsScheme"

  override fun main(args: List<String>) {
    val outputFile = args.getOrNull(1)
    val pluginsFile = args.getOrNull(2)
    val eventsScheme = EventsSchemeBuilder.EventsScheme(System.getenv("INSTALLER_LAST_COMMIT_HASH"),
                                                        EventsSchemeBuilder.buildEventsScheme())
    val text = GsonBuilder().setPrettyPrinting().create().toJson(eventsScheme)
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
}
