// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.events

import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import java.util.regex.Pattern

object EventsSchemeBuilder {
  enum class FieldDataType { ARRAY, PRIMITIVE }
  data class FieldDescriptor(val path: String, val value: Set<String>, val dataType: FieldDataType = FieldDataType.PRIMITIVE)
  data class EventDescriptor(val event: String, val fields: Set<FieldDescriptor>)
  data class GroupDescriptor(val id: String,
                             val type: String,
                             val version: Int,
                             val schema: Set<EventDescriptor>,
                             val className: String)

  data class EventsScheme(val commitHash: String?, val buildNumber: String?, val scheme: List<GroupDescriptor>)

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
        if (field is StringListEventField.ValidatedByInlineRegexp) {
          validateRegexp(field.regexp)
        }
        hashSetOf(FieldDescriptor(fieldName, field.validationRule.toHashSet(), FieldDataType.ARRAY))
      }
      is PrimitiveEventField -> {
        if (field is StringEventField.ValidatedByInlineRegexp) {
          validateRegexp(field.regexp)
        }
        hashSetOf(FieldDescriptor(fieldName, field.validationRule.toHashSet()))
      }
    }
  }

  private fun validateRegexp(regexp: String) {
    if (regexp == ".*") {
      throw IllegalStateException("Regexp should be more strict to prevent accidentally reporting sensitive data.")
    }
    Pattern.compile(regexp)
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
  fun buildEventsScheme(): List<GroupDescriptor> = buildEventsScheme(null)

  /**
   * @param pluginId id of the plugin, only groups registered in that plugin will be used to build scheme.
   * If null, all registered groups will be used.
   */
  @JvmStatic
  fun buildEventsScheme(pluginId: String?): List<GroupDescriptor> {
    val result = mutableListOf<GroupDescriptor>()
    val counterCollectors = FUCounterUsageLogger.instantiateCounterCollectors(pluginId)
    result.addAll(collectGroupsFromExtensions("counter", counterCollectors))

    val stateCollectors = ArrayList<FeatureUsagesCollector>()
    ApplicationUsagesCollector.EP_NAME.processWithPluginDescriptor { collector, descriptor ->
      if (pluginId == null || pluginId == descriptor.pluginId.idString) {
        stateCollectors.add(collector)
      }
    }
    ProjectUsagesCollector.EP_NAME.processWithPluginDescriptor { collector, descriptor ->
      if (pluginId == null || pluginId == descriptor.pluginId.idString) {
        stateCollectors.add(collector)
      }
    }
    result.addAll(collectGroupsFromExtensions("state", stateCollectors))
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
        .map { (eventName, events) -> EventDescriptor(eventName, buildFields(events, eventName, group.id)) }
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
