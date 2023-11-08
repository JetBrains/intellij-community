// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogTestMetadataPersistence
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors
import java.io.File
import kotlin.system.exitProcess


private const val outputFileParameter = "--outputFile="
private const val pluginsFileParameter = "--pluginsFile="
private const val pluginIdParameter = "--pluginId="
private const val errorsFileParameter = "--errorsFile="
private const val recorderIdParameter = "--recorderId="
private const val testEventSchemeParameter = "--testEventScheme="

private val LOG: Logger
  get() = logger<EventsSchemeBuilderAppStarter>()

internal class EventsSchemeBuilderAppStarter : ApplicationStarter {
  override val commandName: String
    get() = "buildEventsScheme"

  override fun main(args: List<String>) {
    var outputFile: String? = null
    var pluginsFile: String? = null
    var pluginId: String? = null
    var errorsFile: String? = null
    var recorderId: String? = null
    var testEventsScheme = false

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
      else if (arg.startsWith(recorderIdParameter)) {
        recorderId = arg.substringAfter(recorderIdParameter)
      }
      else if (arg.startsWith(testEventSchemeParameter)) {
        testEventsScheme = arg.substringAfter(testEventSchemeParameter).toBoolean()
      }
    }

    val groups: List<GroupDescriptor>
    try {
      groups = EventsSchemeBuilder.buildEventsScheme(recorderId, pluginId, getPluginsToSkipSchemeGeneration())
    }
    catch (e: IllegalMetadataSchemeStateException) {
      LOG.error(e)
      if (errorsFile != null) FileUtil.writeToFile(File(errorsFile), e.toString())
      exitProcess(0)
    }
    val errors = EventSchemeValidator.validateEventScheme(groups)
    val errorsList = errors.values.flatten()
    if (errorsList.isNotEmpty()) {
      val errorsListString = errorsList.joinToString("\n")
      LOG.error(IllegalStateException(errorsListString))
      if (errorsFile != null) FileUtil.writeToFile(File(errorsFile), errorsListString)
    }

    val text: String
    if (!testEventsScheme) {
      val eventsScheme = EventsScheme(System.getenv("INSTALLER_LAST_COMMIT_HASH"),
                                      System.getenv("IDEA_BUILD_NUMBER"),
                                      groups.filter { errors[it]?.isEmpty() ?: true })
      text = SerializationHelper.serialize(eventsScheme)
      logEnabledPlugins(pluginsFile)
    }
    else {
      val groupsDescriptor = EventGroupRemoteDescriptors()
      for (group in groups) {
        groupsDescriptor.groups.add(
          EventLogTestMetadataPersistence.createGroupWithCustomRules(group.id, SerializationHelper.serialize(createValidationRules(group))))
      }
      text = SerializationHelper.serialize(groupsDescriptor)
    }

    if (outputFile != null) {
      FileUtil.writeToFile(File(outputFile), text)
    }
    else {
      println(text)
    }

    exitProcess(0)
  }

  private fun createValidationRules(group: GroupDescriptor): EventGroupRemoteDescriptors.GroupRemoteRule? {
    val eventIds = hashSetOf<String>()
    val eventData = hashMapOf<String, MutableSet<String>>()
    val events = group.schema
    events.forEach { event ->
      eventIds.add(event.event)
      event.fields.forEach { dataField ->
        val validationRule = dataField.value
        val validationRules = eventData[dataField.path]
        if (validationRules == null) {
          eventData[dataField.path] = validationRule.toHashSet()
        }
        else {
          validationRules.addAll(validationRule)
        }
      }
    }

    if (eventIds.isEmpty() && eventData.isEmpty()) return null

    val rules = EventGroupRemoteDescriptors.GroupRemoteRule()
    rules.event_id = eventIds
    rules.event_data = eventData
    return rules
  }

  private fun logEnabledPlugins(pluginsFile: String?) {
    val text = buildString {
      for (descriptor in PluginManagerCore.loadedPlugins) {
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