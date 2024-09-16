// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.ref

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.GitTag
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.remote.hosting.GitRemoteBranchesUtil
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository

class GitCheckoutAction
  : GitSingleRefAction<GitReference>(GitBundle.messagePointer("branches.checkout")) {

  override fun isEnabledForRef(ref: GitReference, repositories: List<GitRepository>): Boolean {
    if (ref !is GitBranch && ref !is GitTag) return false

    return if (isCurrentRefInAnyRepo(ref, repositories)) repositories.diverged() else true
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitReference) {
    if (reference is GitRemoteBranch) {
      GitRemoteBranchesUtil.checkoutRemoteBranch(project, repositories, reference.name)
    }
    else {
      GitBrancher.getInstance(project).checkout(reference.fullName, false, repositories, null)
    }
  }
}

private fun List<GitRepository>.diverged(): Boolean {
  var sameRef: GitReference? = null

  for (repo in this) {
    val ref = GitRefUtil.getCurrentReference(repo)
    if (sameRef == null) {
      sameRef = ref
    }
    else if (sameRef != ref) return true
  }

  return false
}