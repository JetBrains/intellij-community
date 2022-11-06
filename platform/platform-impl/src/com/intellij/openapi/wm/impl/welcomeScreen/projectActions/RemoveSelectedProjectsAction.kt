// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.*

/**
 * @author Konstantin Bulenkov
 */
internal class RemoveSelectedProjectsAction : RecentProjectsWelcomeScreenActionBase() {
  init {
    isEnabledInModalContext = true  // To allow to delete items from the Manage Recent Projects modal dialog, see IDEA-302750
  }

  override fun actionPerformed(event: AnActionEvent) {
    val item = getSelectedItem(event) ?: return
    removeItem(item)
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = getSelectedItem(event) != null
  }

  companion object {
    fun removeItem(item: RecentProjectTreeItem) {
      val recentProjectsManager = RecentProjectsManager.getInstance()
      val cloneableProjectsService = CloneableProjectsService.getInstance()

      val exitCode = Messages.showYesNoDialog(
        IdeBundle.message("dialog.message.remove.0.from.recent.projects.list", item.displayName()),
        IdeBundle.message("dialog.title.remove.recent.project"),
        IdeBundle.message("button.remove"),
        IdeBundle.message("button.cancel"),
        Messages.getQuestionIcon()
      )

      if (exitCode == Messages.OK) {
        when (item) {
          is ProjectsGroupItem -> recentProjectsManager.removeGroup(item.group)
          is RecentProjectItem -> recentProjectsManager.removePath(item.projectPath)
          is CloneableProjectItem -> cloneableProjectsService.removeCloneableProject(item.cloneableProject)
          is RootItem -> {}
        }
      }
    }
  }
}