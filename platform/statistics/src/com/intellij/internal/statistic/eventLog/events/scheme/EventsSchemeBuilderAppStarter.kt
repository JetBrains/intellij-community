// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import kotlin.system.exitProcess


private const val outputFileParameter = "--outputFile="
private const val pluginsFileParameter = "--pluginsFile="
private const val pluginIdParameter = "--pluginId="
private const val errorsFileParameter = "--errorsFile="

internal class EventsSchemeBuilderAppStarter : ApplicationStarter {
  private val LOG = logger<EventsSchemeBuilderAppStarter>()

  override val commandName: String
    get() = "buildEventsScheme"

  override fun main(args: List<String>) {
    var outputFile: String? = null
    var pluginsFile: String? = null
    var pluginId: String? = null
    var errorsFile: String? = null

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
      else if (arg.startsWith(errorsFileParameter)) {
        errorsFile = arg.substringAfter(errorsFileParameter)
      }
    }

    val groups = EventsSchemeBuilder.buildEventsScheme(null, pluginId, getPluginsToSkipSchemeGeneration())
    val errors = EventSchemeValidator.validateEventScheme(groups)
    val errorsList = errors.values.flatten()
    if (errorsList.isNotEmpty()) {
      val errorsListString = errorsList.joinToString("\n")
      LOG.error(IllegalStateException(errorsListString))
      if (errorsFile != null) FileUtil.writeToFile(File(errorsFile), errorsListString)
    }
    val eventsScheme = EventsScheme(System.getenv("INSTALLER_LAST_COMMIT_HASH"),
                                    System.getenv("IDEA_BUILD_NUMBER"),
                                    groups.filter { errors[it]?.isEmpty() ?: true })

    val text = SerializationHelper.serialize(eventsScheme)
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
          appendLine(descriptor.pluginId.idString)
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

  private fun getPluginsToSkipSchemeGeneration(): Set<String> {
    val skipGenerationOfBrokenPlugins = System.getenv("SKIP_GENERATION_OF_BROKEN_PLUGINS")
    if (skipGenerationOfBrokenPlugins == null) return emptySet()
    return skipGenerationOfBrokenPlugins.split(",").toSet()
  }
}