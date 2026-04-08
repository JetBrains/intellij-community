// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.otherIde

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.openapi.vfs.isFile

internal class VsCodeCppDataCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = LaunchJsonUsagesCollector.GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val projectDir = project.guessProjectDir() ?: return emptySet()
    val vsCodeDir = projectDir.findFileOrDirectory(".vscode") ?: return emptySet()
    if (!vsCodeDir.isValid || !vsCodeDir.isDirectory) return emptySet()

    val hasCppProperties = vsCodeDir.findChild("c_cpp_properties.json")?.isFile == true
    val hasCppSettings = detectCppInVsCodeSettings(vsCodeDir)
    if (!hasCppProperties && !hasCppSettings) return emptySet()
    return setOf(cppConfigurationEvent.metric(hasCppProperties, hasCppSettings))
  }

  private fun detectCppInVsCodeSettings(vsCodeDir: VirtualFile): Boolean {
    val settingsFile = vsCodeDir.findChild("settings.json") ?: return false
    if (!settingsFile.isFile) return false
    val rootNode = try {
      VSCODE_OBJECT_MAPPER.readTree(settingsFile.inputStream)
    }
    catch (_: Throwable) {
      return false
    }
    return rootNode.isObject && rootNode.properties().any { (key, _) ->
      key.startsWith("C_Cpp.")
    }
  }

}

private val hasCppPropertiesField = EventFields.Boolean("has_cpp_properties")
private val hasCppSettingsField = EventFields.Boolean("has_cpp_settings")
private val cppConfigurationEvent = LaunchJsonUsagesCollector.GROUP.registerEvent(
  "cpp.configuration", hasCppPropertiesField, hasCppSettingsField
)