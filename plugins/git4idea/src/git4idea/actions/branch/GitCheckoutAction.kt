// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.diverged
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.repo.GitRepository

class GitCheckoutAction
  : GitSingleBranchAction(GitBundle.messagePointer("branches.checkout")) {

  override fun isEnabledForRef(ref: GitBranch, repositories: List<GitRepository>)=
    if (isCurrentRefInAnyRepo(ref, repositories)) repositories.diverged() else true

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, branch: GitBranch) {
    if (branch.isRemote) {
      GitRemoteBranchesUtil.checkoutRemoteBranch(project, repositories, branch.name)
    }
    else {
      GitBrancher.getInstance(e.project!!).checkout(branch.name, false, repositories, null)
    }
  }
}
