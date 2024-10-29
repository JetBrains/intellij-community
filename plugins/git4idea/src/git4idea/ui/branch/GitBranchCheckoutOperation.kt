// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.containers.ContainerUtil
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchOptions
import git4idea.history.GitHistoryUtils
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository

internal class GitBranchCheckoutOperation(private val project: Project, private val repositories: Collection<GitRepository>) {

  private val brancher = GitBrancher.getInstance(project)

  init {
    assert(repositories.isNotEmpty())
  }

  fun perform(startPoint: String, options: GitNewBranchOptions) {
    perform(startPoint, options, null)
  }

  fun perform(startPoint: String, options: GitNewBranchOptions, callInAwtLater: Runnable?) {
    val checkout = options.checkout
    val name = options.name
    val reset = options.reset

    val localHasMoreCommits = checkLocalHasMoreCommits(project, repositories, name, startPoint)
    if (checkout) {
      performCheckout(startPoint, name, localHasMoreCommits, reset, callInAwtLater)
    }
    else {
      performCreate(startPoint, name, localHasMoreCommits, reset, callInAwtLater)
    }
  }

  private fun performCheckout(startPoint: String,
                              name: String,
                              localHasMoreCommits: Boolean,
                              reset: Boolean,
                              callInAwtLater: Runnable? = null) {
    if (localHasMoreCommits) {
      if (reset) {
        val result = Messages.showYesNoCancelDialog(
          GitBundle.message("branches.checkout.with.reset.local.has.more.commits", name, startPoint),
          GitBundle.message("checkout.0", startPoint),
          GitBundle.message("checkout.and.rebase"), GitBundle.message("branches.drop.local.commits"), IdeBundle.message("button.cancel"),
          null)
        when (result) {
          Messages.YES -> checkout(startPoint, name, CheckoutConflictResolution.REBASE, callInAwtLater = callInAwtLater)
          Messages.NO -> checkout(startPoint, name, CheckoutConflictResolution.RESET, callInAwtLater = callInAwtLater)
          Messages.CANCEL -> return
        }
      }
      else {
        val result = Messages.showYesNoCancelDialog(
          GitBundle.message("branches.checkout.local.has.more.commits", name, startPoint),
          GitBundle.message("checkout.0", startPoint),
          GitBundle.message("checkout.and.rebase"), GitBundle.message("branches.checkout.local"), IdeBundle.message("button.cancel"),
          null)
        when (result) {
          Messages.YES -> checkout(startPoint, name, CheckoutConflictResolution.REBASE, callInAwtLater = callInAwtLater)
          Messages.NO -> checkout(startPoint, name, CheckoutConflictResolution.USE_LOCAL, callInAwtLater = callInAwtLater)
          Messages.CANCEL -> return
        }
      }
    }
    else {
      checkout(startPoint, name, if (reset) CheckoutConflictResolution.RESET else CheckoutConflictResolution.TRY, callInAwtLater = callInAwtLater)
    }
  }

  private fun performCreate(startPoint: String, name: String, localHasMoreCommits: Boolean, reset: Boolean, callInAwtLater: Runnable?) {
    if (localHasMoreCommits) {
      if (reset) {
        val result = Messages.showYesNoDialog(
          GitBundle.message("branches.create.with.reset.local.has.more.commits", name, startPoint),
          GitBundle.message("checkout.0", startPoint),
          GitBundle.message("branches.drop.local.commits"), IdeBundle.message("button.cancel"), null)
        if (result == Messages.YES) create(startPoint, name, true, callInAwtLater)
      }
      else {
        val result = Messages.showYesNoDialog(
          GitBundle.message("branches.create.local.has.more.commits", name, startPoint),
          GitBundle.message("checkout.0", startPoint),
          GitBundle.message("new.branch.dialog.operation.create.name"), IdeBundle.message("button.cancel"), null)
        if (result == Messages.YES) create(startPoint, name, false, callInAwtLater)
      }
    }
    else {
      create(startPoint, name, reset, callInAwtLater)
    }
  }

