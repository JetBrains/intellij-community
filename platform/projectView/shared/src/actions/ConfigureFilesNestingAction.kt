// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.actions

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.FileNestingInProjectViewDialog
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.projectView.window.isProjectViewSplit
import com.intellij.ui.treeStructure.ProjectViewUpdateCause

internal class ConfigureFilesNestingAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = isFileNestingAllowed(event)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun isFileNestingAllowed(event: AnActionEvent): Boolean {
    val project = event.project ?: return false
    val view = ProjectView.getInstance(project)
    return view.currentProjectViewPane?.isFileNestingEnabled ?: false
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val dialog = FileNestingInProjectViewDialog(project)
    dialog.reset(ProjectViewState.getInstance(project).useFileNestingRules)
    if (dialog.showAndGet()) {
      val view = ProjectView.getInstance(project)
      dialog.apply { view.setUseFileNestingRules(it) }
      view.currentProjectViewPane?.updateFromRoot(true, ProjectViewUpdateCause.SETTINGS)
    }
  }
}