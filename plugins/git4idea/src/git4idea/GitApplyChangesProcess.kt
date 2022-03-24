// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.xml.util.XmlStringUtil.wrapInHtml
import com.intellij.xml.util.XmlStringUtil.wrapInHtmlTag
import git4idea.GitUtil.refreshChangedVfs
import git4idea.actions.GitAbortOperationAction
import git4idea.changes.GitChangeUtils.getStagedChanges
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.commands.GitSimpleEventDetector
import git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT
import git4idea.commands.GitSimpleEventDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector
import git4idea.i18n.GitBundle
import git4idea.index.isStagingAreaAvailable
import git4idea.index.showStagingArea
import git4idea.merge.GitConflictResolver
import git4idea.merge.GitDefaultMergeDialogCustomizer
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitUntrackedFilesHelper
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Applies the given Git operation (e.g. cherry-pick or revert) to the current working tree,
 * waits for the [ChangeListManager] update, shows the commit dialog and removes the changelist after commit,
 * if the commit was successful.
 */
internal class GitApplyChangesProcess(private val project: Project,
                                      private val commits: List<VcsFullCommitDetails>,
                                      forceAutoCommit: Boolean,
                                      @Nls private val operationName: String,
                                      @Nls private val appliedWord: String,
                                      private val command: (GitRepository, VcsCommitMetadata, autoCommit: Boolean, List<GitLineHandlerListener>) -> GitCommandResult,
                                      private val abortCommand: GitAbortOperationAction,
                                      private val emptyCommitDetector: (GitCommandResult) -> Boolean,
                                      private val defaultCommitMessageGenerator: (GitRepository, VcsFullCommitDetails) -> @NonNls String,
                                      private val preserveCommitMetadata: Boolean,
                                      private val cleanupBeforeCommit: (GitRepository, autoCommit: Boolean) -> Unit = { _, _ -> }) {
  private val repositoryManager = GitRepositoryManager.getInstance(project)
  private val vcsNotifier = VcsNotifier.getInstance(project)
  private val changeListManager = ChangeListManagerEx.getInstanceEx(project)
  private val vcsHelper = AbstractVcsHelper.getInstance(project)
  private val autoCommit = forceAutoCommit || !changeListManager.areChangeListsEnabled()

  fun execute() {
    // ensure there are no stall changes (ex: from recent commit) that prevent changes from being moved into temp changelist
    if (changeListManager.areChangeListsEnabled()) {
      val semaphore = CountDownLatch(1)
      changeListManager.invokeAfterUpdate(false) {
        semaphore.countDown()
      }
      ProgressIndicatorUtils.awaitWithCheckCanceled(semaphore)
    }

    val commitsInRoots = DvcsUtil.groupCommitsByRoots(repositoryManager, commits)
    LOG.info("${operationName}ing commits: " + toString(commitsInRoots))

    val successfulCommits = mutableListOf<VcsFullCommitDetails>()
    val skippedCommits = mutableListOf<VcsFullCommitDetails>()

    repoLoop@ for ((repository, repoCommits) in commitsInRoots) {
      try {
        for (commit in repoCommits) {
          val success = executeForCommit(repository, commit, successfulCommits, skippedCommits)
          if (!success) return
        }
      }
      finally {
        repository.update()
      }
    }
    notifyResult(successfulCommits, skippedCommits)
  }

  /**
   * @return true to continue with other commits, false to break execution
   */
  private fun executeForCommit(repository: GitRepository,
                               commit: VcsFullCommitDetails,
                               successfulCommits: MutableList<VcsFullCommitDetails>,
                               alreadyPicked: MutableList<VcsFullCommitDetails>): Boolean {
    val conflictDetector = GitSimpleEventDetector(CHERRY_PICK_CONFLICT)
    val localChangesOverwrittenDetector = GitSimpleEventDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK)
    val untrackedFilesDetector = GitUntrackedFilesOverwrittenByOperationDetector(repository.root)

    val commitMessage = defaultCommitMessageGenerator(repository, commit)

    val strategy: CommitStrategy = when {
      isStagingAreaAvailable(project) -> {
        StagingAreaCommit(repository, commit, commitMessage)
      }
      changeListManager.areChangeListsEnabled() &&
      VcsApplicationSettings.getInstance().CREATE_CHANGELISTS_AUTOMATICALLY -> {
        ChangeListCommit(repository, commit, commitMessage)
      }
      else -> {
        SimplifiedCommit(repository, commit, commitMessage)
      }
    }

    strategy.start()
    try {
      val startHash = GitUtil.getHead(repository)

      val result = command(repository, commit, autoCommit,
                           listOf(conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector))

      if (result.success()) {
        if (autoCommit) {
          refreshChangedVfs(repository, startHash)
          successfulCommits.add(commit)
          return true
        }
        else {
          refreshStagedVfs(repository.root)
          VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(repository.root)
          changeListManager.waitForUpdate()
          strategy.afterChangesRefreshed()

          return strategy.doUserCommit(successfulCommits, alreadyPicked)
        }
      }
      else if (conflictDetector.hasHappened()) {
        val mergeCompleted = ConflictResolver(project, repository.root, commit.id.toShortString(),
                                              VcsUserUtil.getShortPresentation(commit.author), commit.subject,
                                              operationName).merge()

        refreshStagedVfs(repository.root) // `ConflictResolver` only refreshes conflicted files
        VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(repository.root)
        changeListManager.waitForUpdate()
        strategy.afterChangesRefreshed()

        if (mergeCompleted) {
          LOG.debug("All conflicts resolved, will show commit dialog.")
          return strategy.doUserCommit(successfulCommits, alreadyPicked)
        }
        else {
          notifyConflictWarning(repository, commit, successfulCommits)
          return false
        }
      }
      else if (untrackedFilesDetector.wasMessageDetected()) {
        val description = getSuccessfulCommitDetailsIfAny(successfulCommits)

        GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, repository.root,
                                                                  untrackedFilesDetector.relativeFilePaths, operationName, description)
        return false
      }
      else if (localChangesOverwrittenDetector.hasHappened()) {
        notifyError(GitBundle.message("apply.changes.would.be.overwritten", operationName), commit, successfulCommits)
        return false
      }
      else if (emptyCommitDetector(result)) {
        alreadyPicked.add(commit)
        return true
      }
      else {
        notifyError(result.errorOutputAsHtmlString, commit, successfulCommits)
        return false
      }
    }
    finally {
      strategy.finish()
    }
  }

  private abstract class CommitStrategy {
    open fun start() = Unit
    open fun finish() = Unit
    abstract fun doUserCommit(successfulCommits: MutableList<VcsFullCommitDetails>,
                              alreadyPicked: MutableList<VcsFullCommitDetails>): Boolean

    open fun afterChangesRefreshed() = Unit
  }

  private inner class ChangeListCommit(val repository: GitRepository,
                                       val commit: VcsFullCommitDetails,
                                       val commitMessage: String) : CommitStrategy() {
    lateinit var changeList: LocalChangeList
    lateinit var previousDefaultChangelist: LocalChangeList

    override fun start() {
      previousDefaultChangelist = changeListManager.defaultChangeList

      val changeListName = createNameForChangeList(project, commitMessage)
      val changeListData = if (preserveCommitMetadata) createChangeListData(commit) else null
      changeList = changeListManager.addChangeList(changeListName, commitMessage, changeListData)
      changeListManager.setDefaultChangeList(changeList, true)
    }

    override fun finish() {
      changeListManager.setDefaultChangeList(previousDefaultChangelist, true)
      changeListManager.scheduleAutomaticEmptyChangeListDeletion(changeList, true)
    }

    override fun doUserCommit(successfulCommits: MutableList<VcsFullCommitDetails>,
                              alreadyPicked: MutableList<VcsFullCommitDetails>): Boolean {
      return commitChangelist(repository, commit, commitMessage, changeList, successfulCommits, alreadyPicked)
    }
  }

  private inner class SimplifiedCommit(val repository: GitRepository,
                                       val commit: VcsFullCommitDetails,
                                       val commitMessage: String) : CommitStrategy() {
    override fun doUserCommit(successfulCommits: MutableList<VcsFullCommitDetails>,
                              alreadyPicked: MutableList<VcsFullCommitDetails>): Boolean {
      val list = changeListManager.defaultChangeList
      return commitChangelist(repository, commit, commitMessage, list, successfulCommits, alreadyPicked)
    }

    override fun afterChangesRefreshed() {
      val list = changeListManager.defaultChangeList
      if (preserveCommitMetadata &&
          changeListManager.areChangeListsEnabled() &&
          list.changes.isNotEmpty()) {
        changeListManager.editChangeListData(list.name, createChangeListData(commit))
      }
    }
  }

  private class StagingAreaCommit(val repository: GitRepository,
                                  val commit: VcsFullCommitDetails,
                                  val commitMessage: String) : CommitStrategy() {
    override fun doUserCommit(successfulCommits: MutableList<VcsFullCommitDetails>,
                              alreadyPicked: MutableList<VcsFullCommitDetails>): Boolean {
      runInEdt {
        showStagingArea(repository.project, commitMessage)
      }
      return false
    }
  }

  private fun commitChangelist(repository: GitRepository,
                               commit: VcsFullCommitDetails,
                               commitMessage: String,
                               changeList: LocalChangeList,
                               successfulCommits: MutableList<VcsFullCommitDetails>,
                               alreadyPicked: MutableList<VcsFullCommitDetails>): Boolean {
    val actualList = changeListManager.getChangeList(changeList.id)
    if (actualList == null) {
      LOG.error("Couldn't find the changelist with id ${changeList.id} and name ${changeList.name} among " +
                changeListManager.changeLists.joinToString { "${it.id} ${it.name}" })
      return false
    }
    val changes = actualList.changes
    if (changes.isEmpty()) {
      LOG.debug("No changes in the $actualList. All changes in the CLM: ${getAllChangesInLogFriendlyPresentation(changeListManager)}")
      alreadyPicked.add(commit)
      return true
    }

    LOG.debug("Showing commit dialog for changes: ${changes}")
    val committed = showCommitDialogAndWaitForCommit(repository, changeList, commitMessage, changes)
    if (committed) {
      markDirty(changes)
      changeListManager.waitForUpdate()

      successfulCommits.add(commit)
      return true
    }
    else {
      notifyCommitCancelled(commit, successfulCommits)
      return false
    }
  }

  private fun getAllChangesInLogFriendlyPresentation(changeListManager: ChangeListManager) =
    changeListManager.changeLists.map { "[${it.name}] ${it.changes}" }

  private fun refreshStagedVfs(root: VirtualFile) {
    val staged = getStagedChanges(project, root)
    RefreshVFsSynchronously.refresh(staged)
  }

  private fun markDirty(changes: Collection<Change>) {
    VcsDirtyScopeManager.getInstance(project).filePathsDirty(ChangesUtil.getPaths(changes), null)
  }

  private fun showCommitDialogAndWaitForCommit(repository: GitRepository,
                                               changeList: LocalChangeList,
                                               commitMessage: String,
                                               changes: Collection<Change>): Boolean {
    val commitSucceeded = AtomicBoolean()
    val sem = Semaphore(0)
    ApplicationManager.getApplication().invokeAndWait({
      try {
        cleanupBeforeCommit(repository, autoCommit)
        val commitNotCancelled = vcsHelper.commitChanges(changes, changeList, commitMessage,
          object : CommitResultHandler {
            override fun onSuccess(commitMessage1: String) {
              commitSucceeded.set(true)
              sem.release()
            }

            override fun onCancel() {
              commitSucceeded.set(false)
              sem.release()
            }

            override fun onFailure(errors: List<VcsException>) {
              commitSucceeded.set(false)
              sem.release()
            }
          })

        if (!commitNotCancelled) {
          commitSucceeded.set(false)
          sem.release()
        }
      }
      catch (t: Throwable) {
        LOG.error(t)
        commitSucceeded.set(false)
        sem.release()
      }
    }, ModalityState.NON_MODAL)

    // need additional waiting, because commitChanges is asynchronous
    try {
      sem.acquire()
    }
    catch (e: InterruptedException) {
      LOG.error(e)
      return false
    }

    return commitSucceeded.get()
  }

  private fun createChangeListData(commit: VcsFullCommitDetails) = ChangeListData(commit.author, Date(commit.authorTime))

  private fun notifyResult(successfulCommits: List<VcsFullCommitDetails>, skipped: List<VcsFullCommitDetails>) {
    when {
      skipped.isEmpty() -> {
        vcsNotifier.notifySuccess(null,
                                  GitBundle.message("apply.changes.operation.successful", operationName.capitalize()),
                                  getCommitsDetails(successfulCommits))
      }
      successfulCommits.isNotEmpty() -> {
        val title = GitBundle.message("apply.changes.applied.for.commits", appliedWord.capitalize(), successfulCommits.size,
                                      successfulCommits.size + skipped.size)
        val description = getCommitsDetails(successfulCommits) + UIUtil.HR + formSkippedDescription(skipped, true)
        vcsNotifier.notifySuccess(null, title, description)
      }
      else -> {
        vcsNotifier.notifyImportantWarning(null, GitBundle.message("apply.changes.nothing.to.do", operationName),
                                           formSkippedDescription(skipped, false))
      }
    }
  }

  private fun notifyConflictWarning(repository: GitRepository,
                                    commit: VcsFullCommitDetails,
                                    successfulCommits: List<VcsFullCommitDetails>) {
    val title = GitBundle.message("apply.changes.operation.performed.with.conflicts", operationName.capitalize())

    var description = commitDetails(commit)
    description += UIUtil.BR + GitBundle.message("apply.changes.unresolved.conflicts.text")
    description += getSuccessfulCommitDetailsIfAny(successfulCommits)

    val notification = VcsNotifier.IMPORTANT_ERROR_NOTIFICATION
      .createNotification(title, description, NotificationType.WARNING)
      .addAction(NotificationAction.createSimple(GitBundle.message("apply.changes.unresolved.conflicts.notification.resolve.action.text")) {
        val hash = commit.id.toShortString()
        val commitAuthor = VcsUserUtil.getShortPresentation(commit.author)
        val commitMessage = commit.subject
        ConflictResolver(project, repository.root, hash, commitAuthor, commitMessage, operationName).mergeNoProceedInBackground()
      })
      .addAction(NotificationAction.create(GitBundle.message("apply.changes.unresolved.conflicts.notification.abort.action.text",
                                                             operationName.capitalize())) { _, notification ->
        if (abortCommand.performInBackground(repository)) {
          notification.expire()
        }
      })
    VcsNotifier.getInstance(project).notify(notification)
  }

  private fun notifyCommitCancelled(commit: VcsFullCommitDetails, successfulCommits: List<VcsFullCommitDetails>) {
    if (successfulCommits.isEmpty()) {
      // don't notify about cancelled commit. Notify just in the case when there were already successful commits in the queue.
      return
    }
    var description = commitDetails(commit)
    description += getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notifyMinorWarning(GitNotificationIdsHolder.COMMIT_CANCELLED, GitBundle.message("apply.changes.operation.canceled", operationName.capitalize()), description)
  }

  private fun notifyError(@Nls content: String,
                          failedCommit: VcsFullCommitDetails,
                          successfulCommits: List<VcsFullCommitDetails>) {
    var description = commitDetails(failedCommit) + UIUtil.BR + content
    description += getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notifyError(null, GitBundle.message("apply.changes.operation.failed", operationName.capitalize()), description)
  }

  @Nls
  private fun getSuccessfulCommitDetailsIfAny(successfulCommits: List<VcsFullCommitDetails>): String {
    var description = ""
    if (successfulCommits.isNotEmpty()) {
      description += UIUtil.HR +
                     GitBundle.message("apply.changes.operation.successful.for.commits", operationName, successfulCommits.size) +
                     UIUtil.BR
      description += getCommitsDetails(successfulCommits)
    }
    return description
  }

  @Nls
  private fun formSkippedDescription(skipped: List<VcsFullCommitDetails>, but: Boolean): String {
    val hashes = StringUtil.join(skipped, { commit -> commit.id.toShortString() }, ", ")
    if (but) {
      return GitBundle.message("apply.changes.skipped", hashes, skipped.size, appliedWord)
    }
    return GitBundle.message("apply.changes.everything.applied", hashes, appliedWord)
  }

  @NlsSafe
  private fun getCommitsDetails(successfulCommits: List<VcsFullCommitDetails>): String {
    var description = ""
    for (commit in successfulCommits) {
      if (description.isNotEmpty()) description += UIUtil.BR
      description += commitDetails(commit)
    }
    return description
  }

  @NlsSafe
  private fun commitDetails(commit: VcsFullCommitDetails): String {
    return commit.id.toShortString() + " " + StringUtil.escapeXmlEntities(commit.subject)
  }

  private fun toString(commitsInRoots: Map<GitRepository, List<VcsFullCommitDetails>>): String {
    return commitsInRoots.entries.joinToString("; ") { entry ->
      val commits = entry.value.joinToString { it.id.asString() }
      getShortRepositoryName(entry.key) + ": [" + commits + "]"
    }
  }

  class ConflictResolver(project: Project,
                         root: VirtualFile,
                         commitHash: String,
                         commitAuthor: String,
                         commitMessage: String,
                         @Nls operationName: String
  ) : GitConflictResolver(project, setOf(root),
                          makeParams(project, commitHash, commitAuthor, commitMessage, operationName)) {
    override fun notifyUnresolvedRemain() {/* we show a [possibly] compound notification after applying all commits.*/
    }
  }

  companion object {
    private val LOG = logger<GitApplyChangesProcess>()
  }
}

private fun makeParams(project: Project,
                       commitHash: String,
                       commitAuthor: String,
                       commitMessage: String,
                       @Nls operationName: String): GitConflictResolver.Params {

  val params = GitConflictResolver.Params(project)
  params.setErrorNotificationTitle(GitBundle.message("apply.changes.operation.performed.with.conflicts", operationName.capitalize()))
  params.setMergeDialogCustomizer(MergeDialogCustomizer(project, commitHash, commitAuthor, commitMessage, operationName))
  return params
}

private class MergeDialogCustomizer(
  project: Project,
  @NlsSafe private val commitHash: String,
  private val commitAuthor: String,
  @NlsSafe private val commitMessage: String,
  @Nls private val operationName: String
) : GitDefaultMergeDialogCustomizer(project) {

  override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>) = wrapInHtml(
    GitBundle.message(
      "apply.conflict.dialog.description.label.text",
      operationName,
      wrapInHtmlTag(commitHash, "code"),
      commitAuthor,
      UIUtil.BR + wrapInHtmlTag(commitMessage, "code")
    )
  )
}
