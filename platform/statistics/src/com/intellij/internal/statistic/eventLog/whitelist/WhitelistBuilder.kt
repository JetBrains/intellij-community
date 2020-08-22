// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist

import com.google.gson.GsonBuilder
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import kotlin.system.exitProcess

object WhitelistBuilder {
  data class WhitelistField(val path: String, val value: Set<String>)
  data class WhitelistEvent(val event: String, val fields: Set<WhitelistField>)
  data class WhitelistGroup(val id: String, val type: String, val version: Int, val schema: Set<WhitelistEvent>)

  private fun fieldSchema(field: EventField<*>, fieldName: String): Set<WhitelistField> {
    if (field == EventFields.PluginInfo || field == EventFields.PluginInfoFromInstance) {
      return hashSetOf(
        WhitelistField("plugin", hashSetOf("{util#plugin}")),
        WhitelistField("plugin_type", hashSetOf("{util#plugin_type}")),
        WhitelistField("plugin_version", hashSetOf("{util#plugin_version}"))
      )
    }

    return when (field) {
      is ObjectEventField -> buildObjectEvenScheme(fieldName, field.fields)
      is ObjectListEventField -> buildObjectEvenScheme(fieldName, field.fields)
      is PrimitiveEventField -> hashSetOf(WhitelistField(fieldName, field.validationRule.toHashSet()))
    }
  }

  private fun buildObjectEvenScheme(fieldName: String, fields: Array<out EventField<*>>): Set<WhitelistField> {
    val whitelistFields = hashSetOf<WhitelistField>()
    for (eventField in fields) {
      whitelistFields.addAll(fieldSchema(eventField, fieldName + "." + eventField.name))
    }
    return whitelistFields
  }

  @JvmStatic
  fun buildWhitelist(): List<WhitelistGroup> {
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
      val whitelistEvents = group.events.groupBy { it.eventId }
        .map { (name, events) -> WhitelistEvent(name, buildFields(events)) }
        .toHashSet()
      val whitelistGroup = WhitelistGroup(group.id, groupType, group.version, whitelistEvents)
      result.add(whitelistGroup)
    }
  }

  private fun buildFields(events: List<BaseEventId>): HashSet<WhitelistField> {
    return events.flatMap { it.getFields() }
      .flatMap { field -> fieldSchema(field, field.name) }
      .groupBy { it.path }
      .map { (name, values) -> WhitelistField(name, values.flatMap { it.value }.toHashSet()) }
      .toHashSet()
  }
}

class WhitelistBuilderAppStarter : ApplicationStarter {
  override fun getCommandName(): String = "buildWhitelist"

  override fun main(args: List<String>) {
    val groups = WhitelistBuilder.buildWhitelist()
    val text = GsonBuilder().setPrettyPrinting().create().toJson(groups)
    if (args.size == 2) {
      FileUtil.writeToFile(File(args[1]), text)
    }
    else {
      println(text)
    }
    exitProcess(0)
  }
}
