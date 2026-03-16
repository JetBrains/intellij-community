// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider
import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.ListEventField
import com.intellij.internal.statistic.eventLog.events.ObjectEventField
import com.intellij.internal.statistic.eventLog.events.ObjectListEventField
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.RegexpIntEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.events.StringListEventField
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectors
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.regex.Pattern

@ApiStatus.Internal
object EventsSchemeBuilder {
  @VisibleForTesting
  val pluginInfoFields: Set<FieldDescriptor> = setOf(
    FieldDescriptor("plugin", setOf("{util#plugin}"), false),
    FieldDescriptor("plugin_type", setOf("{util#plugin_type}"), false),
    FieldDescriptor("plugin_version", setOf("{util#plugin_version}"), false)
  )

  private val classValidationRules = setOf(
    "class_name", "dialog_class", "quick_fix_class_name", "run_config_factory", "tip_info", "run_config_factory",
    "run_config_id", "facets_type", "registry_key"
  ).map { "{util#$it}" }

  private fun fieldSchema(field: EventField<*>, fieldName: String, eventName: String, groupId: String): Set<FieldDescriptor> {
    if (field.name.contains(".")) {
      throw IllegalMetadataSchemeStateException(
        "Field name should not contains dots, because dots are used to express hierarchy. " +
        "Group=$groupId, event=$eventName, field=${field.name}"
      )
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

  private fun buildFieldDescriptors(
    fieldName: String,
    validationRules: List<String>,
    fieldDataType: FieldDataType,
    shouldBeAnonymized: Boolean,
    description: String?,
  ): Set<FieldDescriptor> {
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

  private fun buildObjectEvenScheme(fieldName: String, fields: Array<out EventField<*>>, eventName: String, groupId: String): Set<FieldDescriptor> {
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
  fun buildEventsScheme(
    descriptions: Map<String, String> = emptyMap(),
    recorder: String? = null,
    pluginId: String? = null,
    brokenPluginIds: Set<String> = emptySet(),
  ): List<GroupDescriptor> {
    val result = mutableListOf<GroupDescriptor>()
    val recorders = mutableSetOf<String>()
    val counterCollectors = ArrayList<FeatureUsageCollectorInfo>()
    UsageCollectors.COUNTER_EP_NAME.processWithPluginDescriptor { counterUsageCollectorEP, descriptor: PluginDescriptor ->
      if (counterUsageCollectorEP.implementationClass != null) {
        val collectorPlugin = descriptor.pluginId.idString
        if ((pluginId == null && !brokenPluginIds.contains(collectorPlugin)) || pluginId == collectorPlugin) {
          val collector = ApplicationManager.getApplication()
            .instantiateClass<FeatureUsagesCollector>(counterUsageCollectorEP.implementationClass, descriptor)
          recorders.add(collector.group.recorder)
          counterCollectors.add(FeatureUsageCollectorInfo(collector, PluginSchemeDescriptor(collectorPlugin)))
        }
      }
    }

    result.addAll(collectGroupsFromExtensions("counter", counterCollectors, descriptions, recorder))

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
    result.addAll(collectGroupsFromExtensions("state", stateCollectors, descriptions, recorder))

    //add event log system collectors for all recorders
    val systemCollectors = ArrayList<FeatureUsageCollectorInfo>()
    if (recorder != null) systemCollectors.add(calculateEventLogSystemCollector(recorder))
    else {
      recorders.forEach { systemCollectors.add(calculateEventLogSystemCollector(it)) }
    }
    result.addAll(collectGroupsFromExtensions("counter", systemCollectors, descriptions, recorder))

    result.sortBy(GroupDescriptor::id)
    return result
  }

  @VisibleForTesting
  fun collectGroupsFromExtensions(
    groupType: String,
    collectors: Collection<FeatureUsageCollectorInfo>,
    recorder: String?,
  ): MutableCollection<GroupDescriptor> = collectGroupsFromExtensions(groupType, collectors, descriptions = emptyMap(), recorder)

  @VisibleForTesting
  fun collectGroupsFromExtensions(
    groupType: String,
    collectors: Collection<FeatureUsageCollectorInfo>,
    descriptions: Map<String, String>,
    recorder: String?,
  ): MutableCollection<GroupDescriptor> {
    val result = HashMap<String, GroupDescriptor>()
    for ((collector, plugin) in collectors) {
      val collectorClass = collector.javaClass.enclosingClass ?: collector.javaClass
      validateGroupId(collector)
      val group = collector.group ?: continue
      if (recorder != null && group.recorder != recorder) continue
      val existingGroup = result[group.id]
      if (existingGroup != null && group.version != existingGroup.version) {
        throw IllegalMetadataSchemeStateException(
          "If group is reused in multiple collectors classes (e.g Project and Application collector), " +
          "it should have the same version (group=${group.id})"
        )
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
          val eventDescription = descriptions["${group.recorder}.${group.id}.${eventId}"]
          EventDescriptor(eventId, buildFields(events, eventId, group.id), eventDescription, objectArrays = arrays)
        }
        .toSet()
      val groupDescription = descriptions["${group.recorder}.${group.id}"]
      result[group.id] = GroupDescriptor(
        group.id, groupType, group.version, eventsDescriptors, collectorClass.name, group.recorder,
        PluginSchemeDescriptor(plugin.id), groupDescription, collector.fileName
      )
    }
    return result.values
  }

  // path: top_object.inner_object.field_name
  private fun getObjectArrays(parentPath: String?, field: EventField<*>): List<String> {
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
    return FeatureUsageCollectorInfo(eventLogSystemCollector, PluginSchemeDescriptor(eventLogProviderPlugin ?: PluginManagerCore.CORE_PLUGIN_ID))
  }

  private fun validateGroupId(collector: FeatureUsagesCollector) {
    try {
      // get group id to check that either group or group id is overridden
      @Suppress("DEPRECATION", "removal")
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
      throw IllegalMetadataSchemeStateException(
        "Field couldn't be defined twice with different shouldBeAnonymized value (group=$groupId, event=$eventName, field=$name)"
      )
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

  data class FeatureUsageCollectorInfo(val collector: FeatureUsagesCollector, val plugin: PluginSchemeDescriptor)
}

internal class IllegalMetadataSchemeStateException(message: String) : Exception(message)
