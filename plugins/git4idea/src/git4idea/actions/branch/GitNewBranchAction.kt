// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.GitUtil
import git4idea.actions.branch.GitBranchActionsUtil.getRepositoriesForTopLevelActions
import git4idea.repo.GitRepository
import git4idea.ui.branch.createOrCheckoutNewBranch
import git4idea.ui.branch.popup.GitBranchesTreePopupStep.Companion.TOP_LEVEL_ACTION_PLACE

class GitNewBranchAction
  : DumbAwareAction(DvcsBundle.messagePointer("new.branch.action.text.with.ellipsis"),
                    DvcsBundle.messagePointer("new.branch.action.description"),
                    AllIcons.General.Add) {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val repositories = getRepositoriesForTopLevelActions(e) { it.place == TOP_LEVEL_ACTION_PLACE }
    val visible = project != null && !repositories.isEmpty()
    e.presentation.isVisible = visible
    e.presentation.isEnabled = visible && !repositories.all(GitRepository::isFresh)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val repositories = getRepositoriesForTopLevelActions(e) { it.place == TOP_LEVEL_ACTION_PLACE }
    createOrCheckoutNewBranch(e.project!!, repositories, GitUtil.HEAD,
                              initialName = repositories.getCommonCurrentBranch())
  }
}
