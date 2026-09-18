// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectUpdater
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectsService
import java.nio.file.Path

/** Registry key that enables the "Update Project" action on the welcome screen. The action is off by default. */
private const val RECENT_PROJECT_UPDATE_ENABLED = "ide.welcome.screen.recent.project.update"

/**
 * Updates a recent version control project from the welcome screen without opening it.
 *
 * The action shows only for a recent project that has a repository. It triggers the holder, which confirms
 * trust, loads the project, and runs the standard "Update Project" flow on it.
 */
internal class UpdateRecentProjectAction : RecentProjectsWelcomeScreenActionBase() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private val myRecentProjectsService = RecentProjectsService.getInstance()

  override fun update(event: AnActionEvent) {
    if (!Registry.`is`(RECENT_PROJECT_UPDATE_ENABLED)) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    val item = getSelectedItem(event) as? RecentProjectItem
    val projectPath = item?.projectNioPath()
    event.presentation.isEnabledAndVisible = projectPath != null && RecentProjectUpdater.findUpdater(projectPath) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val projectPath = (getSelectedItem(event) as? RecentProjectItem)?.projectNioPath() ?: return
    myRecentProjectsService.updateRecentProject(projectPath)
  }
}

private fun RecentProjectItem.projectNioPath(): Path? = runCatching { Path.of(projectPath) }.getOrNull()
