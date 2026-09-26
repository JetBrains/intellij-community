// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectGroup
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProjectsGroupItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProviderRecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem

/**
 * @author Konstantin Bulenkov
 */
class CreateNewProjectGroupAction : RecentProjectsWelcomeScreenActionBase() {
  override fun actionPerformed(e: AnActionEvent) {
    val validator = object : InputValidator {
      override fun checkInput(inputString: String): Boolean {
        val text = inputString.trim()
        return getGroup(text) == null
      }

      override fun canClose(inputString: String): Boolean {
        return true
      }
    }

    val newGroup = Messages.showInputDialog(null, IdeBundle.message("dialog.message.project.group.name"),
                                            IdeBundle.message("dialog.title.create.new.project.group"), null, null, validator)
    if (newGroup != null) {
      RecentProjectsManager.getInstance().addGroup(ProjectGroup(newGroup))
    }
  }

  override fun update(event: AnActionEvent) {
    val item = getSelectedItem(event)
    event.presentation.isEnabled = item == null || item is RecentProjectItem || item is ProjectsGroupItem
    event.presentation.isVisible = item !is ProviderRecentProjectItem
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  companion object {
    private fun getGroup(name: String): ProjectGroup? {
      for (group in RecentProjectsManager.getInstance().groups) {
        if (group.name == name) {
          return group
        }
      }

      return null
    }
  }
}