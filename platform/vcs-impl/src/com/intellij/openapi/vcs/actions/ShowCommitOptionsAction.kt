// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ShowCommitOptionsAction : AnAction() {
  init {
    templatePresentation.icon = AllIcons.Ide.Notification.Gear
    templatePresentation.hoveredIcon = AllIcons.Ide.Notification.GearHover
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getProjectCommitWorkflowHandler()?.isActive == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val workflowHandler = e.getProjectCommitWorkflowHandler()!!
    workflowHandler.showCommitOptions(e.isFromActionToolbar, e.dataContext)
  }
}