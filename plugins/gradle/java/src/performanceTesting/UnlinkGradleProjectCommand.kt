// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.externalSystem.action.DetachExternalProjectAction.detachProject
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.ui.playback.PlaybackContext
import com.jetbrains.performancePlugin.commands.OpenFileCommand.Companion.findFile
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.util.GradleConstants

class UnlinkGradleProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "unlinkGradleProject"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project

    val filePath = extractCommandArgument(PREFIX)
    val projectPath = findFile(extractCommandArgument(PREFIX), project)

    if (projectPath == null) {
      throw IllegalArgumentException("File not found: $filePath")
    }

    val dataProject = ExternalSystemApiUtil.findProjectNode(project, GradleConstants.SYSTEM_ID, projectPath.path)!!.data
    dataProject.linkedExternalProjectPath
    withContext(Dispatchers.EDT) {
      detachProject(project, dataProject.owner, dataProject, null)
    }
  }

  override fun getName(): String {
    return NAME
  }
}