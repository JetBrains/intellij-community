// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.LayeredIcon

private val ICON = LayeredIcon(AllIcons.General.GearPlain, AllIcons.General.Dropdown)

class ShowCommitOptionsAction : AnAction() {
  init {
    templatePresentation.icon = ICON
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getProjectCommitWorkflowHandler()?.isActive == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val workflowHandler = e.getProjectCommitWorkflowHandler()!!
    workflowHandler.showCommitOptions(e.isFromActionToolbar, e.dataContext)
  }
}