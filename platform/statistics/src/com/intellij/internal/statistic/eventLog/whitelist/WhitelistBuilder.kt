// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist

import com.google.gson.GsonBuilder
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.service.fus.collectors.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import kotlin.system.exitProcess

private data class WhitelistField(val path: String, val value: List<String>)
private data class WhitelistEvent(val event: String, val fields: List<WhitelistField>)
private data class WhitelistGroup(val id: String, val type: String, val schema: List<WhitelistEvent>)

private fun valueSchema(field: EventField<*>): List<String> = when(field) {
  is StringEventField ->
    if (field.customRuleId != null)
      listOf("{util#${field.customRuleId}}")
    else if (field.customEnumId != null)
      listOf("{enum#${field.customEnumId}}")
    else
      emptyList()

  is StringListEventField ->
    if (field.customRuleId != null) listOf("{util#${field.customRuleId}}") else emptyList()

  is EnumEventField<*> ->
    field.transformAllEnumConstants()

  is IntEventField, is LongEventField ->
    listOf("{regexp#integer}")

  is BooleanEventField ->
    listOf("{enum#boolean}")

  EventFields.AnonymizedPath ->
    listOf("{util#hash}")

  EventFields.InputEvent ->
    listOf("{util#shortcut}")

  EventFields.ActionPlace ->
    listOf("{util#place}")

  EventFields.CurrentFile ->
    listOf("{util#current_file}")

  EventFields.Version ->
    listOf("{regexp#version}")

  else -> {
    emptyList()
  }
}

private fun fieldSchema(field: EventField<*>): List<WhitelistField> {
  if (field == EventFields.PluginInfo || field == EventFields.PluginInfoFromInstance) {
    return listOf(
      WhitelistField("plugin", listOf("{util#plugin}")),
      WhitelistField("plugin_type", listOf("{util#plugin_type}")),
      WhitelistField("plugin_info", listOf("{util#plugin_info}"))
    )
  }
  return listOf(WhitelistField(field.name, valueSchema(field)))
}

private fun buildWhitelist(): List<WhitelistGroup> {
  val result = mutableListOf<WhitelistGroup>()
  collectWhitelistFromExtensions(result, "counter", FUCounterUsageLogger.instantiateCounterCollectors())
  collectWhitelistFromExtensions(result, "state", ApplicationUsagesCollector.EP_NAME.extensionList)
  collectWhitelistFromExtensions(result, "state", ProjectUsagesCollector.EP_NAME.extensionList)
  return result
}

private fun collectWhitelistFromExtensions(result: MutableList<WhitelistGroup>,
                                           groupType: String,
                                           collectors: Collection<FeatureUsagesCollector>) {
  for (collector in collectors) {
    val group = collector.group ?: continue
    val whitelistEvents = group.events.map { event ->
      WhitelistEvent(event.eventId, event.getFields().flatMap { field -> fieldSchema(field) })
    }
    val whitelistGroup = WhitelistGroup(group.id, groupType, whitelistEvents)
    result.add(whitelistGroup)
  }
}

class WhitelistBuilderAppStarter : ApplicationStarter {
  override fun getCommandName(): String = "buildWhitelist"

  override fun main(args: List<String>) {
    val groups = buildWhitelist()
    for (group in groups) {
      println(GsonBuilder().setPrettyPrinting().create().toJson(group))
    }
    exitProcess(0)
  }
}
