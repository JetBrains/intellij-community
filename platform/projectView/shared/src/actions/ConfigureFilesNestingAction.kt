// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.actions

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.FileNestingInProjectViewDialog
import com.intellij.ide.projectView.impl.ProjectViewFileNestingModel
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehavior
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.projectView.window.ProjectViewOptionSupport
import com.intellij.platform.projectView.window.isProjectViewSplit
import com.intellij.ui.treeStructure.ProjectViewUpdateCause

internal class ConfigureFilesNestingAction : DumbAwareAction(), ActionRemoteBehaviorSpecification {
  override fun getBehavior(): ActionRemoteBehavior? {
    return if (isProjectViewSplit()) {
      ActionRemoteBehavior.FrontendOnly
    }
    else {
      null
    }
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = isFileNestingAllowed(event)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun isFileNestingAllowed(event: AnActionEvent): Boolean {
    val project = event.project ?: return false

    if (!isProjectViewSplit()) {
      val view = ProjectView.getInstance(project)
      return view.currentProjectViewPane?.isFileNestingEnabled ?: false
    }

    return ProjectViewOptionSupport.getInstance(project).getFileNestingState()?.isFileNestingAvailable == true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return

    if (!isProjectViewSplit()) {
      val dialog = FileNestingInProjectViewDialog(project)
      dialog.reset(ProjectViewState.getInstance(project).useFileNestingRules)
      if (dialog.showAndGet()) {
        val view = ProjectView.getInstance(project)
        dialog.apply { view.setUseFileNestingRules(it) }
        view.currentProjectViewPane?.updateFromRoot(true, ProjectViewUpdateCause.SETTINGS)
      }
      return
    }

    val projectViewOptionSupport = ProjectViewOptionSupport.getInstance(project)
    val model = FileNestingModel(projectViewOptionSupport.getFileNestingState() ?: return)
    val dialog = FileNestingInProjectViewDialog(project, model)
    dialog.reset(model.isFileNestingOn)
    if (dialog.showAndGet()) {
      dialog.apply { isOn ->
        model.isFileNestingOn = isOn
      }
      projectViewOptionSupport.requestFileNestingChange(model.isFileNestingOn, model.activeRules)
    }
  }
}

private class FileNestingModel(private var state: FileNestingState) : ProjectViewFileNestingModel {
  var isFileNestingOn: Boolean
    get() = state.isFileNestingOn
    set(value) {
      state = state.copy(isFileNestingOn = value)
    }

  val activeRules: List<NestingRuleState>
    get() = state.activeRules

  override fun getRules(): List<ProjectViewFileNestingService.NestingRule> = state.activeRules.map { it.toNestingRule() }

  override fun setRules(rules: List<ProjectViewFileNestingService.NestingRule>) {
    state = state.copy(activeRules = rules.map { it.toNestingRuleState() })
  }

  override fun getDefaultRules(): List<ProjectViewFileNestingService.NestingRule> = state.defaultRules.map { it.toNestingRule() }
}
