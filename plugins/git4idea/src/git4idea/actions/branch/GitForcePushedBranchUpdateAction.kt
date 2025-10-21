// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.cherrypick.VcsCherryPickManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.UpdatedFiles
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.vcs.VcsActivity
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitNotificationIdsHolder
import git4idea.GitOperationsCollector.UPDATE_FORCE_PUSHED_BRANCH_ACTIVITY
import git4idea.GitRemoteBranch
import git4idea.GitUtil.HEAD
import git4idea.GitVcs
import git4idea.branch.GitBranchPair
import git4idea.branch.GitBranchUiHandlerImpl
import git4idea.branch.GitBranchWorker
import git4idea.commands.Git
import git4idea.config.GitVcsSettings
import git4idea.fetch.GitFetchSupport
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.reset.GitResetMode
import git4idea.reset.GitResetOperation
import git4idea.update.*
import git4idea.update.GitUpdateInfoAsLog.NotificationData
import git4idea.util.GitPreservingProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.util.*

/**
 * Executes update for possible force pushed current branch for Git repositories in the current project.
 * If no local commits exist in the current branch or there are merge commits - will execute "Update Project" routine.
 *
 * Update process contains the following steps:
 *
 * * Calculate non-pushed commits
 * * Backup current local branch
 * * Hard-reset the current branch on the fetched remote head
 * * Cherry-pick commits from backup
 *
 * @see GitUpdateExecutionProcess
 */
internal class GitForcePushedBranchUpdateAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null
                                         && GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.service<GitForcePushedBranchUpdateExecutor>()?.updateCurrentBranch()
  }
}

@Service(Service.Level.PROJECT)
internal class GitForcePushedBranchUpdateExecutor(private val project: Project, internal val coroutineScope: CoroutineScope) {
  fun updateCurrentBranch() {
    coroutineScope.launch(Dispatchers.IO) {
      withBackgroundProgress(project, GitBundle.message("action.git.update.force.pushed.branch.progress")) {

        val repositories = GitRepositoryManager.getInstance(project).repositories
        val updateResults = hashMapOf<GitRepository, GitUpdateResult>()
        val (toUpdate, skippedRoots) = calculateToUpdate(repositories)
        val updateRanges = GitUpdatedRanges.calcInitialPositions(project, toUpdate)
        val updateActivity = UPDATE_FORCE_PUSHED_BRANCH_ACTIVITY.started(project)

        try {
          for ((repository, branchPair) in toUpdate) {
            val result = updateRepository(repository, branchPair)
            updateResults[repository] = result
          }
        }
        finally {
          updateActivity.finished()
        }

        val updatedPositions = updateRanges.calcCurrentPositions()
        val updateNotificationData = GitUpdateInfoAsLog(project, updatedPositions).calculateDataAndCreateLogTab()
        notify(project, updateResults, skippedRoots, updateNotificationData)
      }
    }
  }

