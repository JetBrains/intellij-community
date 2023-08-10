// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.changes

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.jetbrains.changeReminder.ChangeReminderBundle
import com.jetbrains.changeReminder.anyGitRootsForIndexing
import com.jetbrains.changeReminder.getGitRoots
import com.jetbrains.changeReminder.plugin.UserSettings

class ChangeReminderCheckAction : ToggleAction(), DumbAware {

  private val userSettings = service<UserSettings>()

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project ?: let {
      e.presentation.isEnabledAndVisible = false
      return
    }
    if (!project.anyGitRootsForIndexing()) {
      e.presentation.isEnabled = false
      if (project.getGitRoots().isEmpty()) {
        e.presentation.isVisible = false
      }
      else {
        e.presentation.setDescription(ChangeReminderBundle.messagePointer(
          "action.ChangesView.ViewOptions.ShowRelatedFiles.disabled.index.description"
        ))
      }
    }
  }

  override fun isSelected(e: AnActionEvent) = userSettings.isPluginEnabled

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    userSettings.isPluginEnabled = state
  }
}