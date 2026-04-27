// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.otherIde

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import org.jetbrains.annotations.VisibleForTesting

private val OBJECT_MAPPER = JsonMapper.builder()
  .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
  .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
  .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
  .build()

private val LOG = logger<DotVSCodeProjectCollector>()

internal class DotVSCodeProjectCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  @VisibleForTesting
  public override fun getMetrics(project: Project): Set<MetricEvent> {
    val projectDir = project.guessProjectDir() ?: return emptySet()
    val events = mutableSetOf<MetricEvent>()

    val vsCodeDir = projectDir.findChild(".vscode")

    val vsCodeDirExists = vsCodeDir != null && vsCodeDir.isValid && vsCodeDir.isDirectory
    events.add(vsCodeFolderDetectedEvent.metric(vsCodeDirExists))

    if (vsCodeDirExists) {
      processLaunchJson(vsCodeDir, events)
      reportCppSettings(vsCodeDir, events)
    }

    return events
  }
}

private val GROUP = EventLogGroup("other.ide.vscode", version = 3)
private val vsCodeFolderDetectedEvent = GROUP.registerEvent("folder.detected", EventFields.Boolean("exists"))
private val numberOfConfigurationsField = EventFields.Int("numberOfConfigurations")

// WebStorm: we want to know should we hide by default 'Before launch' section
private val hasCompoundConfigurations = EventFields.Boolean("hasCompoundConfigurations")
private val launchJsonDetectedEvent = GROUP.registerEvent(
  "launch.json.detected",
  numberOfConfigurationsField,
  hasCompoundConfigurations
)

internal val jsConfigurationEvent: VarargEventId = GROUP.registerVarargEvent(
  "js.configuration",
  JavaScriptConfigurationFields.configurationType,
  JavaScriptConfigurationFields.request,
  JavaScriptConfigurationFields.hasPreLaunchTask,
  JavaScriptConfigurationFields.hasNonEmptyRuntimeArgs,
  JavaScriptConfigurationFields.hasCustomEnvVars,
  JavaScriptConfigurationFields.hasCustomUrl,
  JavaScriptConfigurationFields.urlIsLocalHost,
  JavaScriptConfigurationFields.hasCustomPort,
  JavaScriptConfigurationFields.hasCustomSkipFiles,
  JavaScriptConfigurationFields.hasPathMapping,
  JavaScriptConfigurationFields.hasCustomWebRoot,
  JavaScriptConfigurationFields.pauseForSourceMapEnabled,
)

private val hasCppPropertiesField = EventFields.Boolean("has_cpp_properties")
private val hasCppSettingsField = EventFields.Boolean("has_cpp_settings")
private val cppConfigurationEvent = GROUP.registerEvent(
  "cpp.configuration", hasCppPropertiesField, hasCppSettingsField
)

private fun reportCppSettings(
  vsCodeDir: VirtualFile,
  events: MutableSet<MetricEvent>,
) {
  val hasCppProperties = vsCodeDir.findChild("c_cpp_properties.json")?.isFile == true
  val hasCppSettings = detectCppInVsCodeSettings(vsCodeDir)
  if (!hasCppProperties && !hasCppSettings) return
  events.add(cppConfigurationEvent.metric(hasCppProperties, hasCppSettings))
}

private fun processLaunchJson(
  vsCodeDir: VirtualFile,
  events: MutableSet<MetricEvent>,
) {
  val launchJsonFile = vsCodeDir.findChild("launch.json")
  if (launchJsonFile != null && launchJsonFile.isFile) {
    try {
      val rootNode = OBJECT_MAPPER.readTree(launchJsonFile.inputStream)
      events.add(reportLaunchJsonDetected(rootNode))
      events.addAll(reportJSConfigurations(rootNode))
    }
    catch (e: Throwable) {
      LOG.warn("Cannot parse \"launch.json\" file", e)
    }
  }
}

private fun detectCppInVsCodeSettings(vsCodeDir: VirtualFile): Boolean {
  val settingsFile = vsCodeDir.findChild("settings.json") ?: return false
  if (!settingsFile.isFile) return false
  val rootNode = try {
    OBJECT_MAPPER.readTree(settingsFile.inputStream)
  }
  catch (_: Throwable) {
    return false
  }
  return rootNode.isObject && rootNode.properties().any { (key, _) ->
    key.startsWith("C_Cpp.")
  }
}

private fun reportLaunchJsonDetected(rootNode: JsonNode): MetricEvent {
  val hasCompounds = rootNode.has("compounds")
  val configurationsNode = rootNode.get("configurations")
  val numberOfConfigurations = if (configurationsNode.isArray) {
    configurationsNode.count { it.isObject } // filter some garbage like null
  }
  else {
    LOG.info("\"configurations\" field is expected to be an array of objects, but the actual type is ${configurationsNode.nodeType}")
    -1 // incorrect launch.json
  }

  return launchJsonDetectedEvent.metric(numberOfConfigurations, hasCompounds)
}
