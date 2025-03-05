// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginDescriptor
import java.util.regex.Pattern

object EventsSchemeBuilder {

  val pluginInfoFields = setOf(
    FieldDescriptor("plugin", setOf("{util#plugin}"), false),
    FieldDescriptor("plugin_type", setOf("{util#plugin_type}"), false),
    FieldDescriptor("plugin_version", setOf("{util#plugin_version}"), false)
  )

  private val classValidationRuleNames = setOf("class_name", "dialog_class", "quick_fix_class_name",
                                               "run_config_factory", "tip_info", "run_config_factory",
                                               "run_config_id", "facets_type", "registry_key")
  private val classValidationRules = classValidationRuleNames.map { "{util#$it}" }

  private fun fieldSchema(field: EventField<*>, fieldName: String, eventName: String, groupId: String): Set<FieldDescriptor> {
    if (field.name.contains(".")) {
      throw IllegalMetadataSchemeStateException("Field name should not contains dots, because dots are used to express hierarchy. " +
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
        buildFieldDescriptors(fieldName, field.validationRule, FieldDataType.ARRAY, field.shouldBeAnonymized, field.description)
      }
      is PrimitiveEventField -> {
        if (field is StringEventField.ValidatedByInlineRegexp) {
          validateRegexp(field.regexp)
        }
        if (field is RegexpIntEventField) {
          validateRegexp(field.regexp)
        }

        buildFieldDescriptors(fieldName, field.validationRule, FieldDataType.PRIMITIVE, field.shouldBeAnonymized, field.description)
      }
    }
  }

  private fun buildFieldDescriptors(fieldName: String,
                                    validationRules: List<String>,
                                    fieldDataType: FieldDataType,
                                    shouldBeAnonymized: Boolean,
                                    description: String?): Set<FieldDescriptor> {
    val fields = mutableSetOf(FieldDescriptor(fieldName, validationRules.toSet(), shouldBeAnonymized, fieldDataType, description))
    if (validationRules.any { it in classValidationRules }) {
      fields.addAll(pluginInfoFields)
    }
    return fields
  }

