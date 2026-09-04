// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance

import com.intellij.gradle.java.performance.ImportGradleProjectCommand.Companion.linkGradleProjectIfNeeded
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import com.jetbrains.performancePlugin.commands.SetupProjectSdkUtil
import kotlinx.coroutines.CancellationException
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
    try {
      linkGradleProjectIfNeeded(project, context, settings)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      throw IllegalStateException("Link of a gradle project failed. Not a gradle project. ${e.message}", e)
    }
    settings.linkedProjectsSettings.forEach {
      it.gradleJvm = sdk.name
    }
  }

  override fun getName(): String {
    return NAME
  }
}
