// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import git4idea.GitUtil
import git4idea.actions.branch.GitBranchActionsUtil.getAffectedRepositories
import git4idea.ui.branch.createOrCheckoutNewBranch

class GitNewBranchAction
  : DumbAwareAction(DvcsBundle.messagePointer("new.branch.action.text.with.ellipsis"),
                    DvcsBundle.messagePointer("new.branch.action.description"),
                    AllIcons.General.Add) {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val repositories = getAffectedRepositories(e)
    e.presentation.isEnabledAndVisible = project != null && !repositories.isEmpty()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val repositories = getAffectedRepositories(e)
    createOrCheckoutNewBranch(e.project!!, repositories, GitUtil.HEAD,
                              initialName = repositories.getCommonCurrentBranch())
  }
}
