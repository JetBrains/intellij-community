// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.history.ActivityId
import com.intellij.history.LocalHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.xml.util.XmlStringUtil.wrapInHtml
import com.intellij.xml.util.XmlStringUtil.wrapInHtmlTag
import git4idea.GitUtil.refreshChangedVfs
import git4idea.actions.GitAbortOperationAction
import git4idea.applyChanges.GitApplyChangesLocalChangesDetectedNotification
import git4idea.applyChanges.GitApplyChangesNotificationsHandler
import git4idea.changes.GitChangeUtils.getStagedChanges
import git4idea.cherrypick.GitLocalChangesConflictDetector
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.commands.GitSimpleEventDetector
import git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector
import git4idea.i18n.GitBundle
import git4idea.index.isStagingAreaAvailable
import git4idea.index.showStagingArea
import git4idea.merge.GitConflictResolver
import git4idea.merge.GitDefaultMergeDialogCustomizer
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.stash.GitChangesSaver
import git4idea.util.GitUntrackedFilesHelper
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Applies the given Git operation (e.g. cherry-pick or revert) to the current working tree,
 * waits for the [ChangeListManager] update, shows the commit dialog and removes the changelist after commit,
 * if the commit was successful.
 */
internal abstract class GitApplyChangesProcess(
  protected val project: Project,
  private val commits: List<VcsCommitMetadata>,
  @Nls private val operationName: String,
  @Nls private val appliedWord: String,
  private val abortCommand: GitAbortOperationAction,
  private val preserveCommitMetadata: Boolean,
  private val activityName: @NlsContexts.Label String,
  private val activityId: ActivityId,
) {
  private val repositoryManager = GitRepositoryManager.getInstance(project)
  private val vcsNotifier = VcsNotifier.getInstance(project)
  private val changeListManager = ChangeListManagerEx.getInstanceEx(project)
  private val vcsHelper = AbstractVcsHelper.getInstance(project)
  private val notificationsHandler = project.service<GitApplyChangesNotificationsHandler>()

  protected abstract fun isEmptyCommit(result: GitCommandResult): Boolean

  protected abstract fun cleanupBeforeCommit(repository: GitRepository)

  protected abstract fun generateDefaultMessage(repository: GitRepository, commit: VcsCommitMetadata): @NonNls String

  protected abstract fun applyChanges(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    listeners: List<GitLineHandlerListener>,
  ): GitCommandResult

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
      if (!trySaveChanges(commitsInRoots.map { (repo, _) -> repo.root }, changesSaver)) {
        return
      }
    }

    val successfulCommits = mutableListOf<VcsCommitMetadata>()
    val skippedCommits = mutableListOf<VcsCommitMetadata>()

    for ((repository, repoCommits) in commitsInRoots) {
      try {
        for (commit in repoCommits) {
          if (!executeForCommit(repository, commit, successfulCommits, skippedCommits)) {
            notificationsHandler.operationFailed(operationName, repository, changesSaver)
            return
          }
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

  fun trySaveChanges(roots: List<VirtualFile>, changesSaver: GitChangesSaver): Boolean {
    val errorMessage = changesSaver.saveLocalChangesOrError(roots) ?: return true

    VcsNotifier.getInstance(project)
      .notifyError(VcsNotificationIdsHolder.UNCOMMITTED_CHANGES_SAVING_ERROR,
                   VcsBundle.message("notification.title.couldn.t.save.uncommitted.changes"),
                   errorMessage)
    return false
  }

  /**
   * @return true to continue with other commits, false to break execution
   */
  protected open fun executeForCommit(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    successfulCommits: MutableList<VcsCommitMetadata>,
    alreadyPicked: MutableList<VcsCommitMetadata>,
  ): Boolean {
    val conflictDetector = GitSimpleEventDetector(CHERRY_PICK_CONFLICT)
    val localChangesOverwrittenDetector = GitLocalChangesConflictDetector()
    val untrackedFilesDetector = GitUntrackedFilesOverwrittenByOperationDetector(repository.root)

    val commitMessage = generateDefaultMessage(repository, commit)

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

    val action = LocalHistory.getInstance().startAction(activityName, activityId)
    strategy.start()
    try {
      val startHash = GitUtil.getHead(repository)

      val result = applyChanges(repository, commit, listOf(conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector))

      if (result.success()) {
        refreshChangedVfs(repository, startHash)
        successfulCommits.add(commit)
        return true
      }
      else if (conflictDetector.isDetected) {
        val mergeCompleted = ConflictResolver(project, repository.root, commit.id.toShortString(),
                                              VcsUserUtil.getShortPresentation(commit.author), commit.subject,
                                              operationName).merge()

        refreshStagedVfs(repository.root) // `ConflictResolver` only refreshes conflicted files
        VcsDirtyScopeManager.getInstance(project).rootDirty(repository.root)
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
      else if (untrackedFilesDetector.isDetected) {
        val description = getSuccessfulCommitDetailsIfAny(successfulCommits)

        GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, repository.root,
                                                                  untrackedFilesDetector.relativeFilePaths, operationName, description)
        return false
      }
      else if (localChangesOverwrittenDetector.isDetected) {
        handleLocalChangesDetected(repository, commit.takeIf { localChangesOverwrittenDetector.byMerge }, successfulCommits, alreadyPicked)
        return false
      }
      else if (isEmptyCommit(result)) {
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
      action.finish()
    }
  }

  private fun handleLocalChangesDetected(
    repository: GitRepository,
    failedOnCommit: VcsCommitMetadata?,
    successfulCommits: MutableList<VcsCommitMetadata>,
    alreadyPicked: MutableList<VcsCommitMetadata>,
  ) {
    val notification = GitApplyChangesLocalChangesDetectedNotification(operationName, failedOnCommit, successfulCommits, repository) { saver ->
      val alreadyPickedSet = buildSet {
        addAll(alreadyPicked)
        addAll(successfulCommits)
      }
      LOG.info("Re-trying $operationName, skipping ${alreadyPickedSet.size} already processed commits")
      execute(saver, commits.filter { commit -> !alreadyPickedSet.contains(commit) })
    }
    vcsNotifier.notify(notification)
  }

  private abstract class CommitStrategy {
    open fun start() = Unit
    open fun finish() = Unit
    abstract fun doUserCommit(
      successfulCommits: MutableList<VcsCommitMetadata>,
      alreadyPicked: MutableList<VcsCommitMetadata>,
    ): Boolean

    open fun afterChangesRefreshed() = Unit
  }

  private inner class ChangeListCommit(
    val repository: GitRepository,
    val commit: VcsCommitMetadata,
    val commitMessage: String,
  ) : CommitStrategy() {
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

    override fun doUserCommit(
      successfulCommits: MutableList<VcsCommitMetadata>,
      alreadyPicked: MutableList<VcsCommitMetadata>,
    ): Boolean {
      return commitChangelist(repository, commit, commitMessage, changeList, successfulCommits, alreadyPicked)
    }
  }

  private inner class SimplifiedCommit(
    val repository: GitRepository,
    val commit: VcsCommitMetadata,
    val commitMessage: String,
  ) : CommitStrategy() {
    override fun doUserCommit(
      successfulCommits: MutableList<VcsCommitMetadata>,
      alreadyPicked: MutableList<VcsCommitMetadata>,
    ): Boolean {
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

  private class StagingAreaCommit(
    val repository: GitRepository,
    val commit: VcsCommitMetadata,
    val commitMessage: String,
  ) : CommitStrategy() {
    override fun doUserCommit(
      successfulCommits: MutableList<VcsCommitMetadata>,
      alreadyPicked: MutableList<VcsCommitMetadata>,
    ): Boolean {
      runInEdt {
        showStagingArea(repository.project, commitMessage)
      }
      return false
    }
  }

  private fun commitChangelist(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    commitMessage: String,
    changeList: LocalChangeList,
    successfulCommits: MutableList<VcsCommitMetadata>,
    alreadyPicked: MutableList<VcsCommitMetadata>,
  ): Boolean {
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

  private fun showCommitDialogAndWaitForCommit(
    repository: GitRepository,
    changeList: LocalChangeList,
    commitMessage: String,
    changes: Collection<Change>,
  ): Boolean {
    val commitSucceeded = AtomicBoolean()
    val sem = Semaphore(0)
    ApplicationManager.getApplication().invokeAndWait({
      try {
        cleanupBeforeCommit(repository)
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
    }, ModalityState.nonModal())

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

  private fun createChangeListData(commit: VcsCommitMetadata): ChangeListData {
    return ChangeListData(author = commit.author,
                          date = Date(commit.authorTime),
                          automatic = true)
  }

  private fun notifyResult(successfulCommits: List<VcsCommitMetadata>, skipped: List<VcsCommitMetadata>) {
    when {
      skipped.isEmpty() -> {
        vcsNotifier.notifySuccess(GitNotificationIdsHolder.APPLY_CHANGES_SUCCESS,
                                  GitBundle.message("apply.changes.operation.successful", operationName.capitalize()),
                                  getCommitsDetails(successfulCommits))
      }
      successfulCommits.isNotEmpty() -> {
        val title = GitBundle.message("apply.changes.applied.for.commits", appliedWord.capitalize(), successfulCommits.size,
                                      successfulCommits.size + skipped.size)
        val description = getCommitsDetails(successfulCommits) + UIUtil.HR + formSkippedDescription(skipped, true)
        vcsNotifier.notifySuccess(GitNotificationIdsHolder.APPLY_CHANGES_SUCCESS, title, description)
      }
      else -> {
        vcsNotifier.notify(GitApplyChangesNothingToDoNotification(operationName, formSkippedDescription(skipped, false)))
      }
    }
  }

  private fun notifyConflictWarning(
    repository: GitRepository,
    commit: VcsCommitMetadata,
    successfulCommits: List<VcsCommitMetadata>,
  ) {
    val description = commitDetails(commit) +
                      UIUtil.BR +
                      GitBundle.message("apply.changes.unresolved.conflicts.text") +
                      getSuccessfulCommitDetailsIfAny(successfulCommits)
    VcsNotifier.getInstance(project)
      .notify(GitApplyChangesConflictNotification(operationName, description, commit, repository, abortCommand))
  }

  private fun notifyCommitCancelled(commit: VcsCommitMetadata, successfulCommits: List<VcsCommitMetadata>) {
    if (successfulCommits.isEmpty()) {
      // don't notify about cancelled commit. Notify just in the case when there were already successful commits in the queue.
      return
    }
    var description = commitDetails(commit)
    description += getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notifyMinorWarning(GitNotificationIdsHolder.COMMIT_CANCELLED, GitBundle.message("apply.changes.operation.canceled", operationName.capitalize()), description)
  }

  private fun notifyError(
    @Nls content: String,
    failedCommit: VcsCommitMetadata,
    successfulCommits: List<VcsCommitMetadata>,
  ) {
    var description = commitDetails(failedCommit) + UIUtil.BR + content
    description += getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notifyError(GitNotificationIdsHolder.APPLY_CHANGES_ERROR, GitBundle.message("apply.changes.operation.failed", operationName.capitalize()), description)
  }

  @Nls
  private fun getSuccessfulCommitDetailsIfAny(successfulCommits: List<VcsCommitMetadata>) =
    getSuccessfulCommitDetailsIfAny(successfulCommits, operationName)

  @Nls
  private fun formSkippedDescription(skipped: List<VcsCommitMetadata>, but: Boolean): String {
    val hashes = StringUtil.join(skipped, { commit -> commit.id.toShortString() }, ", ")
    if (but) {
      return GitBundle.message("apply.changes.skipped", hashes, skipped.size, appliedWord)
    }
    return GitBundle.message("apply.changes.everything.applied", hashes, appliedWord)
  }

  private fun toString(commitsInRoots: Map<GitRepository, List<VcsCommitMetadata>>): String {
    return commitsInRoots.entries.joinToString("; ") { entry ->
      val commits = entry.value.joinToString { it.id.asString() }
      getShortRepositoryName(entry.key) + ": [" + commits + "]"
    }
  }

  class ConflictResolver(
    project: Project,
    root: VirtualFile,
    commitHash: String,
    commitAuthor: String,
    commitMessage: String,
    @Nls operationName: String,
  ) : GitConflictResolver(project, setOf(root),
                          makeParams(project, commitHash, commitAuthor, commitMessage, operationName)) {
    override fun notifyUnresolvedRemain() {/* we show a [possibly] compound notification after applying all commits.*/
    }
  }

  internal companion object {
    private val LOG = logger<GitApplyChangesProcess>()

    @NlsSafe
    fun commitDetails(commit: VcsCommitMetadata): String {
      return commit.id.toShortString() + " " + StringUtil.escapeXmlEntities(commit.subject)
    }

    @Nls
    fun getSuccessfulCommitDetailsIfAny(successfulCommits: List<VcsCommitMetadata>, operationName: String): String {
      var description = ""
      if (successfulCommits.isNotEmpty()) {
        description += UIUtil.HR +
                       GitBundle.message("apply.changes.operation.successful.for.commits", operationName, successfulCommits.size) +
                       UIUtil.BR
        description += getCommitsDetails(successfulCommits)
      }
      return description
    }

    @NlsSafe
    private fun getCommitsDetails(successfulCommits: List<VcsCommitMetadata>): String {
      var description = ""
      for (commit in successfulCommits) {
        if (description.isNotEmpty()) description += UIUtil.BR
        description += commitDetails(commit)
      }
      return description
    }
  }
}

private fun makeParams(
  project: Project,
  commitHash: String,
  commitAuthor: String,
  commitMessage: String,
  @Nls operationName: String,
): GitConflictResolver.Params {

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
  @Nls private val operationName: String,
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
