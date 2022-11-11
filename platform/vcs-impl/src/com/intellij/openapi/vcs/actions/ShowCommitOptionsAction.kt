// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.actions.commit.getContextCommitWorkflowHandler
import com.intellij.ui.ExperimentalUI
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import com.intellij.vcs.commit.NonModalCommitWorkflowHandler

class ShowCommitOptionsAction : DumbAwareAction() {
  init {
    templatePresentation.icon = AllIcons.Ide.Notification.Gear
    templatePresentation.hoveredIcon = AllIcons.Ide.Notification.GearHover
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      when (val workflowHandler = e.getContextCommitWorkflowHandler()) {
        is ChangesViewCommitWorkflowHandler -> workflowHandler.isActive
        is NonModalCommitWorkflowHandler<*, *> -> true
        else -> false
      }
    if (ExperimentalUI.isNewUI()) {
      e.presentation.icon = AllIcons.General.GearPlain
      e.presentation.hoveredIcon = AllIcons.General.GearPlain
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val workflowHandler = e.getContextCommitWorkflowHandler() as NonModalCommitWorkflowHandler<*, *>
    workflowHandler.showCommitOptions(e.isFromActionToolbar, e.dataContext)
  }
}