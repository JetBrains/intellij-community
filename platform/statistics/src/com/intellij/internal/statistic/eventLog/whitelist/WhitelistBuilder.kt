// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.whitelist

import com.google.gson.GsonBuilder
import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.ide.plugins.PluginManagerCore
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
    result.addAll(collectWhitelistFromExtensions("counter", FUCounterUsageLogger.instantiateCounterCollectors()))
    result.addAll(collectWhitelistFromExtensions("state", ApplicationUsagesCollector.EP_NAME.extensionList))
    result.addAll(collectWhitelistFromExtensions("state", ProjectUsagesCollector.EP_NAME.extensionList))
    return result
  }

  fun collectWhitelistFromExtensions(groupType: String,
                                     collectors: Collection<FeatureUsagesCollector>): MutableList<WhitelistGroup> {
    val result = mutableListOf<WhitelistGroup>()
    for (collector in collectors) {
      val group = collector.group ?: continue
      val whitelistEvents = group.events.groupBy { it.eventId }
        .map { (name, events) -> WhitelistEvent(name, buildFields(events)) }
        .toHashSet()
      val whitelistGroup = WhitelistGroup(group.id, groupType, group.version, whitelistEvents)
      result.add(whitelistGroup)
    }
    return result
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
    logInstalledPlugins()
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

  private fun logInstalledPlugins() {
    println("Disabled plugins:")
    for (id in DisabledPluginsState.disabledPlugins()) {
      println(id.toString())
    }

    println("\nEnabled plugins:")
    for (descriptor in PluginManagerCore.getLoadedPlugins()) {
      val bundled = descriptor.isBundled
      if (descriptor.isEnabled) {
        println("${descriptor.name} (bundled=$bundled)")
      }
    }
  }
}
