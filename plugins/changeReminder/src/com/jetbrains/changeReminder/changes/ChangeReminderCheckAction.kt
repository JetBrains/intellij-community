// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.changes

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.jetbrains.changeReminder.anyGitRootsForIndexing
import com.jetbrains.changeReminder.getGitRoots
import com.jetbrains.changeReminder.plugin.UserSettings

class ChangeReminderCheckAction :
  ToggleAction(),
  DumbAware {

  private val userSettings = service<UserSettings>()

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project!!
    if (!project.anyGitRootsForIndexing()) {
      e.presentation.isEnabled = false
      if (project.getGitRoots().isEmpty()) {
        e.presentation.isVisible = false
      }
      else {
        e.presentation.description = "Git index is disabled"
      }
    }
  }

  override fun isSelected(e: AnActionEvent) = userSettings.isPluginEnabled

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    userSettings.isPluginEnabled = state
  }
}