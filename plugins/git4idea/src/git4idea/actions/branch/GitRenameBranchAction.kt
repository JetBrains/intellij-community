// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    val hasUpstream = repositories.any { repository ->
      repository.getBranchTrackInfo(branchName) != null
    }

    val options = GitNewBranchDialog(project, repositories,
                                     GitBundle.message("branches.rename.branch", branchName),
                                     branchName,
                                     false, showUnsetUpstreamOption = hasUpstream,
                                     operation = GitBranchOperationType.RENAME).showAndGetOptions()
    if (options != null) {
      if (!options.unsetUpstream) {
        GitBrancher.getInstance(project).renameBranch(branchName, options.name, options.repositories.toList())
      }
      else {
        GitBrancher.getInstance(project).renameBranchAndUnsetUpstream(branchName, options.name, options.repositories.toList())
      }
    }
  }
}
