// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win

import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.ReopenProjectAction
import com.intellij.ide.SystemDock
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.ui.win.WinShellIntegration.VoidShellTask
import com.intellij.util.PathUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val LOG = logger<WinDockDelegate>()

internal suspend fun createWinDockDelegate(): SystemDock? {
  val stackTraceHolder = Throwable("Asynchronously launched from here")

  try {
    val recentProjectsInDockSupported = serviceAsync<RecentProjectListActionProvider>().recentProjectsInDocSupported()
    @Suppress("SpellCheckingInspection")
    if (RegistryManager.getInstanceAsync().`is`("windows.jumplist") && recentProjectsInDockSupported) {
      return WinDockDelegate(WinShellIntegration.getInstance() ?: return null)
    }
    else {
      return null
    }
  }
  catch (err: Throwable) {
    err.addSuppressed(stackTraceHolder)
    LOG.error("Failed to initialize com.intellij.ui.win.WinShellIntegration instance", err)
    return null
  }
}

private class WinDockDelegate(private val wsi: WinShellIntegration) : SystemDock {
  override suspend fun updateRecentProjectsMenu() {
    val recentProjectActions = serviceAsync<RecentProjectListActionProvider>().getActionsWithoutGroups()
    val jumpTasks = convertToJumpTasks(recentProjectActions)
    // todo WinShellIntegration should use coroutines
    withContext(Dispatchers.IO) {
      wsi.postShellTask(VoidShellTask {
        it.clearRecentTasksList()
        it.setRecentTasksList(jumpTasks.toTypedArray())
      }).get()
    }
  }
}

private fun convertToJumpTasks(actions: List<AnAction>): List<JumpTask> {
  val launcherFileName = ApplicationNamesInfo.getInstance().scriptName + "64.exe"
  val launcherPath = Path.of(PathManager.getBinPath(), launcherFileName).toString()

  val result = ArrayList<JumpTask>(actions.size)
  for (action in actions) {
    if (action !is ReopenProjectAction) {
      LOG.debug { "Failed to convert an action \"$action\" to Jump Task: the action is not ReopenProjectAction" }
      continue
    }

    val projectPath = action.projectPath
    val projectPathSystem = PathUtil.toSystemDependentName(projectPath)
    if (projectPathSystem.isBlank()) {
      LOG.debug("Failed to convert a ReopenProjectAction \"$action\" to Jump Task: path to the project is empty (\"$projectPathSystem\")")
      continue
    }

    val taskTitle: String
    val taskTooltip: String
    val presentationText = action.projectDisplayName
    if (!presentationText.isNullOrBlank()) {
      taskTitle = presentationText
      taskTooltip = "$presentationText ($projectPathSystem)"
    }
    else {
      val projectName = action.projectNameToDisplay
      if (!projectName.isBlank()) {
        taskTitle = projectName
        taskTooltip = "$projectName ($projectPathSystem)"
      }
      else {
        taskTitle = projectPathSystem
        taskTooltip = projectPathSystem
      }
    }

    val taskArgs = "\"" + projectPathSystem + "\""
    result.add(JumpTask(taskTitle, launcherPath, taskArgs, taskTooltip))
  }
  return result
}