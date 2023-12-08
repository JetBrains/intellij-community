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
    val items = getSelectedItems(event) ?: return
    removeItems(items)
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = getSelectedItem(event) != null
  }

  companion object {
    fun removeItems(items: List<RecentProjectTreeItem>) {
      if (items.isEmpty()) return

      val recentProjectsManager = RecentProjectsManager.getInstance()
      val cloneableProjectsService = CloneableProjectsService.getInstance()

      val title =
        if (items.size == 1) IdeBundle.message("dialog.title.remove.recent.project")
        else IdeBundle.message("dialog.title.remove.recent.project.plural")

      val message =
        if (items.size == 1) IdeBundle.message("dialog.message.remove.0.from.recent.projects.list", items.first().displayName())
        else IdeBundle.message("dialog.message.remove.projects.from.recent.projects.list")

      val exitCode = Messages.showYesNoDialog(
        message,
        title,
        IdeBundle.message("button.remove"),
        IdeBundle.message("button.cancel"),
        Messages.getQuestionIcon()
      )

      if (exitCode == Messages.OK) {
        items.forEach { item ->
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
}