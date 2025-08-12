// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.vcs.update

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.util.ui.OptionsDialog
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractCommonUpdateAction protected constructor(
  private val actionInfo: ActionInfo,
  private val scopeInfo: ScopeInfo,
  private val alwaysVisible: Boolean,
) : DumbAwareAction() {
  companion object {
    @Deprecated("Use VcsUpdateProcess.checkUpdateHasCustomNotification",
                ReplaceWith("VcsUpdateProcess.checkUpdateHasCustomNotification(vcss)", "com.intellij.openapi.vcs.update.VcsUpdateProcess"))
    @JvmStatic
    fun showsCustomNotification(vcss: Collection<AbstractVcs>): Boolean = VcsUpdateProcess.checkUpdateHasCustomNotification(vcss)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    VcsUpdateProcess.launchUpdate(
      project,
      actionInfo,
      scopeInfo,
      e.dataContext,
      actionName = getTemplatePresentation().text,
      forceShowOptions = OptionsDialog.shiftIsPressed(e.modifiers),
    )
  }

  protected abstract fun filterRootsBeforeAction(): Boolean

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project

    if (project == null) {
      presentation.setEnabledAndVisible(false)
      return
    }

    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val underVcs = vcsManager.hasActiveVcss()
    if (!underVcs) {
      presentation.setEnabledAndVisible(false)
      return
    }

    val scopeName = scopeInfo.getScopeName(e.dataContext, actionInfo)
    var actionName = actionInfo.getActionName(scopeName)
    if (actionInfo.showOptions(project) || OptionsDialog.shiftIsPressed(e.modifiers)) {
      actionName += "..."
    }
    presentation.setText(actionName)

    if (supportingVcsesAreEmpty(vcsManager, actionInfo)) {
      presentation.setEnabledAndVisible(false)
      return
    }

    if (filterRootsBeforeAction()) {
      val roots = VcsUpdateProcess.getRoots(project, actionInfo, scopeInfo, e.dataContext, false)
      if (roots.isEmpty()) {
        presentation.setVisible(alwaysVisible)
        presentation.setEnabled(false)
        return
      }
    }

    val singleVcs = vcsManager.getSingleVCS()
    presentation.setVisible(true)
    presentation.setEnabled(!vcsManager.isBackgroundVcsOperationRunning() && (singleVcs == null || !singleVcs.isUpdateActionDisabled))
  }
}

private fun supportingVcsesAreEmpty(vcsManager: ProjectLevelVcsManager, actionInfo: ActionInfo): Boolean {
  return vcsManager.getAllActiveVcss().all { actionInfo.getEnvironment(it) == null }
}
