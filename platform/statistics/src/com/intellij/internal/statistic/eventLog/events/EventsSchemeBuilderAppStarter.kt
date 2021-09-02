// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.events

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.lang.reflect.Type
import kotlin.system.exitProcess

class EventsSchemeBuilderAppStarter : ApplicationStarter {
  override fun getCommandName(): String = "buildEventsScheme"

  override fun main(args: List<String>) {
    var outputFile: String? = null
    var pluginsFile: String? = null
    var pluginId: String? = null

    for (arg in args) {
      if (arg.startsWith(outputFileParameter)) {
        outputFile = arg.substringAfter(outputFileParameter)
      }
      else if (arg.startsWith(pluginsFileParameter)) {
        pluginsFile = arg.substringAfter(pluginsFileParameter)
      }
      else if (arg.startsWith(pluginIdParameter)) {
        pluginId = arg.substringAfter(pluginIdParameter)
      }
    }

    val eventsScheme = EventsSchemeBuilder.EventsScheme(System.getenv("INSTALLER_LAST_COMMIT_HASH"),
      System.getenv("IDEA_BUILD_NUMBER"),
      EventsSchemeBuilder.buildEventsScheme(pluginId))
    val text = GsonBuilder()
      .registerTypeAdapter(EventsSchemeBuilder.FieldDataType::class.java, FieldDataTypeSerializer)
      .setPrettyPrinting()
      .create()
      .toJson(eventsScheme)
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

  companion object {
    private const val outputFileParameter = "--outputFile="
    private const val pluginsFileParameter = "--pluginsFile="
    private const val pluginIdParameter = "--pluginId="
  }

  object FieldDataTypeSerializer : JsonSerializer<EventsSchemeBuilder.FieldDataType> {
    override fun serialize(src: EventsSchemeBuilder.FieldDataType?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
      if (src == EventsSchemeBuilder.FieldDataType.PRIMITIVE || src == null) {
        return context!!.serialize(null)
      }
      return context!!.serialize(src.name)
    }
  }
}