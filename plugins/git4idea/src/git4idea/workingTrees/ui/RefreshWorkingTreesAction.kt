// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY
import git4idea.workingTrees.GitWorkingTreesNewBadgeUtil

internal class RefreshWorkingTreesAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val repository = e.getData(CURRENT_REPOSITORY)
    e.presentation.isEnabled = repository != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitWorkingTreesNewBadgeUtil.workingTreesFeatureWasUsed()
    val repository = e.getData(CURRENT_REPOSITORY) ?: return
    repository.workingTreeHolder.scheduleReload()
  }
}
