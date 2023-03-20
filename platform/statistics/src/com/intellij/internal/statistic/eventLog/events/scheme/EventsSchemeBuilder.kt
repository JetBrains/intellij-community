// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginDescriptor
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

  /**
   * @param recorder id of the recorder, only groups from that recorder will be used to build scheme.
   * If null, groups from all recorders will be used.
   * @param pluginId id of the plugin, only groups registered in that plugin will be used to build scheme.
   * If null, all registered groups will be used.
   * @param brokenPluginIds list of plugin ids, groups registered in this plugins will **not** be used to build scheme.
   * If null, all registered groups will be used.
   * Only applicable when `pluginId == null`
   */
  @JvmStatic
  @JvmOverloads
  fun buildEventsScheme(recorder: String?, pluginId: String? = null, brokenPluginIds: Set<String> = emptySet()): List<GroupDescriptor> {
    val result = mutableListOf<GroupDescriptor>()
    val counterCollectors = ArrayList<FeatureUsageCollectorInfo>()
    FUCounterUsageLogger.EP_NAME.processWithPluginDescriptor { counterUsageCollectorEP, descriptor: PluginDescriptor ->
      if (counterUsageCollectorEP.implementationClass != null) {
        val collectorPlugin = descriptor.pluginId.idString
        if ((pluginId == null && !brokenPluginIds.contains(collectorPlugin)) || pluginId == collectorPlugin) {
          val collector = ApplicationManager.getApplication().instantiateClass<FeatureUsagesCollector>(
            counterUsageCollectorEP.implementationClass, descriptor)
          counterCollectors.add(FeatureUsageCollectorInfo(collector, collectorPlugin))
        }
      }
    }
    result.addAll(collectGroupsFromExtensions("counter", counterCollectors, recorder))

    val stateCollectors = ArrayList<FeatureUsageCollectorInfo>()
    ApplicationUsagesCollector.EP_NAME.processWithPluginDescriptor { collector, descriptor ->
      val collectorPlugin = descriptor.pluginId.idString
      if ((pluginId == null && !brokenPluginIds.contains(collectorPlugin)) || pluginId == collectorPlugin) {
        stateCollectors.add(FeatureUsageCollectorInfo(collector, collectorPlugin))
      }
    }
    ProjectUsagesCollector.EP_NAME.processWithPluginDescriptor { collector, descriptor ->
      val collectorPlugin = descriptor.pluginId.idString
      if ((pluginId == null && !brokenPluginIds.contains(collectorPlugin)) || pluginId == collectorPlugin) {
        stateCollectors.add(FeatureUsageCollectorInfo(collector, collectorPlugin))
      }
    }
    result.addAll(collectGroupsFromExtensions("state", stateCollectors, recorder))
    result.sortBy(GroupDescriptor::id)
    return result
  }

  fun collectGroupsFromExtensions(groupType: String,
                                  collectors: Collection<FeatureUsageCollectorInfo>,
                                  recorder: String?): MutableCollection<GroupDescriptor> {
    val result = HashMap<String, GroupDescriptor>()
    for ((collector, plugin) in collectors) {
      val collectorClass = if (collector.javaClass.enclosingClass != null) collector.javaClass.enclosingClass else collector.javaClass
      validateGroupId(collector)
      val group = collector.group ?: continue
      if (recorder != null && group.recorder != recorder) continue
      val existingGroup = result[group.id]
      if (existingGroup != null && group.version != existingGroup.version) {
        throw IllegalStateException("If group is reused in multiple collectors classes (e.g Project and Application collector), " +
                                    "it should have the same version (group=${group.id})")
      }
      val existingScheme = existingGroup?.schema ?: HashSet()
      val eventsDescriptors = existingScheme + group.events.groupBy { it.eventId }
        .map { (eventName, events) -> EventDescriptor(eventName, buildFields(events, eventName, group.id)) }
        .toSet()
      result[group.id] = GroupDescriptor(group.id, groupType, group.version, eventsDescriptors, collectorClass.name, group.recorder, plugin)
    }
    return result.values
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

  data class FeatureUsageCollectorInfo(val collector: FeatureUsagesCollector,
                                       val pluginId: String)
}
