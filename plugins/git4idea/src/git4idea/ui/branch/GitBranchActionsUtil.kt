// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchDialog
import git4idea.branch.GitNewBranchOptions
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

internal fun checkoutOrReset(project: Project,
                             repositories: List<GitRepository>,
                             startPoint: String,
                             newBranchOptions: GitNewBranchOptions) {
  if (repositories.isEmpty()) return
  val name = newBranchOptions.name
  if (!newBranchOptions.reset) {
    checkout(project, repositories, startPoint, name, false)
  }
  else {
    val hasCommits = checkCommitsUnderProgress(project, repositories, startPoint, name)
    if (hasCommits) {
      VcsNotifier.getInstance(project)
        .notifyError("Checkout Failed", "Can't overwrite $name branch because some commits can be lost")
      return
    }
    val brancher = GitBrancher.getInstance(project)
    brancher.checkoutNewBranchStartingFrom(name, startPoint, true, repositories, null)
  }
}

internal fun createNewBranch(project: Project, repositories: List<GitRepository>, startPoint: String, options: GitNewBranchOptions) {
  val brancher = GitBrancher.getInstance(project)
  val name = options.name
  if (options.reset) {
    val hasCommits = checkCommitsUnderProgress(project, repositories, startPoint, name)
    if (hasCommits) {
      VcsNotifier.getInstance(project).notifyError("New Branch Creation Failed",
                                                   "Can't overwrite $name branch because some commits can be lost")
      return
    }

    val (currentBranchOfSameName, currentBranchOfDifferentName) = repositories.partition { it.currentBranchName == name }
    //git checkout -B for current branch conflict and execute git branch -f for others
    if (currentBranchOfSameName.isNotEmpty()) {
      brancher.checkoutNewBranchStartingFrom(name, startPoint, true, currentBranchOfSameName, null)
    }
    if (currentBranchOfDifferentName.isNotEmpty()) {
      brancher.createBranch(name, currentBranchOfDifferentName.associateWith { startPoint }, true)
    }
  }
  else {
    // create branch for other repos
    brancher.createBranch(name, repositories.filter { it.branches.findLocalBranch(name) == null }.associateWith { startPoint })
  }
}

@JvmOverloads
internal fun createOrCheckoutNewBranch(project: Project,
                                       repositories: List<GitRepository>,
                                       startPoint: String,
                                       title: String = "Create New Branch",
                                       initialName: String? = null) {
  val options = GitNewBranchDialog(project, repositories, title, initialName, true, true, true).showAndGetOptions() ?: return
  if (options.checkout) {
    checkoutOrReset(project, repositories, startPoint, options)
  }
  else {
    createNewBranch(project, repositories, startPoint, options)
  }
}
