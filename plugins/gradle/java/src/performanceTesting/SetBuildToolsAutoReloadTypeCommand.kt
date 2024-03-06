package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter


class SetBuildToolsAutoReloadTypeCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "setBuildToolsAutoReloadType"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    ExternalSystemProjectTrackerSettings.getInstance(context.project).apply {
      autoReloadType = ExternalSystemProjectTrackerSettings.AutoReloadType.valueOf(extractCommandArgument(PREFIX))
    }
  }

  override fun getName(): String {
    return NAME
  }
}