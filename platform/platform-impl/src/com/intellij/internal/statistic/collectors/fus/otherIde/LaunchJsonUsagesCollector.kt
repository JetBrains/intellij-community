// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.otherIde

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.openapi.vfs.isFile
import tools.jackson.core.json.JsonReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

private val logger = logger<LaunchJsonUsagesCollector>()

private val OBJECT_MAPPER = JsonMapper.builder()
  .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
  .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
  .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
  .build()

internal class LaunchJsonUsagesCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  //override fun requiresReadAccess(): Boolean = true

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val projectDir = project.guessProjectDir() ?: return emptySet()
    return buildSet {
      val vsCodeDir = projectDir.findFileOrDirectory(".vscode") ?: return@buildSet
      if (!vsCodeDir.isDirectory) {
        logger.warn(".vscode is not a directory")
      }
      if (!vsCodeDir.isValid) return@buildSet
      add(vsCodeFolderDetectedEvent.metric())

      val launchJsonFile = vsCodeDir.findChild("launch.json") ?: return@buildSet
      if (!launchJsonFile.isFile) return@buildSet
      val rootNode = try {
        OBJECT_MAPPER.readTree(launchJsonFile.inputStream)
      }
      catch (e: Throwable) {
        logger.warn("Cannot parse \"launch.json\" file", e)
        return@buildSet
      }

      add(reportLaunchJsonDetected(rootNode))
      addAll(reportJSConfigurations(rootNode))
    }
  }

  private fun reportLaunchJsonDetected(rootNode: JsonNode): MetricEvent {
    val hasCompounds = rootNode.has("compounds")
    val configurationsNode = rootNode.get("configurations")
    val numberOfConfigurations = if (configurationsNode.isArray) {
      configurationsNode.count { it.isObject } // filter some garbage like null
    }
    else {
      logger.info("\"configurations\" field is expected to be an array of objects, but the actual type is ${configurationsNode.nodeType}")
      -1 // incorrect launch.json
    }

    return launchJsonDetectedEvent.metric(numberOfConfigurations, hasCompounds)
  }

  companion object {
    val GROUP = EventLogGroup("other.ide.vscode", version = 1)

    val vsCodeFolderDetectedEvent = GROUP.registerEvent("folder.detected")

    val numberOfConfigurationsField = EventFields.Int("numberOfConfigurations")

    // WebStorm: we want to know should we hide by default 'Before launch' section
    val hasCompoundConfigurations = EventFields.Boolean("hasCompoundConfigurations")
    val launchJsonDetectedEvent = GROUP.registerEvent(
      "launch.json.detected",
      numberOfConfigurationsField,
      hasCompoundConfigurations
    )

    // The event is declared here because if it is moved to another file,
    // it will be registered lazily and will not be included in the schema.
    val jsConfigurationEvent: VarargEventId = GROUP.registerVarargEvent(
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
  }
}
