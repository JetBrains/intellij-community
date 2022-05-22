// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.CloneableProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectsGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RootItem

/**
 * @author Konstantin Bulenkov
 */
internal class RemoveSelectedProjectsAction : RecentProjectsWelcomeScreenActionBase() {
  override fun actionPerformed(event: AnActionEvent) {
    val recentProjectsManager = RecentProjectsManager.getInstance()
    val cloneableProjectsService = CloneableProjectsService.getInstance()

    val item = getSelectedItem(event) ?: return
    when (item) {
      is ProjectsGroupItem -> recentProjectsManager.removeGroup(item.group)
      is RecentProjectItem -> recentProjectsManager.removePath(item.projectPath)
      is CloneableProjectItem -> cloneableProjectsService.cancelClone(item.progressIndicator)
      is RootItem -> {}
    }
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = getSelectedItem(event) != null
  }
}