// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist

import com.google.gson.GsonBuilder
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.ApplicationStarter
import kotlin.system.exitProcess

object WhitelistBuilder {
  data class WhitelistField(val path: String, val value: List<String>)
  data class WhitelistEvent(val event: String, val fields: List<WhitelistField>)
  data class WhitelistGroup(val id: String, val type: String, val schema: List<WhitelistEvent>)

  private fun fieldSchema(field: EventField<*>, fieldName: String): List<WhitelistField> {
    if (field == EventFields.PluginInfo || field == EventFields.PluginInfoFromInstance) {
      return listOf(
        WhitelistField("plugin", listOf("{util#plugin}")),
        WhitelistField("plugin_type", listOf("{util#plugin_type}")),
        WhitelistField("plugin_version", listOf("{util#plugin_version}"))
      )
    }

    return when (field) {
      is ObjectEventField -> buildObjectEvenScheme(fieldName, field.fields)
      is ObjectListEventField -> buildObjectEvenScheme(fieldName, field.fields)
      is PrimitiveEventField -> listOf(WhitelistField(fieldName, field.validationRule))
    }
  }

  private fun buildObjectEvenScheme(fieldName: String, fields: Array<out EventField<*>>): List<WhitelistField> {
    val whitelistFields = mutableListOf<WhitelistField>()
    for (eventField in fields) {
      whitelistFields.addAll(fieldSchema(eventField, fieldName + "." + eventField.name))
    }
    return whitelistFields
  }

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
      val whitelistEvents = group.events.map { event ->
        WhitelistEvent(event.eventId, event.getFields().flatMap { field -> fieldSchema(field, field.name) })
      }
      val whitelistGroup = WhitelistGroup(group.id, groupType, whitelistEvents)
      result.add(whitelistGroup)
    }
  }
}

class WhitelistBuilderAppStarter : ApplicationStarter {
  override fun getCommandName(): String = "buildWhitelist"

  override fun main(args: List<String>) {
    val groups = WhitelistBuilder.buildWhitelist()
    println(GsonBuilder().setPrettyPrinting().create().toJson(groups))
    exitProcess(0)
  }
}