  private fun validateRegexp(regexp: String) {
    if (regexp == ".*") {
      throw IllegalMetadataSchemeStateException("Regexp should be more strict to prevent accidentally reporting sensitive data.")
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
    val recorders = mutableSetOf<String>()
    val counterCollectors = ArrayList<FeatureUsageCollectorInfo>()
    UsageCollectors.COUNTER_EP_NAME.processWithPluginDescriptor { counterUsageCollectorEP, descriptor: PluginDescriptor ->
      if (counterUsageCollectorEP.implementationClass != null) {
        val collectorPlugin = descriptor.pluginId.idString
        if ((pluginId == null && !brokenPluginIds.contains(collectorPlugin)) || pluginId == collectorPlugin) {
          val collector = ApplicationManager.getApplication().instantiateClass<FeatureUsagesCollector>(
            counterUsageCollectorEP.implementationClass, descriptor)
          recorders.add(collector.group.recorder)
          counterCollectors.add(FeatureUsageCollectorInfo(collector, PluginSchemeDescriptor(collectorPlugin)))
        }
      }
    }

    result.addAll(collectGroupsFromExtensions("counter", counterCollectors, recorder))

    val stateCollectors = ArrayList<FeatureUsageCollectorInfo>()
    UsageCollectors.APPLICATION_EP_NAME.processWithPluginDescriptor { bean, descriptor ->
      val collectorPlugin = descriptor.pluginId.idString
      if ((pluginId == null && !brokenPluginIds.contains(collectorPlugin)) || pluginId == collectorPlugin) {
        recorders.add(bean.collector.group.recorder)
        stateCollectors.add(FeatureUsageCollectorInfo(bean.collector, PluginSchemeDescriptor(collectorPlugin)))
      }
    }
    UsageCollectors.PROJECT_EP_NAME.processWithPluginDescriptor { bean, descriptor ->
      val collectorPlugin = descriptor.pluginId.idString
      if ((pluginId == null && !brokenPluginIds.contains(collectorPlugin)) || pluginId == collectorPlugin) {
        recorders.add(bean.collector.group.recorder)
        stateCollectors.add(FeatureUsageCollectorInfo(bean.collector, PluginSchemeDescriptor(collectorPlugin)))
      }
    }
    result.addAll(collectGroupsFromExtensions("state", stateCollectors, recorder))

    //add event log system collectors for all recorders
    val systemCollectors = ArrayList<FeatureUsageCollectorInfo>()
    if (recorder != null) systemCollectors.add(calculateEventLogSystemCollector(recorder))
    else {
      recorders.forEach { systemCollectors.add(calculateEventLogSystemCollector(it)) }
    }
    result.addAll(collectGroupsFromExtensions("counter", systemCollectors, recorder))

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
        throw IllegalMetadataSchemeStateException("If group is reused in multiple collectors classes (e.g Project and Application collector), " +
                                                  "it should have the same version (group=${group.id})")
      }
      val existingScheme = existingGroup?.schema ?: HashSet()
      // why group by eventId?? eventId is unique within the group
      val eventsDescriptors = existingScheme + group.events.groupBy { it.eventId }
        .map { (eventId, events) ->

          val arrays = events.flatMap { event ->
            event.getFields().flatMap { field ->
              // From each field, we extract the paths of its object arrays
              getObjectArrays(null, field)
            }
          }

          EventDescriptor(eventId,
                          buildFields(events, eventId, group.id),
                          getEventDescription(events, eventId, group.id),
                          objectArrays = arrays)
        }
        .toSet()
      result[group.id] = GroupDescriptor(group.id, groupType, group.version, eventsDescriptors, collectorClass.name, group.recorder,
                                         PluginSchemeDescriptor(plugin.id), group.description, collector.fileName)
    }
    return result.values
  }

  // path: top_object.inner_object.field_name
  fun getObjectArrays(parentPath: String?, field: EventField<*>): List<String> {
    val path = parentPath?.let { it + "." + field.name } ?: field.name
    val objectArrays = mutableListOf<String>()
    if (field is ObjectListEventField) {
      objectArrays.add(path)
      field.fields.forEach {
        objectArrays += getObjectArrays(path, it)
      }
    }
    if (field is ObjectEventField) {
      field.fields.forEach {
        objectArrays += getObjectArrays(path, it)
      }
    }
    return objectArrays
  }

  /**
   * Get the event log group for each recorder from the event log provider of the recorder.
   * If PluginDescriptor of the event log provider isn't found, then use core plugin id.
   */
  private fun calculateEventLogSystemCollector(recorder: String): FeatureUsageCollectorInfo {
    val eventLogProvider = getEventLogProvider(recorder)
    val eventLogSystemCollector = eventLogProvider.eventLogSystemLogger
    val eventLogProviderPlugin = PluginManager.getPluginByClass(eventLogProvider.javaClass)?.pluginId?.idString
    return FeatureUsageCollectorInfo(eventLogSystemCollector, PluginSchemeDescriptor(eventLogProviderPlugin
                                                                                     ?: PluginManagerCore.CORE_PLUGIN_ID))
  }

  private fun getEventDescription(events: List<BaseEventId>, eventName: String, groupId: String): String? {
    val eventDescriptions = events.mapNotNullTo(HashSet()) { it.description }
    if (eventDescriptions.size > 1) {
      throw IllegalMetadataSchemeStateException("Events couldn't be defined twice with different descriptions (group=$groupId, event=$eventName)")
    }
    return eventDescriptions.firstOrNull()
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
    return events.asSequence()
      .flatMap { it.getFields() }
      .flatMap { field -> fieldSchema(field, field.name, eventName, groupId) }
      .groupBy { it.path }
      .map { (name, values) ->
        val type = getDataType(values, name, eventName, groupId)
        val shouldBeAnonymized = getShouldBeAnonymized(values, name, eventName, groupId)
        val fieldDescription = getFieldDescription(values, name, eventName, groupId)
        FieldDescriptor(name, values.flatMap { it.value }.toSet(), shouldBeAnonymized, type, fieldDescription)
      }
      .toSet()
  }

  private fun getFieldDescription(values: List<FieldDescriptor>, field: String, event: String, groupId: String): String? {
    val fieldsDescriptions = values.mapNotNullTo(HashSet()) { it.description }
    if (fieldsDescriptions.size > 1) {
      throw IllegalMetadataSchemeStateException("Fields couldn't be defined twice with different descriptions (group=$groupId, event=$event, field=$field)")
    }
    return fieldsDescriptions.firstOrNull()
  }

  private fun getShouldBeAnonymized(values: List<FieldDescriptor>, name: String, eventName: String, groupId: String): Boolean {
    val shouldBeAnonymized = values.first().shouldBeAnonymized
    return if (values.any { it.shouldBeAnonymized != shouldBeAnonymized })
      throw IllegalMetadataSchemeStateException("Field couldn't be defined twice with different shouldBeAnonymized value (group=$groupId, event=$eventName, field=$name)")
    else {
      shouldBeAnonymized
    }
  }

  private fun getDataType(values: List<FieldDescriptor>, name: String, eventName: String, groupId: String): FieldDataType {
    val dataType = values.first().dataType
    return if (values.any { it.dataType != dataType })
      throw IllegalMetadataSchemeStateException("Field couldn't have multiple types (group=$groupId, event=$eventName, field=$name)")
    else {
      dataType
    }
  }

  data class FeatureUsageCollectorInfo(val collector: FeatureUsagesCollector,
                                       val plugin: PluginSchemeDescriptor)
}

internal class IllegalMetadataSchemeStateException(message: String) : Exception(message)
