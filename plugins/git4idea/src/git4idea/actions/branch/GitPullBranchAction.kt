// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchPair
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.GitBranchPopupActions.addTooltipText
import git4idea.update.GitUpdateExecutionProcess
import java.util.function.Supplier

sealed class GitPullBranchAction(dynamicText: Supplier<@NlsActions.ActionText String>,
                                 private val updateMethod: UpdateMethod) : GitSingleBranchAction(dynamicText) {

  override val disabledForLocal = true

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    if (branch !is GitRemoteBranch) return
    GitUpdateExecutionProcess.launchUpdate(project, repositories,
                                           configureTarget(repositories, branch),
                                           updateMethod, false)
  }

  companion object {
    private fun configureTarget(repositories: List<GitRepository>, branch: GitRemoteBranch): Map<GitRepository, GitBranchPair> {
      val map = LinkedHashMap<GitRepository, GitBranchPair>()
      for (repo in repositories) {
        val currentBranch = repo.currentBranch
        if (currentBranch != null) {
          map[repo] = GitBranchPair(currentBranch, branch)
        }
      }
      return map
    }
  }

  class WithMerge : GitPullBranchAction(GitBundle.messagePointer("branches.action.pull.into.branch.using.merge.selected"),
                                        UpdateMethod.MERGE) {

    override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
      with(e.presentation) {
        text = GitBundle.message("branches.action.pull.into.branch.using.merge",
                                 GitBranchPopupActions.getCurrentBranchTruncatedPresentation(project, repositories))

        description = GitBundle.message("branches.action.pull.into.branch.using.merge.description",
                                        GitBranchPopupActions.getCurrentBranchFullPresentation(project, repositories))

        addTooltipText(this, description)
      }
    }
  }

  class WithRebase : GitPullBranchAction(GitBundle.messagePointer("branches.action.pull.into.branch.using.rebase.selected"),
                                         UpdateMethod.REBASE) {

    override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
      with(e.presentation) {
        text = GitBundle.message("branches.action.pull.into.branch.using.rebase",
                                 GitBranchPopupActions.getCurrentBranchTruncatedPresentation(project, repositories))

        description = GitBundle.message("branches.action.pull.into.branch.using.rebase.description",
                                        GitBranchPopupActions.getCurrentBranchFullPresentation(project, repositories))

        addTooltipText(this, description)
      }
    }
  }
}