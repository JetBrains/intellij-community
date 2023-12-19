package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import com.jetbrains.performancePlugin.commands.SetupProjectSdkUtil
import org.jetbrains.plugins.gradle.performanceTesting.ImportGradleProjectCommand.linkGradleProjectIfNeeded
import org.jetbrains.plugins.gradle.settings.GradleSettings

/**
 * The command sets a gradle jdk
 * Syntax: %setGradleJdk [sdk_name]|[sdk_type]|[sdk_path]
 */
class SetGradleJdkCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME = "setGradleJdk"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val (sdkName, sdkType, sdkHome) = extractCommandArgument(PREFIX).split("|")

    val sdk = SetupProjectSdkUtil.setupOrDetectSdk(sdkName, sdkType, sdkHome)
    val settings = GradleSettings.getInstance(project)
    linkGradleProjectIfNeeded(project, context, settings)
      .onError { throwable -> throw IllegalStateException("Link of a gradle project failed. Not a gradle project. ${throwable.message}") }
      .onSuccess { _ ->
        settings.linkedProjectsSettings.forEach {
          it.gradleJvm = sdk.name
        }
      }
  }

  override fun getName(): String {
    return NAME
  }
}