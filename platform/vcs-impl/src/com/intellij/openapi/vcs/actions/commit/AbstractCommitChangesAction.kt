// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions.commit

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.vcsUtil.VcsUtil

abstract class AbstractCommitChangesAction : DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    CheckinActionUtil.updateCommonCommitAction(e)

    val presentation = e.presentation
    if (presentation.isEnabled) {
      val changes = e.getData(VcsDataKeys.CHANGES).orEmpty()

      if (e.place == ActionPlaces.CHANGES_VIEW_POPUP) {
        val changeLists = e.getData(VcsDataKeys.CHANGE_LISTS).orEmpty()

        presentation.isEnabled = when {
          changeLists.isEmpty() -> changes.isNotEmpty()
          changeLists.size == 1 -> changeLists.single().changes.isNotEmpty()
          else -> false
        }
      }

      if (presentation.isEnabled) {
        val manager = ChangeListManager.getInstance(e.project!!)
        presentation.isEnabled = changes.all { isActionEnabled(manager, it) }
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val initialChangeList = CheckinActionUtil.getInitiallySelectedChangeList(project, e)
    val pathsToCommit = ProjectLevelVcsManager.getInstance(project).allVersionedRoots
      .map { VcsUtil.getFilePath(it) }

    val executor = getExecutor(project)

    CheckinActionUtil.performCommonCommitAction(e, project, initialChangeList, pathsToCommit,
                                                ActionsBundle.message("action.CheckinProject.text"),
                                                executor, false)
  }

  protected abstract fun getExecutor(project: Project): CommitExecutor

  protected open fun isActionEnabled(manager: ChangeListManager, it: Change) = manager.getChangeList(it) != null
}
