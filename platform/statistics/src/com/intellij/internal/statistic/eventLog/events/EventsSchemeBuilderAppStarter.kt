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
    val outputFile = args.getOrNull(1)
    val pluginsFile = args.getOrNull(2)
    val eventsScheme = EventsSchemeBuilder.EventsScheme(System.getenv("INSTALLER_LAST_COMMIT_HASH"),
      System.getenv("IDEA_BUILD_NUMBER"),
      EventsSchemeBuilder.buildEventsScheme())
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

  object FieldDataTypeSerializer : JsonSerializer<EventsSchemeBuilder.FieldDataType> {
    override fun serialize(src: EventsSchemeBuilder.FieldDataType?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
      if (src == EventsSchemeBuilder.FieldDataType.PRIMITIVE || src == null) {
        return context!!.serialize(null)
      }
      return context!!.serialize(src.name)
    }
  }
}