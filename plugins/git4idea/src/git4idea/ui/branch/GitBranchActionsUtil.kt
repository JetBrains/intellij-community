// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.dvcs.branch.GroupingKey
import com.intellij.dvcs.branch.isGroupingEnabled
import com.intellij.dvcs.branch.setGrouping
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchDialog
import git4idea.branch.GitNewBranchOptions
import git4idea.config.GitVcsSettings
import git4idea.fetch.GitFetchSupport
import git4idea.history.GitHistoryUtils
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import javax.swing.Icon

object L {
  val LOG: Logger = Logger.getInstance(L::class.java)
}

internal fun checkCommitsUnderProgress(project: Project,
                                       repositories: List<GitRepository>,
                                       startRef: String,
                                       branchName: String): Boolean =
  ProgressManager.getInstance().runProcessWithProgressSynchronously<Boolean, RuntimeException>({
                                                                                                 checkCommitsBetweenRefAndBranchName(
                                                                                                   project, repositories, startRef,
                                                                                                   branchName)
                                                                                               }, GitBundle.message(
    "branches.checking.existing.commits.process"), true, project)

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
    L.LOG.warn("Couldn't collect commits in ${repository.presentableUrl} for $startRef..$endRef") // NON-NLS
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
        .notifyError(GitBundle.message("branches.checkout.failed.title"),
                     GitBundle.message("branches.checkout.failed.description", name))
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
      VcsNotifier.getInstance(project).notifyError(GitBundle.message("branches.creation.failed.title"),
                                                   GitBundle.message("branches.checkout.failed.description", name))
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
                                       @Nls(capitalization = Nls.Capitalization.Title)
                                       title: String = GitBundle.message("branches.create.new.branch.dialog.title"),
                                       initialName: String? = null) {
  val options = GitNewBranchDialog(project, repositories, title, initialName, true, true, false, true).showAndGetOptions() ?: return
  if (options.checkout) {
    checkoutOrReset(project, repositories, startPoint, options)
  }
  else {
    createNewBranch(project, repositories, startPoint, options)
  }
}

internal fun updateBranches(project: Project, repositories: List<GitRepository>, localBranchNames: List<String>) {
  val repoToTrackingInfos =
    repositories.associateWith { it.branchTrackInfos.filter { info -> localBranchNames.contains(info.localBranch.name) } }
  if (repoToTrackingInfos.isEmpty()) return

  GitVcs.runInBackground(object : Task.Backgroundable(project, GitBundle.message("branches.updating.process"), true) {
    var successFetches = 0
    override fun run(indicator: ProgressIndicator) {
      val fetchSupport = GitFetchSupport.fetchSupport(project)
      for ((repo, trackingInfos) in repoToTrackingInfos) {
        for (trackingInfo in trackingInfos) {
          val localBranchName = trackingInfo.localBranch.name
          val remoteBranchName = trackingInfo.remoteBranch.nameForRemoteOperations
          val fetchResult = fetchSupport.fetch(repo, trackingInfo.remote, "$remoteBranchName:$localBranchName")
          try {
            fetchResult.throwExceptionIfFailed()
            successFetches += 1
          }
          catch (ignored: VcsException) {
            fetchResult.showNotificationIfFailed(GitBundle.message("branches.update.failed"))
          }
        }
      }
    }

    override fun onSuccess() {
      if (successFetches > 0) {
        VcsNotifier.getInstance(myProject).notifySuccess(GitBundle.message("branches.selected.branches.updated.title",
                                                                           localBranchNames.size))
      }
    }
  })
}

internal fun isTrackingInfosExist(branchNames: List<String>, repositories: List<GitRepository>) =
  repositories
    .flatMap(GitRepository::getBranchTrackInfos)
    .any { trackingBranchInfo -> branchNames.any { branchName -> branchName == trackingBranchInfo.localBranch.name } }

internal fun hasRemotes(project: Project): Boolean {
  return GitUtil.getRepositories(project).any { repository -> !repository.remotes.isEmpty() }
}

internal abstract class BranchGroupingAction(private val key: GroupingKey,
                                             icon: Icon? = null) : ToggleAction(key.text, key.description, icon), DumbAware {

  abstract fun setSelected(key: GroupingKey, state: Boolean)

  override fun isSelected(e: AnActionEvent) =
    e.project?.let { GitVcsSettings.getInstance(it).branchSettings.isGroupingEnabled(key) } ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    val branchSettings = GitVcsSettings.getInstance(project).branchSettings
    branchSettings.setGrouping(key, state)
    setSelected(key, state)
  }
}
