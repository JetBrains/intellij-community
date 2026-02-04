// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogTestMetadataPersistence
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.createParentDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val descriptionDirParameter = "--descriptionDir="
private const val outputFileParameter = "--outputFile="
private const val pluginsFileParameter = "--pluginsFile="
private const val pluginIdParameter = "--pluginId="
private const val errorsFileParameter = "--errorsFile="
private const val recorderIdParameter = "--recorderId="
private const val testEventSchemeParameter = "--testEventScheme="

private val LOG: Logger
  get() = logger<EventsSchemeBuilderAppStarter>()

internal class EventsSchemeBuilderAppStarter : ApplicationStarter {
  override fun main(args: List<String>) {
    var descriptionDir: Path? = null
    var outputFile: Path? = null
    var pluginsFile: Path? = null
    var pluginId: String? = null
    var errorsFile: Path? = null
    var recorderId: String? = null
    var testEventsScheme = false

    for (arg in args) {
      if (arg.startsWith(descriptionDirParameter)) {
        descriptionDir = Path(arg.substringAfter(descriptionDirParameter))
      }
      else if (arg.startsWith(outputFileParameter)) {
        outputFile = Path(arg.substringAfter(outputFileParameter))
      }
      else if (arg.startsWith(pluginsFileParameter)) {
        pluginsFile = Path(arg.substringAfter(pluginsFileParameter))
      }
      else if (arg.startsWith(pluginIdParameter)) {
        pluginId = arg.substringAfter(pluginIdParameter)
      }
      else if (arg.startsWith(errorsFileParameter)) {
        errorsFile = Path(arg.substringAfter(errorsFileParameter))
      }
      else if (arg.startsWith(recorderIdParameter)) {
        recorderId = arg.substringAfter(recorderIdParameter)
      }
      else if (arg.startsWith(testEventSchemeParameter)) {
        testEventsScheme = arg.substringAfter(testEventSchemeParameter).toBoolean()
      }
    }

    val descriptions = HashMap<String, String>()
    descriptionDir?.listDirectoryEntries()?.forEach { file ->
      val recorder = file.nameWithoutExtension
      file.bufferedReader().use {
        Properties()
          .apply { load(file.bufferedReader()) }
          .forEach { (k, v) -> descriptions["${recorder}.${k}"] = v.toString() }
      }
    }

    val groups = try {
      EventsSchemeBuilder.buildEventsScheme(descriptions, recorderId, pluginId, getPluginsToSkipSchemeGeneration())
    }
    catch (e: IllegalMetadataSchemeStateException) {
      LOG.error(e)
      errorsFile?.createParentDirectories()?.writeText(e.toString())
      exitProcess(0)
    }
    val errors = EventSchemeValidator.validateEventScheme(groups)
    val errorList = errors.values.flatten()
    if (errorList.isNotEmpty()) {
      val errorListString = errorList.joinToString("\n")
      LOG.error(IllegalStateException(errorListString))
      errorsFile?.createParentDirectories()?.writeText(errorListString)
    }

    val text = if (!testEventsScheme) {
      val eventsScheme = EventsScheme(
        System.getenv("INSTALLER_LAST_COMMIT_HASH"),
        System.getenv("IDEA_BUILD_NUMBER"),
        groups.filter { errors[it]?.isEmpty() ?: true }
      )
      logEnabledPlugins(pluginsFile)
      SerializationHelper.serialize(eventsScheme)
    }
    else {
      val groupsDescriptor = EventGroupRemoteDescriptors()
      for (group in groups) {
        groupsDescriptor.groups.add(
          EventLogTestMetadataPersistence.createGroupWithCustomRules(group.id, SerializationHelper.serialize(createValidationRules(group)))
        )
      }
      SerializationHelper.serialize(groupsDescriptor)
    }

    if (outputFile != null) {
      outputFile.createParentDirectories().writeText(text)
    }
    else {
      println(text)
    }

    exitProcess(0)
  }

  private fun createValidationRules(group: GroupDescriptor): EventGroupRemoteDescriptors.GroupRemoteRule? {
    val eventIds = hashSetOf<String>()
    val eventData = hashMapOf<String, MutableSet<String>>()
    group.schema.forEach { event ->
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
    return if (eventIds.isEmpty() && eventData.isEmpty()) null
    else EventGroupRemoteDescriptors.GroupRemoteRule().apply {
      event_id = eventIds
      event_data = eventData
    }
  }

  private fun logEnabledPlugins(pluginsFile: Path?) {
    val text = buildString {
      for (descriptor in PluginManagerCore.loadedPlugins) {
        if (!PluginManagerCore.isDisabled(descriptor.pluginId)) {
          appendLine(descriptor.pluginId.idString)
        }
      }
    }
    if (pluginsFile != null) {
      pluginsFile.createParentDirectories().writeText(text)
    }
    else {
      println("Enabled plugins:")
      println(text)
    }
  }

  private fun getPluginsToSkipSchemeGeneration(): Set<String> {
    return System.getenv("SKIP_GENERATION_OF_BROKEN_PLUGINS")?.split(",")?.toSet() ?: emptySet()
  }
}
