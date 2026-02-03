// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitActivity
import git4idea.GitDisposable
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.actions.GitAbortOperationAction
import git4idea.applyChanges.GitApplyChangesConflictNotification
import git4idea.applyChanges.GitApplyChangesLocalChangesDetectedNotification
import git4idea.applyChanges.GitApplyChangesProcess.Companion.commitDetails
import git4idea.applyChanges.GitApplyChangesProcess.Companion.getCommitsDetails
import git4idea.changes.GitChangeUtils.getStagedChanges
import git4idea.cherrypick.CherryPickContinueCommand.executeCherryPickContinue
import git4idea.cherrypick.GitCherryPickProcess.Companion.getCherryPickHead
import git4idea.commands.GitCommandResult
import git4idea.config.GitVcsApplicationSettings
import git4idea.history.GitHistoryUtils
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal object GitCherryPickContinueProcess {

  internal fun launchCherryPick(repository: GitRepository): Job {
    val project = repository.project
    return GitDisposable.getInstance(project).coroutineScope.launch {
      withBackgroundProgress(project, GitBundle.message("cherry.pick.continue.progress.title")) {
        DvcsUtil.workingTreeChangeStarted(project, GitBundle.message("activity.name.cherry.pick"), GitActivity.CherryPick).use {
          val vcsNotifier = VcsNotifier.getInstance(project)
          val startHash = GitUtil.getHead(repository)
          // Hide stale error notifications from earlier failures
          vcsNotifier.hideAllNotificationsById(GitNotificationIdsHolder.CHERRY_PICK_CONTINUE_FAILED)

          val result = repository.executeCherryPickContinue()

          when (result) {
            CherryPickContinueCommand.CherryPickContinueResult.Success -> handleSuccess(vcsNotifier, getAppliedCommits(repository, startHash))
            CherryPickContinueCommand.CherryPickContinueResult.Conflict -> handleConflictsDetected(repository, vcsNotifier, repository.getCherryPickHead()!!)
            CherryPickContinueCommand.CherryPickContinueResult.EmptyCommit -> handleEmptyCommitDetected(repository, vcsNotifier, startHash)
            CherryPickContinueCommand.CherryPickContinueResult.LocalChangesOverwritten -> handleLocalChangesOverwritten(repository, vcsNotifier, repository.getCherryPickHead())
            CherryPickContinueCommand.CherryPickContinueResult.NoCherryPickInProgress -> vcsNotifier.notifyError(GitBundle.message("cherry.pick.continue.cherry.pick.not.in.progress"))
            is CherryPickContinueCommand.CherryPickContinueResult.UnknownError -> vcsNotifier.notifyError(result.commandResult.errorOutputAsHtmlString)
          }

          repository.update()
          val changeListManager = ChangeListManagerEx.getInstanceEx(project)
          RefreshVFsSynchronously.refresh(getStagedChanges(project, repository.root))
          VcsDirtyScopeManager.getInstance(project).rootDirty(repository.root)
          changeListManager.waitForUpdate()
        }
      }
    }
  }

  private val operationName = GitBundle.message("cherry.pick.name")

  private fun getAppliedCommits(repository: GitRepository, startHash: Hash?): List<VcsCommitMetadata> {
    return startHash?.let { start ->
      GitUtil.getHead(repository)?.let { endHash ->
        getMetadataBetweenHashes(repository.project, repository.root, start, endHash)
      }
    }.orEmpty()
  }

  private fun handleSuccess(vcsNotifier: VcsNotifier, appliedCommits: List<VcsCommitMetadata>) {
    vcsNotifier.notifySuccess(appliedCommits.getCommitsDetails())
  }

  private fun handleEmptyCommitDetected(repository: GitRepository, vcsNotifier: VcsNotifier, startHash: Hash?) {
    val result = GitVcsApplicationSettings.getInstance().emptyCherryPickResolutionStrategy.apply(repository)

    if (result.success()) {
      val appliedCommits = getAppliedCommits(repository, startHash)
      vcsNotifier.notifySuccess(GitVcsApplicationSettings.getInstance().emptyCherryPickResolutionStrategy.notificationMessage(appliedCommits))
    }
    else vcsNotifier.notifyError(result.errorOutputAsHtmlString)
  }

  private fun handleConflictsDetected(repository: GitRepository, vcsNotifier: VcsNotifier, commit: VcsCommitMetadata) {
    val description = commit.commitDetails() +
                      UIUtil.BR +
                      GitBundle.message("apply.changes.unresolved.conflicts.text")
    val abortCommand = GitAbortOperationAction.CherryPick()
    val notification = GitApplyChangesConflictNotification(operationName, description, commit, repository, abortCommand).apply {
      setDisplayId(GitNotificationIdsHolder.CHERRY_PICK_CONTINUE_FAILED)
    }
    vcsNotifier.notify(notification)
  }

  private fun handleLocalChangesOverwritten(repository: GitRepository, vcsNotifier: VcsNotifier, commit: VcsCommitMetadata?) {
    val notification = GitApplyChangesLocalChangesDetectedNotification(operationName, commit, emptyList(), repository, retryAction = null).apply {
      setDisplayId(GitNotificationIdsHolder.CHERRY_PICK_CONTINUE_FAILED)
    }
    vcsNotifier.notify(notification)
  }

  private fun VcsNotifier.notifyError(@NlsContexts.NotificationContent message: String) = notifyError(
    GitNotificationIdsHolder.CHERRY_PICK_CONTINUE_FAILED,
    GitBundle.message("cherry.pick.continue.failed"),
    message,
    true
  )

  private fun VcsNotifier.notifySuccess(@NlsContexts.NotificationContent message: String) = notifySuccess(
    GitNotificationIdsHolder.CHERRY_PICK_CONTINUE_SUCCESS,
    GitBundle.message("cherry.pick.continue.successful"),
    message
  )

  internal fun GitCommandResult.isEmptyCommit(): Boolean {
    val stdout = outputAsJoinedString
    val stderr = errorOutputAsJoinedString
    return stdout.contains("nothing to commit") ||
           stdout.contains("nothing added to commit but untracked files present") ||
           stderr.contains("previous cherry-pick is now empty")
  }

  private fun getMetadataBetweenHashes(
    project: Project,
    root: VirtualFile,
    fromExclusiveHash: Hash,
    toInclusiveHash: Hash,
  ): List<VcsCommitMetadata> {
    val rootObject = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(root) ?: return emptyList()
    val provider: VcsLogProvider = VcsLogManager
                                     .findLogProviders(listOf(rootObject), project)[root]
                                   ?: return emptyList()


    val range: VcsLogRangeFilter = VcsLogFilterObject.fromRange(fromExclusiveHash.asString(), toInclusiveHash.asString())

    val commitsInRange = provider.getCommitsMatchingFilter(
      root,
      VcsLogFilterObject.collection(range),
      PermanentGraph.Options.Default,
      -1
    )

    val hashes = commitsInRange.map { it.id.asString() }
    return GitHistoryUtils.collectCommitsMetadata(project, root, *hashes.toTypedArray()) ?: emptyList()
  }
}