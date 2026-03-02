// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.applyChanges

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.history.ActivityId
import com.intellij.history.LocalHistory
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerEx
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.util.VcsUserUtil
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.GitUtil.refreshChangedVfs
import git4idea.actions.GitAbortOperationAction
import git4idea.changes.GitChangeUtils.getStagedChanges
import git4idea.cherrypick.GitLocalChangesConflictDetector
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.commands.GitSimpleEventDetector
import git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector
import git4idea.i18n.GitBundle
import git4idea.index.isStagingAreaAvailable
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitChangesSaver
import git4idea.util.GitUntrackedFilesHelper
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Applies the given Git operation (e.g. cherry-pick or revert) to the current working tree,
 * waits for the [ChangeListManager] update, shows the commit dialog and removes the changelist after commit,
 * if the commit was successful.
 */
internal abstract class GitApplyChangesProcess(
  protected val project: Project,
  private val commits: List<VcsCommitMetadata>,
  private val operationName: @Nls String,
  private val appliedWord: @Nls String,
  private val abortCommand: GitAbortOperationAction,
  private val preserveCommitMetadata: Boolean,
  private val activityName: @NlsContexts.Label String,
  private val activityId: ActivityId,
) {
  private val repositoryManager = GitRepositoryManager.getInstance(project)
  private val vcsNotifier = VcsNotifier.getInstance(project)
  private val changeListManager = ChangeListManagerEx.getInstanceEx(project)
  private val notificationsHandler = project.service<GitApplyChangesNotificationsHandler>()

  fun execute() {
    notificationsHandler.beforeApply()
    execute(null, commits)
  }

  private fun execute(changesSaver: GitChangesSaver?, commits: List<VcsCommitMetadata>) {
    // ensure there are no stall changes (ex: from recent commit) that prevent changes from being moved into temp changelist
    if (changeListManager.areChangeListsEnabled()) {
      changeListManager.waitForUpdate()
    }

    val commitsInRoots = DvcsUtil.groupCommitsByRoots(repositoryManager, commits)
    LOG.info("${operationName}ing commits: " + toString(commitsInRoots))

    if (changesSaver != null) {
      if (!changesSaver.trySaveLocalChanges(commitsInRoots.map { (repo, _) -> repo.root })) {
        return
      }
    }

    val successfulCommits = mutableSetOf<VcsCommitMetadata>()
    val skippedCommits = mutableSetOf<VcsCommitMetadata>()

    for ((repository, repoCommits) in commitsInRoots) {
      try {
        if (!executeForRepository(repository, repoCommits, successfulCommits, skippedCommits)) {
          notificationsHandler.operationFailed(operationName, repository, changesSaver)
          return
        }
      }
      finally {
        repository.update()
      }
    }

    notifyResult(successfulCommits, skippedCommits)
    if (changesSaver != null) {
      LOG.info("Restoring saved changes after successful $operationName")
      changesSaver.load()
    }
  }

  /**
   * Function that defines how the commits for a repository should be processed.
   * e.g., Can be used to process everything at once or chunk one by one.
   * Defaults to one by one.
   * @return true to continue with other repositories, false to break execution
   */
  protected open fun executeForRepository(
    repository: GitRepository,
    repoCommits: List<VcsCommitMetadata>,
    successfulCommits: MutableSet<VcsCommitMetadata>,
    alreadyPicked: MutableSet<VcsCommitMetadata>,
  ) = repoCommits.all { commit -> executeForCommitChunk(repository, listOf(commit), successfulCommits, alreadyPicked) }

  /**
   * @param commits commits to be applied, as defined by the [executeForRepository] function
   * @return true to continue with other commits, false to break execution
   */
  protected open fun executeForCommitChunk(
    repository: GitRepository,
    commits: List<VcsCommitMetadata>,
    successfulCommits: MutableSet<VcsCommitMetadata>,
    alreadyPicked: MutableSet<VcsCommitMetadata>,
  ): Boolean {
    val conflictDetector = GitSimpleEventDetector(CHERRY_PICK_CONFLICT)
    val localChangesOverwrittenDetector = GitLocalChangesConflictDetector()
    val untrackedFilesDetector = GitUntrackedFilesOverwrittenByOperationDetector(repository.root)

    val base = commits.first()
    val commitMessage = generateDefaultMessage(repository, base)

    val strategy: GitApplyChangesCommitStrategy = when {
      isStagingAreaAvailable(project) -> StagingAreaGitApplyChangesCommit(repository.project, commitMessage)

      changeListManager.areChangeListsEnabled() && VcsApplicationSettings.getInstance().CREATE_CHANGELISTS_AUTOMATICALLY ->
        ChangeListGitApplyChangesCommit(repository, this, base, commitMessage, preserveCommitMetadata)

      else -> SimplifiedGitApplyChangesCommit(repository, this, base, commitMessage, preserveCommitMetadata)
    }

    val action = LocalHistory.getInstance().startAction(activityName, activityId)
    strategy.start()
    try {
      val startHash = GitUtil.getHead(repository)

      val result = applyChanges(repository, commits, listOf(conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector))

      if (result.success()) {
        refreshChangedVfs(repository, startHash)
        addSuccessfulCommits(successfulCommits, commits)
        return true
      }
      else {
        // Applying the whole sequence failed, need to find where we stopped
        // In this case we should find how many were applied and mark them as successful
        val stoppedAtCommit = findStoppedCommitInSequence(repository, commits)
        val toAdd = commits.subList(0, commits.indexOf(stoppedAtCommit))
        addSuccessfulCommits(successfulCommits, toAdd)
        when {
          conflictDetector.isDetected -> {
            val mergeCompleted = GitApplyChangesConflictResolver(project, repository.root, stoppedAtCommit.id.toShortString(),
                                                                 VcsUserUtil.getShortPresentation(stoppedAtCommit.author), stoppedAtCommit.subject,
                                                                 operationName).merge()

            refreshStagedVfs(repository.root) // `ConflictResolver` only refreshes conflicted files
            VcsDirtyScopeManager.getInstance(project).rootDirty(repository.root)
            changeListManager.waitForUpdate()
            strategy.afterChangesRefreshed()

            if (mergeCompleted) {
              LOG.debug("All conflicts resolved, will show commit dialog.")
              return strategy.doUserCommit(onSuccessfulCommit = {
                addSuccessfulCommits(successfulCommits, listOf(it))
              }, onSkippedCommit = alreadyPicked::add, onCancelledCommit = {
                // don't notify about canceled commit. Notify just in the case when there were already successful commits in the queue.
                if (successfulCommits.isNotEmpty()) {
                  notifyCommitCancelled(it, successfulCommits, operationName)
                }
              })
            }
            else {
              notifyConflictWarning(repository, stoppedAtCommit, successfulCommits)
              return false
            }
          }
          untrackedFilesDetector.isDetected -> {
            val description = getSuccessfulCommitDetailsIfAny(successfulCommits)

            GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, repository.root,
                                                                      untrackedFilesDetector.relativeFilePaths, operationName, description)
            return false
          }
          localChangesOverwrittenDetector.isDetected -> {
            handleLocalChangesDetected(repository, stoppedAtCommit.takeIf { localChangesOverwrittenDetector.byMerge }, successfulCommits, alreadyPicked)
            return false
          }
          isEmptyCommit(result) -> {
            alreadyPicked.add(stoppedAtCommit)
            return true
          }
          else -> {
            notifyError(result.errorOutputAsHtmlString, commits, successfulCommits)
            return false
          }
        }
      }
    }
    finally {
      strategy.finish()
      action.finish()
    }
  }

  private fun handleLocalChangesDetected(
    repository: GitRepository,
    failedOnCommit: VcsCommitMetadata?,
    successfulCommits: Set<VcsCommitMetadata>,
    alreadyPicked: Set<VcsCommitMetadata>,
  ) {
    val notification = GitApplyChangesLocalChangesDetectedNotification(operationName, failedOnCommit, successfulCommits.toList(), repository) { saver ->
      val alreadyPickedSet = alreadyPicked + successfulCommits
      LOG.info("Re-trying $operationName, skipping ${alreadyPickedSet.size} already processed commits")
      execute(saver, commits.filter { commit -> !alreadyPickedSet.contains(commit) })
    }
    vcsNotifier.notify(notification)
  }

  private fun addSuccessfulCommits(successfulCommits: MutableCollection<VcsCommitMetadata>, toAdd: List<VcsCommitMetadata>) {
    successfulCommits.addAll(toAdd)
    onSuccessfulCommitsAdded(toAdd)
  }

  protected abstract fun isEmptyCommit(result: GitCommandResult): Boolean

  abstract fun cleanupBeforeCommit(repository: GitRepository)

  protected abstract fun generateDefaultMessage(repository: GitRepository, commit: VcsCommitMetadata): @NonNls String

  protected open fun onSuccessfulCommitsAdded(commits: Collection<VcsCommitMetadata>) = Unit

  protected abstract fun findStoppedCommitInSequence(
    repository: GitRepository,
    commits: List<VcsCommitMetadata>
  ): VcsCommitMetadata

  protected abstract fun applyChanges(
    repository: GitRepository,
    commits: Collection<VcsCommitMetadata>,
    listeners: List<GitLineHandlerListener>,
  ): GitCommandResult

  private fun refreshStagedVfs(root: VirtualFile) {
    val staged = getStagedChanges(project, root)
    RefreshVFsSynchronously.refresh(staged)
  }

  private fun notifyResult(successfulCommits: Collection<VcsCommitMetadata>, skipped: Collection<VcsCommitMetadata>) = when {
    skipped.isEmpty() -> {
      vcsNotifier.notifySuccess(GitNotificationIdsHolder.APPLY_CHANGES_SUCCESS, GitBundle.message("apply.changes.operation.successful", operationName.capitalize()), successfulCommits.getCommitsDetails())
    }
    successfulCommits.isNotEmpty() -> {
      val title = GitBundle.message("apply.changes.applied.for.commits", appliedWord.capitalize(), successfulCommits.size, successfulCommits.size + skipped.size)
      val description = successfulCommits.getCommitsDetails() + UIUtil.HR + formSkippedDescription(skipped, true)
      vcsNotifier.notifySuccess(GitNotificationIdsHolder.APPLY_CHANGES_SUCCESS, title, description)
    }
    else -> {
      vcsNotifier.notify(GitApplyChangesNothingToDoNotification(operationName, formSkippedDescription(skipped, false)))
    }
  }

  private fun notifyConflictWarning(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    successfulCommits: Collection<VcsCommitMetadata>,
  ) {
    val description = commit.commitDetails() +
                      UIUtil.BR +
                      GitBundle.message("apply.changes.unresolved.conflicts.text") +
                      getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notify(GitApplyChangesConflictNotification(operationName, description, commit, repository, abortCommand))
  }

  private fun notifyError(
    @Nls content: String,
    failedCommit: Collection<VcsCommitMetadata>,
    successfulCommits: Collection<VcsCommitMetadata>,
  ) {
    val description = failedCommit.joinToString { it.commitDetails() } + UIUtil.BR + content + getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notifyError(GitNotificationIdsHolder.APPLY_CHANGES_ERROR, GitBundle.message("apply.changes.operation.failed", operationName.capitalize()), description)
  }

  private fun notifyCommitCancelled(
    commit: VcsCommitMetadata,
    successfulCommits: Collection<VcsCommitMetadata>,
    @Nls operationName: String,
  ) {
    val description = commit.commitDetails() + getSuccessfulCommitDetailsIfAny(successfulCommits, operationName = operationName)
    vcsNotifier.notifyMinorWarning(GitNotificationIdsHolder.COMMIT_CANCELLED, GitBundle.message("apply.changes.operation.canceled", operationName.capitalize()), description)
  }

  @Nls
  private fun getSuccessfulCommitDetailsIfAny(successfulCommits: Collection<VcsCommitMetadata>) =
    getSuccessfulCommitDetailsIfAny(successfulCommits, operationName)

  @Nls
  private fun formSkippedDescription(skipped: Collection<VcsCommitMetadata>, but: Boolean): String {
    val hashes = skipped.joinToString { it.id.toShortString() }
    return if (but)
      GitBundle.message("apply.changes.skipped", hashes, skipped.size, appliedWord)
    else GitBundle.message("apply.changes.everything.applied", hashes, appliedWord)
  }

  private fun toString(commitsInRoots: Map<GitRepository, List<VcsCommitMetadata>>) = commitsInRoots.entries.joinToString("; ") { entry ->
    val commits = entry.value.joinToString { it.id.asString() }
    getShortRepositoryName(entry.key) + ": [" + commits + "]"
  }

  internal companion object {
    private val LOG = logger<GitApplyChangesProcess>()

    @NlsSafe
    internal fun VcsCommitMetadata.commitDetails() = id.toShortString() + " " + StringUtil.escapeXmlEntities(subject)

    @NlsSafe
    internal fun getSuccessfulCommitDetailsIfAny(successfulCommits: Collection<VcsCommitMetadata>, operationName: String) =
      if (successfulCommits.isEmpty()) ""
      else
        buildString {
          append(UIUtil.HR)
          append(GitBundle.message("apply.changes.operation.successful.for.commits", operationName, successfulCommits.size))
          append(UIUtil.BR)
          append(successfulCommits.getCommitsDetails())
        }

    @NlsSafe
    internal fun Collection<VcsCommitMetadata>.getCommitsDetails() = joinToString(separator = UIUtil.BR) { it.commitDetails() }
  }
}
