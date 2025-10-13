// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.GitDisposable
import git4idea.GitLocalBranch
import git4idea.GitNotificationIdsHolder.Companion.BRANCHES_UPDATE_SUCCESSFUL
import git4idea.GitReference
import git4idea.GitUtil
import git4idea.branch.GitBranchPair
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitNewBranchDialog
import git4idea.config.GitVcsSettings
import git4idea.fetch.GitFetchResult
import git4idea.fetch.GitFetchSpec
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.update.GitUpdateExecutionProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.Icon

@JvmOverloads
internal fun createOrCheckoutNewBranch(
  project: Project,
  repositories: Collection<GitRepository>,
  startPoint: String,
  @Nls(capitalization = Nls.Capitalization.Title)
  title: String = GitBundle.message("branches.create.new.branch.dialog.title"),
  initialName: String? = null,
) {
  val options = GitNewBranchDialog(project, repositories, title, initialName, showResetOption = true, localConflictsAllowed = true).showAndGetOptions()
                ?: return
  GitBranchCheckoutOperation(project, options.repositories).perform(startPoint, options)
}

internal fun updateBranches(project: Project, repositories: Collection<GitRepository>, localBranchNames: List<String>): Job {
  return GitDisposable.getInstance(project).coroutineScope.launch {
    withBackgroundProgress(project, GitBundle.message("branches.updating.process")) {
      val (fetchTargets, updateProcessTargets) = prepareUpdateTargets(repositories, localBranchNames)

      if (fetchTargets.isNotEmpty()) {
        fetchBranches(project, fetchTargets, updateHeadOk = false)
      }

      if (updateProcessTargets.isNotEmpty()) {
        GitUpdateExecutionProcess.update(project,
                                         repositories,
                                         updateProcessTargets,
                                         GitVcsSettings.getInstance(project).updateMethod,
                                         false)
      }
    }
  }
}

@VisibleForTesting
internal fun prepareUpdateTargets(
  repositories: Collection<GitRepository>, localBranchNames: List<String>,
): UpdateTargets {
  val repoToTrackingInfos =
    repositories.associateWith { it.branchTrackInfos.filter { info -> localBranchNames.contains(info.localBranch.name) } }

  // If a branch is checked out, do update via GitUpdateExecutionProcess
  val updateProcessTargets = HashMap<GitRepository, GitBranchPair>()
  // Otherwise, perform fetch using remote:local refspec
  val fetchTargets = mutableListOf<GitFetchSpec>()

  for ((repo, trackingInfos) in repoToTrackingInfos) {
    val currentBranch = repo.currentBranch
    for (trackingInfo in trackingInfos) {
      val localBranch = trackingInfo.localBranch
      val remoteBranch = trackingInfo.remoteBranch
      if (localBranch == currentBranch) {
        updateProcessTargets[repo] = GitBranchPair(currentBranch, remoteBranch)
      }
      else {
        // Fast-forward all non-current branches in the selection
        val localBranchName = localBranch.name
        val remoteBranchName = remoteBranch.nameForRemoteOperations
        fetchTargets.add(GitFetchSpec(repo, trackingInfo.remote, "$remoteBranchName:$localBranchName"))
      }
    }
  }

  return UpdateTargets(fetchTargets, updateProcessTargets)
}

internal suspend fun fetchBranches(
  project: Project,
  fetchTargets: List<GitFetchSpec>,
  updateHeadOk: Boolean,
): GitFetchResult {
  return withContext(Dispatchers.Default) {
    val specsToFetch = if (updateHeadOk) {
      fetchTargets.map { it.copy(updateHeadOk = true) }
    }
    else {
      fetchTargets
    }
    val result = coroutineToIndicator {
      GitFetchSupport.fetchSupport(project).fetch(specsToFetch)
    }
    val fetchSuccessful = result.showNotificationIfFailed(GitBundle.message("branches.update.failed"))
    if (fetchSuccessful) {
      VcsNotifier.getInstance(project).notifySuccess(BRANCHES_UPDATE_SUCCESSFUL, "", GitBundle.message("branches.fetch.finished", specsToFetch.size))
    }
    result
  }
}

internal fun isTrackingInfosExist(branchNames: List<String>, repositories: Collection<GitRepository>) =
  repositories
    .flatMap(GitRepository::getBranchTrackInfos)
    .any { trackingBranchInfo -> branchNames.any { branchName -> branchName == trackingBranchInfo.localBranch.name } }

internal fun hasRemotes(project: Project): Boolean {
  return hasAnyRemotes(GitUtil.getRepositories(project))
}

internal fun hasAnyRemotes(repositories: Collection<GitRepository>): Boolean = repositories.any { it.remotes.isNotEmpty() }

internal fun hasTrackingConflicts(
  conflictingLocalBranches: Map<GitRepository, GitLocalBranch>,
  remoteBranchName: String,
): Boolean =
  conflictingLocalBranches.any { (repo, branch) ->
    val trackInfo = GitBranchUtil.getTrackInfoForBranch(repo, branch)
    trackInfo != null && !GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(remoteBranchName, trackInfo.remoteBranch.name)
  }

internal abstract class BranchGroupingAction(
  private val key: GroupingKey,
  icon: Icon? = null,
) : ToggleAction(key.text, key.description, icon), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return GitVcsSettings.getInstance(project).branchSettings.isGroupingEnabled(key)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    GitVcsSettings.getInstance(project).setBranchGroupingSettings(key, state)
  }
}

@VisibleForTesting
internal data class UpdateTargets(
  val fetchTargets: List<GitFetchSpec>,
  val updateProcessTargets: Map<GitRepository, GitBranchPair>,
)