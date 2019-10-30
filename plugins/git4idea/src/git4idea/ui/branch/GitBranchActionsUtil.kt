// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import git4idea.branch.GitBrancher
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository

private val LOG: Logger get() = logger(::LOG)

internal fun checkCommitsUnderProgress(project: Project,
                                       repositories: List<GitRepository>,
                                       startRef: String,
                                       branchName: String): Boolean =
  ProgressManager.getInstance().runProcessWithProgressSynchronously<Boolean, RuntimeException>({
    checkCommitsBetweenRefAndBranchName(project, repositories, startRef, branchName)
  }, "Checking Existing Commits...", true, project)

private fun checkCommitsBetweenRefAndBranchName(project: Project,
                                                repositories: List<GitRepository>,
                                                startRef: String,
                                                branchName: String): Boolean {
  return repositories.any {
    val existingBranch = it.branches.findLocalBranch(branchName)
    existingBranch != null && hasCommits(project, it, startRef, existingBranch.name)
  }
}

private fun hasCommits(project: Project, repository: GitRepository, startRef: String, endRef: String): Boolean {
  try {
    return GitHistoryUtils.collectTimedCommits(project, repository.root, "$startRef..$endRef").isNotEmpty()
  }
  catch (ex: VcsException) {
    LOG.warn("Couldn't collect commits in ${repository.presentableUrl} for $startRef..$endRef")
    return true
  }
}

internal fun checkout(project: Project, repositories: List<GitRepository>, startPoint: String, name: String, withRebase: Boolean) {
  val brancher = GitBrancher.getInstance(project)
  val (reposWithLocalBranch, reposWithoutLocalBranch) = repositories.partition { it.branches.findLocalBranch(name) != null }
  //checkout/rebase existing branch
  if (reposWithLocalBranch.isNotEmpty()) {
    if (withRebase) brancher.rebase(reposWithLocalBranch, startPoint, name)
    else brancher.checkout(name, false, reposWithLocalBranch, null)
  }
  //checkout new
  if (reposWithoutLocalBranch.isNotEmpty()) brancher.checkoutNewBranchStartingFrom(name, startPoint, reposWithoutLocalBranch, null)
}
