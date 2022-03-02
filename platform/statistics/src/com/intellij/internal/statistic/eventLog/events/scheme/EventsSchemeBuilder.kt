// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import java.util.regex.Pattern

object EventsSchemeBuilder {

  val pluginInfoFields = setOf(
    FieldDescriptor("plugin", setOf("{util#plugin}")),
    FieldDescriptor("plugin_type", setOf("{util#plugin_type}")),
    FieldDescriptor("plugin_version", setOf("{util#plugin_version}"))
  )

  private val classValidationRuleNames = setOf("class_name", "dialog_class", "quick_fix_class_name",
                                               "run_config_factory", "tip_info", "run_config_factory",
                                               "run_config_id", "facets_type", "registry_key")
  private val classValidationRules = classValidationRuleNames.map { "{util#$it}" }

  private fun fieldSchema(field: EventField<*>, fieldName: String, eventName: String, groupId: String): Set<FieldDescriptor> {
    if (field.name.contains(".")) {
      throw IllegalStateException("Field name should not contains dots, because dots are used to express hierarchy. " +
                                  "Group=$groupId, event=$eventName, field=${field.name}")
    }

    return when (field) {
      EventFields.PluginInfo,
      EventFields.PluginInfoFromInstance, // todo extract marker trait for delegates
      EventFields.PluginInfoByDescriptor,
      -> pluginInfoFields
      is ObjectEventField -> buildObjectEvenScheme(fieldName, field.fields, eventName, groupId)
      is ObjectListEventField -> buildObjectEvenScheme(fieldName, field.fields, eventName, groupId)
      is ListEventField<*> -> {
        if (field is StringListEventField.ValidatedByInlineRegexp) {
          validateRegexp(field.regexp)
        }
        buildFieldDescriptors(fieldName, field.validationRule, FieldDataType.ARRAY)
      }
      is PrimitiveEventField -> {
        if (field is StringEventField.ValidatedByInlineRegexp) {
          validateRegexp(field.regexp)
        }
        if (field is RegexpIntEventField) {
          validateRegexp(field.regexp)
        }

        buildFieldDescriptors(fieldName, field.validationRule, FieldDataType.PRIMITIVE)
      }
    }
  }

  private fun buildFieldDescriptors(fieldName: String, validationRules: List<String>, fieldDataType: FieldDataType): Set<FieldDescriptor> {
    val fields = mutableSetOf(FieldDescriptor(fieldName, validationRules.toSet(), fieldDataType))
    if (validationRules.any { it in classValidationRules }) {
      fields.addAll(pluginInfoFields)
    }
    return fields
  }

  private fun validateRegexp(regexp: String) {
    if (regexp == ".*") {
      throw IllegalStateException("Regexp should be more strict to prevent accidentally reporting sensitive data.")
    }
    Pattern.compile(regexp)
  }

  private fun buildObjectEvenScheme(fieldName: String, fields: Array<out EventField<*>>,
                                    eventName: String, groupId: String): Set<FieldDescriptor> {
    val fieldsDescriptors = mutableSetOf<FieldDescriptor>()
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
    result.sortBy(GroupDescriptor::id)
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
        .toSet()
      val groupDescriptor = GroupDescriptor(group.id, groupType, group.version, eventsDescriptors, collectorClass.name)
      result.add(groupDescriptor)
    }
    return result
  }

  private fun validateGroupId(collector: FeatureUsagesCollector) {
    try {
      // get group id to check that either group or group id is overridden
      @Suppress("DEPRECATION")
      collector.groupId
    }
    catch (e: IllegalStateException) {
      throw IllegalStateException(e.message + " in " + collector.javaClass.name)
    }
  }

  private fun buildFields(events: List<BaseEventId>, eventName: String, groupId: String): Set<FieldDescriptor> {
    return events.flatMap { it.getFields() }
      .flatMap { field -> fieldSchema(field, field.name, eventName, groupId) }
      .groupBy { it.path }
      .map { (name, values) ->
        val type = defineDataType(values, name, eventName, groupId)
        FieldDescriptor(name, values.flatMap { it.value }.toSet(), type)
      }
      .toSet()
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
