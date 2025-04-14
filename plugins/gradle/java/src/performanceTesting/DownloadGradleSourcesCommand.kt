package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.GradleJavaCoroutineScopeService.Companion.gradleCoroutineScope
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * The command downloads gradle sources
 * Syntax: %downloadGradleSources
 */
class DownloadGradleSourcesCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "downloadGradleSources"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    project.gradleCoroutineScope.launch {
      if (ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProjectAsync(project, GradleConstants.SYSTEM_ID)) {
        val spec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).withVmOptions("-Didea.gradle.download.sources.force=true")
        ExternalSystemUtil.refreshProjects(spec)
      }
    }
  }

  override fun getName(): String {
    return NAME
  }
}