  private fun checkout(startPoint: String, name: String, localConflictResolution: CheckoutConflictResolution, callInAwtLater: Runnable? = null) {
    val (reposWithLocalBranch, reposWithoutLocalBranch) = repositories.partition { it.branches.findLocalBranch(name) != null }

    //checkout existing branch
    if (reposWithLocalBranch.isNotEmpty()) when (localConflictResolution) {
      CheckoutConflictResolution.USE_LOCAL -> brancher.checkout(name, false, reposWithLocalBranch, callInAwtLater)
      CheckoutConflictResolution.RESET -> brancher.checkoutNewBranchStartingFrom(name, startPoint, true, reposWithLocalBranch, callInAwtLater)
      CheckoutConflictResolution.REBASE -> brancher.rebase(reposWithLocalBranch, startPoint, name)
      CheckoutConflictResolution.TRY -> brancher.checkoutNewBranchStartingFrom(name, startPoint, false, reposWithLocalBranch, callInAwtLater)
    }

    //checkout new
    if (reposWithoutLocalBranch.isNotEmpty()) {
      brancher.checkoutNewBranchStartingFrom(name, startPoint, false, reposWithoutLocalBranch, callInAwtLater)
    }
  }

  private fun create(startPoint: String, name: String, reset: Boolean, callInAwtLater: Runnable?) {
    val (reposWithLocalBranch, reposWithoutLocalBranch) = repositories.partition { it.branches.findLocalBranch(name) != null }

    if (reposWithLocalBranch.isNotEmpty() && reset) {
      val (currentBranchOfSameName, currentBranchOfDifferentName) = reposWithLocalBranch.partition { it.currentBranchName == name }
      //git checkout -B for current branches with the same name (cannot force update current branch) and git branch -f for not current
      if (currentBranchOfSameName.isNotEmpty()) {
        brancher.checkoutNewBranchStartingFrom(name, startPoint, true, currentBranchOfSameName, callInAwtLater)
      }
      if (currentBranchOfDifferentName.isNotEmpty()) {
        brancher.createBranch(name, currentBranchOfDifferentName.associateWith { startPoint }, true, callInAwtLater)
      }
    }

    if (reposWithoutLocalBranch.isNotEmpty()) {
      brancher.createBranch(name, reposWithoutLocalBranch.associateWith { startPoint }, callInAwtLater)
    }
  }

  companion object {
    private val LOG = logger<GitBranchCheckoutOperation>()

    internal fun checkLocalHasMoreCommits(project: Project,
                                         repositories: Collection<GitRepository>,
                                         localBranch: String, startPoint: String): Boolean {
      val existingLocalBranches = ContainerUtil.map2MapNotNull(repositories) { r: GitRepository ->
        val local = r.branches.findLocalBranch(localBranch)
        if (local != null) Pair.create(r, local) else null
      }

      val existingLocalHasCommits = if (existingLocalBranches.isNotEmpty()) {
        checkCommitsUnderProgress(project, existingLocalBranches.keys.toList(), startPoint, localBranch)
      }
      else false
      return existingLocalHasCommits
    }

    private fun checkCommitsUnderProgress(project: Project,
                                          repositories: List<GitRepository>,
                                          startRef: String,
                                          branchName: String): Boolean =
      ProgressManager.getInstance().runProcessWithProgressSynchronously<Boolean, RuntimeException>(
        { checkCommitsBetweenRefAndBranchName(project, repositories, startRef, branchName) },
        GitBundle.message("branches.checking.existing.commits.process"), true, project)

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
        LOG.warn("Couldn't collect commits in ${repository.presentableUrl} for $startRef..$endRef") // NON-NLS
        return true
      }
    }


    private enum class CheckoutConflictResolution {
      // just use local
      USE_LOCAL,

      // reset local to target
      RESET,

      // rebase local on target
      REBASE,

      // just try as is
      TRY
    }
  }
}
