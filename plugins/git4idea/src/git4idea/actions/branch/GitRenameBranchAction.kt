// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.branch.GitBranchOperationType
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchDialog
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository

class GitRenameBranchAction : GitSingleBranchAction(ActionsBundle.messagePointer("action.RenameAction.text")) {

  override val disabledForRemote = true

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    val branchName = branch.name
    val options = GitNewBranchDialog(project, repositories,
                                     GitBundle.message("branches.rename.branch", branchName),
                                     branchName,
                                     false, false,
                                     false, false, GitBranchOperationType.RENAME).showAndGetOptions()
    if (options != null) {
      GitBrancher.getInstance(project).renameBranch(branchName, options.name, repositories)
    }
  }
}