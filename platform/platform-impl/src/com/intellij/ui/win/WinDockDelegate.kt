// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win

import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.SystemDock
import com.intellij.ui.win.WinShellIntegration.VoidShellTask
import com.intellij.util.PathUtil
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

private val LOG = logger<WinDockDelegate>()

internal class WinDockDelegate private constructor(private val wsiFuture: Future<WinShellIntegration?>) : SystemDock.Delegate {
  companion object {
    @JvmField val instance: WinDockDelegate

    init {
      val stackTraceHolder = Throwable("Asynchronously launched from here")

      // Not AppExecutorUtil.getAppExecutorService() for class loading optimization
      val wsiFuture = ApplicationManager.getApplication().executeOnPooledThread(Callable {
        try {
          @Suppress("SpellCheckingInspection")
          if (!Registry.`is`("windows.jumplist")) {
            return@Callable null
          }

          return@Callable WinShellIntegration.getInstance()
        }
        catch (err: Throwable) {
          err.addSuppressed(stackTraceHolder)
          LOG.error("Failed to initialize com.intellij.ui.win.WinShellIntegration instance", err)
          return@Callable null
        }
      })
      instance = WinDockDelegate(wsiFuture)
    }
  }

  override fun updateRecentProjectsMenu() {
    val stackTraceHolder = Throwable("Asynchronously launched from here")

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val wsi = wsiFuture.get(30, TimeUnit.SECONDS) ?: return@executeOnPooledThread

        val recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(addClearListItem = false)
        val jumpTasks = convertToJumpTasks(recentProjectActions)
        wsi.postShellTask(VoidShellTask {
          it.clearRecentTasksList()
          it.setRecentTasksList(jumpTasks.toTypedArray())
        }).get()
      }
      catch (e: InterruptedException) {
        e.addSuppressed(stackTraceHolder)
        LOG.warn(e)
      }
      catch (e: Throwable) {
        e.addSuppressed(stackTraceHolder)
        LOG.error(e)
      }
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