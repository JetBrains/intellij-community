// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.cloneDialog.VcsCloneDialog

open class GetFromVersionControlAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val isEnabled = CheckoutProvider.EXTENSION_POINT_NAME.hasAnyExtensions()
    val presentation = e.presentation
    presentation.isEnabledAndVisible = isEnabled
    if (!isEnabled) {
      return
    }
    if (e.place == ActionPlaces.WELCOME_SCREEN) {
      if (FlatWelcomeFrame.USE_TABBED_WELCOME_SCREEN) {
        presentation.icon = AllIcons.Welcome.FromVCSTab
        presentation.selectedIcon = AllIcons.Welcome.FromVCSTabSelected
        presentation.text = ActionsBundle.message("Vcs.VcsClone.Tabbed.Welcome.text")
      }
      else {
        presentation.icon = AllIcons.Vcs.Branch
        presentation.text = ActionsBundle.message("Vcs.VcsClone.Welcome.text")
      }
    }
    else {
      presentation.icon = if (ExperimentalUI.isNewUI() && (ActionPlaces.PROJECT_WIDGET_POPUP == e.place)) ExpUiIcons.Vcs.Vcs else null
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: ProjectManager.getInstance().defaultProject
    val cloneDialog = VcsCloneDialog.Builder(project).forExtension()
    if (cloneDialog.showAndGet()) {
      cloneDialog.doClone(ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener)
    }
  }
}

class ProjectFromVersionControlAction : GetFromVersionControlAction() {}