  private suspend fun updateRepository(repository: GitRepository, branchPair: GitBranchPair): GitUpdateResult {
    val branch = branchPair.source
    val trackedBranch = (branchPair.target as? GitRemoteBranch) ?: return GitUpdateResult.NOTHING_TO_UPDATE

    val localCommits = coroutineToIndicator {
      GitLogUtil.collectMetadata(project, repository.root,
                                 "${trackedBranch.nameForLocalOperations}..${branch.name}").commits
    }
    val updateConfig = mapOf(repository to branchPair)
    if (localCommits.containsMergeCommits()) { //merge commits cannot be cherry-picked as is
      GitUpdateExecutionProcess.update(project,
                                       listOf(repository),
                                       updateConfig,
                                       GitVcsSettings.getInstance(project).updateMethod,
                                       false)
      return GitUpdateResult.SUCCESS
    }

    val updateNotReady = coroutineToIndicator {
      GitUpdateProcess(project, it, listOf(repository), UpdatedFiles.create(), updateConfig, false, false).isUpdateNotReady
    }
    if (updateNotReady) {
      return GitUpdateResult.NOT_READY
    }

    val needBackup = localCommits.isNotEmpty()

    val backupBranchName = branch.name + "-" + UUID.randomUUID()
    return coroutineToIndicator { indicator ->
      val branchWorker = GitBranchWorker(project, Git.getInstance(), GitBranchUiHandlerImpl(project, indicator))
      if (needBackup) {
        branchWorker.createBranch(backupBranchName, mapOf(repository to HEAD))
      }

      val fetchSuccessful =
        GitFetchSupport.fetchSupport(project)
          .fetch(repository, trackedBranch.remote, trackedBranch.nameForRemoteOperations)
          .showNotificationIfFailed(GitBundle.message("notification.title.update.failed"))

      if (!fetchSuccessful) {
        return@coroutineToIndicator GitUpdateResult.NOT_READY
      }

      updateWithPreserveChanges(repository) {
        val resetSuccess = GitResetOperation(project, mapOf(repository to trackedBranch.nameForLocalOperations),
                                             GitResetMode.HARD, indicator, GitResetOperation.OperationPresentation()).execute()
        if (!resetSuccess) {
          return@updateWithPreserveChanges GitUpdateResult.ERROR
        }

        if (needBackup) {
          val gitCherryPick = VcsCherryPickManager.getInstance(project).getCherryPickerFor(GitVcs.getKey())
          if (gitCherryPick == null) {
            return@updateWithPreserveChanges GitUpdateResult.NOT_READY
          }

          val allCherryPicked = gitCherryPick.cherryPick(localCommits.reversed())

          if (allCherryPicked) {
            branchWorker.deleteBranch(backupBranchName, listOf(repository))
          }
          else {
            VcsNotifier.getInstance(project)
              .notifyImportantWarning(GitNotificationIdsHolder.BRANCH_UPDATE_FORCE_PUSHED_BRANCH_NOT_ALL_CHERRY_PICKED, "",
                                      GitBundle.message("action.git.update.force.pushed.branch.not.all.local.commits.chery.picked", backupBranchName))
            return@updateWithPreserveChanges GitUpdateResult.INCOMPLETE
          }
        }

        return@updateWithPreserveChanges GitUpdateResult.SUCCESS
      }
    }
  }

  private fun updateWithPreserveChanges(repository: GitRepository,
                                        operation: () -> GitUpdateResult): GitUpdateResult {
    val result = Ref.create<GitUpdateResult>()
    val saveMethod = GitVcsSettings.getInstance(project).saveChangesPolicy
    val indicator = ProgressManager.getInstance().progressIndicator
    DvcsUtil.workingTreeChangeStarted(project, VcsBundle.message("activity.name.update"), VcsActivity.Update).use {
      GitPreservingProcess(project, Git.getInstance(), setOf(repository.getRoot()),
                           GitBundle.message("git.update.operation"),
                           GitBundle.message("progress.update.destination.remote"), saveMethod, indicator)
      { result.set(operation()) }.execute()
    }

    return result.get()
  }

  private fun calculateToUpdate(repositories: List<GitRepository>): Update {
    val update = hashMapOf<GitRepository, GitBranchPair>()
    val skipped = linkedMapOf<GitRepository, @Nls String>()

    for (repository in repositories) {
      val branch = repository.currentBranch
      if (branch == null) {
        skipped[repository] = GitBundle.message("update.skip.root.reason.detached.head")
        continue
      }

      val trackedBranch = branch.findTrackedBranch(repository)
      if (trackedBranch == null) {
        skipped[repository] = GitBundle.message("update.skip.root.reason.no.tracked.branch")
        continue
      }

      update[repository] = GitBranchPair(branch, trackedBranch)
    }

    return Update(update, skipped)
  }

  private fun notify(project: Project,
                     results: Map<GitRepository, GitUpdateResult>,
                     skippedRepos: Map<GitRepository, @Nls String>,
                     updateNotificationData: NotificationData?) {
    val success = results.values.all(GitUpdateResult::isSuccess)
    if (success) {
      VcsNotifier.getInstance(project).notifySuccess(GitNotificationIdsHolder.BRANCH_UPDATE_FORCE_PUSHED_BRANCH_SUCCESS, "",
                                                     GitBundle.message("action.git.update.force.pushed.branch.success"))
    }
    GitUpdateSession(project, updateNotificationData, success, skippedRepos).showNotification()
  }

  private fun List<VcsCommitMetadata>.containsMergeCommits(): Boolean {
    return any { commit -> commit.parents.size > 1 }
  }

  private data class Update(val update: Map<GitRepository, GitBranchPair>, val skipped: Map<GitRepository, @Nls String>)
}
