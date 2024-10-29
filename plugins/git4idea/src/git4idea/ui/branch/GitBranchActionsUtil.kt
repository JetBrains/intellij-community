// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.dvcs.branch.GroupingKey
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitLocalBranch
import git4idea.GitNotificationIdsHolder.Companion.BRANCHES_UPDATE_SUCCESSFUL
import git4idea.GitReference
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchPair
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitNewBranchDialog
import git4idea.config.GitVcsSettings
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.update.GitUpdateExecutionProcess
import org.jetbrains.annotations.Nls
import javax.swing.Icon

const val GIT_SINGLE_REF_ACTION_GROUP = "Git.Branch"

@JvmOverloads
internal fun createOrCheckoutNewBranch(project: Project,
                                       repositories: Collection<GitRepository>,
                                       startPoint: String,
                                       @Nls(capitalization = Nls.Capitalization.Title)
                                       title: String = GitBundle.message("branches.create.new.branch.dialog.title"),
                                       initialName: String? = null) {
  val options = GitNewBranchDialog(project, repositories, title, initialName, true, true, false, true).showAndGetOptions() ?: return
  GitBranchCheckoutOperation(project, repositories).perform(startPoint, options)
}

internal fun updateBranches(project: Project, repositories: Collection<GitRepository>, localBranchNames: List<String>) {
  val repoToTrackingInfos =
    repositories.associateWith { it.branchTrackInfos.filter { info -> localBranchNames.contains(info.localBranch.name) } }
  if (repoToTrackingInfos.isEmpty()) return

  GitVcs.runInBackground(object : Task.Backgroundable(project, GitBundle.message("branches.updating.process"), true) {
    private val successfullyUpdated = arrayListOf<String>()

    override fun run(indicator: ProgressIndicator) {
      val fetchSupport = GitFetchSupport.fetchSupport(project)
      val currentBranchesMap: MutableMap<GitRepository, GitBranchPair> = HashMap()

      for ((repo, trackingInfos) in repoToTrackingInfos) {
        val currentBranch = repo.currentBranch
        for (trackingInfo in trackingInfos) {
          val localBranch = trackingInfo.localBranch
          val remoteBranch = trackingInfo.remoteBranch
          if (localBranch == currentBranch) {
            currentBranchesMap[repo] = GitBranchPair(currentBranch, remoteBranch)
          }
          else {
            // Fast-forward all non-current branches in the selection
            val localBranchName = localBranch.name
            val remoteBranchName = remoteBranch.nameForRemoteOperations
            val fetchResult = fetchSupport.fetch(repo, trackingInfo.remote, "$remoteBranchName:$localBranchName")
            try {
              fetchResult.throwExceptionIfFailed()
              successfullyUpdated.add(localBranchName)
            }
            catch (ignored: VcsException) {
              fetchResult.showNotificationIfFailed(GitBundle.message("branches.update.failed"))
            }
          }
        }
      }
      // Update all current branches in the selection
      if (currentBranchesMap.isNotEmpty()) {
        GitUpdateExecutionProcess(project,
                                  repositories,
                                  currentBranchesMap,
                                  GitVcsSettings.getInstance(project).updateMethod,
                                  false).execute()
      }
    }

    override fun onSuccess() {
      if (successfullyUpdated.isNotEmpty()) {
        VcsNotifier.getInstance(project).notifySuccess(BRANCHES_UPDATE_SUCCESSFUL, "",
                                                       GitBundle.message("branches.selected.branches.updated.title",
                                                                         successfullyUpdated.size,
                                                                         successfullyUpdated.joinToString("\n")))
      }
    }
  })
}

internal fun isTrackingInfosExist(branchNames: List<String>, repositories: Collection<GitRepository>) =
  repositories
    .flatMap(GitRepository::getBranchTrackInfos)
    .any { trackingBranchInfo -> branchNames.any { branchName -> branchName == trackingBranchInfo.localBranch.name } }

internal fun hasRemotes(project: Project): Boolean {
  return GitUtil.getRepositories(project).any { repository -> !repository.remotes.isEmpty() }
}

internal fun hasTrackingConflicts(conflictingLocalBranches: Map<GitRepository, GitLocalBranch>,
                                  remoteBranchName: String): Boolean =
  conflictingLocalBranches.any { (repo, branch) ->
    val trackInfo = GitBranchUtil.getTrackInfoForBranch(repo, branch)
    trackInfo != null && !GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(remoteBranchName, trackInfo.remoteBranch.name)
  }

internal abstract class BranchGroupingAction(private val key: GroupingKey,
                                             icon: Icon? = null) : ToggleAction(key.text, key.description, icon), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent) =
    e.project?.service<GitBranchManager>()?.isGroupingEnabled(key) ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project ?: return
    project.service<GitBranchManager>().setGrouping(key, state)
  }
}
