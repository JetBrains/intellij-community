/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.getShortRepositoryName
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.util.VcsUserUtil
import git4idea.commands.*
import git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT
import git4idea.commands.GitSimpleEventDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK
import git4idea.merge.GitConflictResolver
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitUntrackedFilesHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.event.HyperlinkEvent

/**
 * Applies the given Git operation (e.g. cherry-pick or revert) to the current working tree,
 * waits for the [ChangeListManager] update, shows the commit dialog and removes the changelist after commit,
 * if the commit was successful.
 */
class GitApplyChangesProcess(private val project: Project,
                             private val commits: List<VcsFullCommitDetails>,
                             private val autoCommit: Boolean,
                             private val operationName: String,
                             private val appliedWord: String,
                             private val command: (GitRepository, Hash, Boolean, List<GitLineHandlerListener>) -> GitCommandResult,
                             private val emptyCommitDetector: (GitCommandResult) -> Boolean,
                             private val defaultCommitMessageGenerator: (VcsFullCommitDetails) -> String,
                             private val findLocalChanges: (Collection<Change>) -> Collection<Change>,
                             private val preserveCommitMetadata: Boolean,
                             private val cleanupBeforeCommit: (GitRepository) -> Unit = {}) {
  private val LOG = logger<GitApplyChangesProcess>()
  private val git = Git.getInstance()
  private val repositoryManager = GitRepositoryManager.getInstance(project)
  private val vcsNotifier = VcsNotifier.getInstance(project)
  private val changeListManager = ChangeListManager.getInstance(project)
  private val vcsHelper = AbstractVcsHelper.getInstance(project)

  fun execute() {
    val commitsInRoots = DvcsUtil.groupCommitsByRoots<GitRepository>(repositoryManager, commits)
    LOG.info("${operationName}ing commits: " + toString(commitsInRoots))

    val successfulCommits = mutableListOf<VcsFullCommitDetails>()
    val skippedCommits = mutableListOf<VcsFullCommitDetails>()

    val token = DvcsUtil.workingTreeChangeStarted(project)
    try {
      for ((repository, value) in commitsInRoots) {
        val result = executeForRepo(repository, value, successfulCommits, skippedCommits)
        repository.update()
        if (!result) {
          return
        }
      }
      notifyResult(successfulCommits, skippedCommits)
    }
    finally {
      token.finish()
    }
  }

  // return true to continue with other roots, false to break execution
  private fun executeForRepo(repository: GitRepository,
                             commits: List<VcsFullCommitDetails>,
                             successfulCommits: MutableList<VcsFullCommitDetails>,
                             alreadyPicked: MutableList<VcsFullCommitDetails>): Boolean {
    for (commit in commits) {
      val conflictDetector = GitSimpleEventDetector(CHERRY_PICK_CONFLICT)
      val localChangesOverwrittenDetector = GitSimpleEventDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK)
      val untrackedFilesDetector = GitUntrackedFilesOverwrittenByOperationDetector(repository.root)

      val result = command(repository, commit.id, autoCommit,
                           listOf(conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector))

      if (result.success()) {
        if (autoCommit) {
          successfulCommits.add(commit)
        }
        else {
          val committed = updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(repository, commit,
                                                                                              successfulCommits, alreadyPicked)
          if (!committed) {
            notifyCommitCancelled(commit, successfulCommits)
            return false
          }
        }
      }
      else if (conflictDetector.hasHappened()) {
        val mergeCompleted = ConflictResolver(project, git, repository.root,
                                              commit.id.asString(), VcsUserUtil.getShortPresentation(commit.author),
                                              commit.subject, operationName).merge()

        if (mergeCompleted) {
          val committed = updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(repository, commit,
                                                                                              successfulCommits, alreadyPicked)
          if (!committed) {
            notifyCommitCancelled(commit, successfulCommits)
            return false
          }
        }
        else {
          updateChangeListManager(commit)
          notifyConflictWarning(repository, commit, successfulCommits)
          return false
        }
      }
      else if (untrackedFilesDetector.wasMessageDetected()) {
        var description = commitDetails(commit) +
                          "Please move or commit them before $operationName."
        description += getSuccessfulCommitDetailsIfAny(successfulCommits)

        var notification = GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, repository.root,
                                                                  untrackedFilesDetector.relativeFilePaths, operationName, description, null)
        vcsNotifier.notify(notification)
        return false
      }
      else if (localChangesOverwrittenDetector.hasHappened()) {
        notifyError("Your local changes would be overwritten by $operationName.<br/>Commit your changes or stash them to proceed.",
                    commit, successfulCommits)
        return false
      }
      else if (emptyCommitDetector(result)) {
        alreadyPicked.add(commit)
      }
      else {
        notifyError(result.errorOutputAsHtmlString, commit, successfulCommits)
        return false
      }
    }
    return true
  }

  private fun updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(repository: GitRepository,
                                                                                  commit: VcsFullCommitDetails,
                                                                                  successfulCommits: MutableList<VcsFullCommitDetails>,
                                                                                  alreadyPicked: MutableList<VcsFullCommitDetails>): Boolean {
    val data = updateChangeListManager(commit)
    if (data == null) {
      alreadyPicked.add(commit)
      return true
    }
    val committed = showCommitDialogAndWaitForCommit(repository, commit, data.changeList, data.commitMessage)
    if (committed) {
      changeListManager.removeChangeList(data.changeList)
      successfulCommits.add(commit)
      return true
    }
    return false
  }

  private fun updateChangeListManager(commit: VcsFullCommitDetails): ChangeListData? {
    val changes = commit.changes
    RefreshVFsSynchronously.updateChanges(changes)
    val commitMessage = defaultCommitMessageGenerator(commit)
    val paths = ChangesUtil.getPaths(changes)
    val changeList = createChangeListAfterUpdate(commit, paths, commitMessage)
    return if (changeList == null) null else ChangeListData(changeList, commitMessage)
  }

  private fun createChangeListAfterUpdate(commit: VcsFullCommitDetails, paths: Collection<FilePath>,
                                          commitMessage: String): LocalChangeList? {
    val waiter = CountDownLatch(1)
    val changeList = Ref.create<LocalChangeList>()
    changeListManager.invokeAfterUpdate({
                                          changeList.set(createChangeListIfThereAreChanges(commit, commitMessage))
                                          waiter.countDown()
                                        }, InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED, operationName.capitalize(),
                                        { vcsDirtyScopeManager -> vcsDirtyScopeManager.filePathsDirty(paths, null) },
                                        ModalityState.NON_MODAL)
    try {
      val success = waiter.await(100, TimeUnit.SECONDS)
      if (!success) {
        LOG.error("Couldn't await for changelist manager refresh")
      }
    }
    catch (e: InterruptedException) {
      LOG.error(e)
      return null
    }

    return changeList.get()
  }

  private fun showCommitDialogAndWaitForCommit(repository: GitRepository, commit: VcsFullCommitDetails,
                                               changeList: LocalChangeList, commitMessage: String): Boolean {
    val commitSucceeded = AtomicBoolean()
    val sem = Semaphore(0)
    ApplicationManager.getApplication().invokeAndWait({
      try {
        cleanupBeforeCommit(repository)
        val changes = commit.changes
        val commitNotCancelled = vcsHelper.commitChanges(
          changes, changeList, commitMessage,
          object : CommitResultHandler {
            override fun onSuccess(commitMessage1: String) {
              commitSucceeded.set(true)
              sem.release()
            }

            override fun onFailure() {
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

  private fun createChangeListIfThereAreChanges(commit: VcsFullCommitDetails, commitMessage: String): LocalChangeList? {
    val originalChanges = commit.changes
    if (originalChanges.isEmpty()) {
      LOG.info("Empty commit " + commit.id)
      return null
    }
    if (noChangesAfterApply(originalChanges)) {
      LOG.info("No changes after executing for commit ${commit.id}")
      return null
    }

    val changeListName = createNameForChangeList(commitMessage)
    val createdChangeList = (changeListManager as ChangeListManagerEx).addChangeList(changeListName, commitMessage,
                                                                                     if (preserveCommitMetadata) commit else null)
    val actualChangeList = moveChanges(originalChanges, createdChangeList)
    if (actualChangeList != null && !actualChangeList.changes.isEmpty()) {
      return createdChangeList
    }
    LOG.warn("No changes were moved to the changelist. Changes from commit: " + originalChanges +
             "\nAll changes: " + changeListManager.getAllChanges())
    changeListManager.removeChangeList(createdChangeList)
    return null
  }

  private fun noChangesAfterApply(originalChanges: Collection<Change>): Boolean {
    return findLocalChanges(originalChanges).isEmpty()
  }

  private fun moveChanges(originalChanges: Collection<Change>, targetChangeList: LocalChangeList): LocalChangeList? {
    val localChanges = findLocalChanges(originalChanges)

    // 1. We have to listen to CLM changes, because moveChangesTo is asynchronous
    // 2. We have to collect the real target change list, because the original target list (passed to moveChangesTo) is not updated in time.
    val moveChangesWaiter = CountDownLatch(1)
    val resultingChangeList = AtomicReference<LocalChangeList>()
    val listener = object : ChangeListAdapter() {
      override fun changesMoved(changes: Collection<Change>, fromList: ChangeList, toList: ChangeList) {
        if (toList is LocalChangeList && targetChangeList.id == toList.id) {
          resultingChangeList.set(toList)
          moveChangesWaiter.countDown()
        }
      }
    }
    try {
      changeListManager.addChangeListListener(listener)
      changeListManager.moveChangesTo(targetChangeList, *localChanges.toTypedArray())
      val success = moveChangesWaiter.await(100, TimeUnit.SECONDS)
      if (!success) {
        LOG.error("Couldn't await for changes move.")
      }
      return resultingChangeList.get()
    }
    catch (e: InterruptedException) {
      LOG.error(e)
      return null
    }
    finally {
      changeListManager.removeChangeListListener(listener)
    }
  }

  private fun createNameForChangeList(commitMessage: String): String {
    val proposedName = commitMessage.trim()
      .substringBefore('\n')
      .trim()
      .replace("[ ]{2,}".toRegex(), " ")
    return UniqueNameGenerator.generateUniqueName(proposedName, "", "", "-", "", { changeListManager.findChangeList(it) == null })
  }

  private fun notifyResult(successfulCommits: List<VcsFullCommitDetails>, skipped: List<VcsFullCommitDetails>) {
    if (skipped.isEmpty()) {
      vcsNotifier.notifySuccess("${operationName.capitalize()} successful", getCommitsDetails(successfulCommits))
    }
    else if (!successfulCommits.isEmpty()) {
      val title = String.format("${operationName.capitalize()}ed %d commits from %d", successfulCommits.size,
                                successfulCommits.size + skipped.size)
      val description = getCommitsDetails(successfulCommits) + "<hr/>" + formSkippedDescription(skipped, true)
      vcsNotifier.notifySuccess(title, description)
    }
    else {
      vcsNotifier.notifyImportantWarning("Nothing to $operationName", formSkippedDescription(skipped, false))
    }
  }

  private fun notifyConflictWarning(repository: GitRepository,
                                    commit: VcsFullCommitDetails,
                                    successfulCommits: List<VcsFullCommitDetails>) {
    val resolveLinkListener = ResolveLinkListener(repository.root,
                                                  commit.id.toShortString(),
                                                  VcsUserUtil.getShortPresentation(commit.author),
                                                  commit.subject)
    var description = commitDetails(commit) + "<br/>Unresolved conflicts remain in the working tree. <a href='resolve'>Resolve them.<a/>"
    description += getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notifyImportantWarning("${operationName.capitalize()}ed with conflicts", description, resolveLinkListener)
  }

  private fun notifyCommitCancelled(commit: VcsFullCommitDetails, successfulCommits: List<VcsFullCommitDetails>) {
    if (successfulCommits.isEmpty()) {
      // don't notify about cancelled commit. Notify just in the case when there were already successful commits in the queue.
      return
    }
    var description = commitDetails(commit)
    description += getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notifyMinorWarning("${operationName.capitalize()} cancelled", description, null)
  }

  private fun notifyError(content: String,
                          failedCommit: VcsFullCommitDetails,
                          successfulCommits: List<VcsFullCommitDetails>) {
    var description = commitDetails(failedCommit) + "<br/>" + content
    description += getSuccessfulCommitDetailsIfAny(successfulCommits)
    vcsNotifier.notifyError("${operationName.capitalize()} Failed", description)
  }

  private fun getSuccessfulCommitDetailsIfAny(successfulCommits: List<VcsFullCommitDetails>): String {
    var description = ""
    if (!successfulCommits.isEmpty()) {
      description += "<hr/>However ${operationName} succeeded for the following " + pluralize("commit", successfulCommits.size) + ":<br/>"
      description += getCommitsDetails(successfulCommits)
    }
    return description
  }

  private fun formSkippedDescription(skipped: List<VcsFullCommitDetails>, but: Boolean): String {
    val hashes = StringUtil.join(skipped, { commit -> commit.id.toShortString() }, ", ")
    if (but) {
      val was = if (skipped.size == 1) "was" else "were"
      val it = if (skipped.size == 1) "it" else "them"
      return String.format("%s %s skipped, because all changes have already been ${appliedWord}.", hashes, was, it)
    }
    return String.format("All changes from %s have already been ${appliedWord}", hashes)
  }

  private fun getCommitsDetails(successfulCommits: List<VcsFullCommitDetails>): String {
    var description = ""
    for (commit in successfulCommits) {
      description += commitDetails(commit) + "<br/>"
    }
    return description.substring(0, description.length - "<br/>".length)
  }

  private fun commitDetails(commit: VcsFullCommitDetails): String {
    return commit.id.toShortString() + " " + commit.subject
  }

  private fun toString(commitsInRoots: Map<GitRepository, List<VcsFullCommitDetails>>): String {
    return commitsInRoots.entries.joinToString("; ") { entry ->
      val commits = entry.value.joinToString { it.id.asString() }
      getShortRepositoryName(entry.key) + ": [" + commits + "]"
    }
  }

  private inner class ResolveLinkListener(private val root: VirtualFile,
                                          private val hash: String,
                                          private val author: String,
                                          private val message: String) : NotificationListener {

    override fun hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
      if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        if (event.description == "resolve") {
          GitApplyChangesProcess.ConflictResolver(project, git, root, hash, author, message, operationName).mergeNoProceed()
        }
      }
    }
  }

  private data class ChangeListData(val changeList: LocalChangeList, val commitMessage: String)

  class ConflictResolver(project: Project,
                         git: Git,
                         root: VirtualFile,
                         commitHash: String,
                         commitAuthor: String,
                         commitMessage: String,
                         operationName: String
  ) : GitConflictResolver(project, git, setOf(root), makeParams(commitHash, commitAuthor, commitMessage, operationName)) {
    override fun notifyUnresolvedRemain() {/* we show a [possibly] compound notification after applying all commits.*/
    }
  }
}

private fun makeParams(commitHash: String, commitAuthor: String, commitMessage: String, operationName: String): GitConflictResolver.Params {
  val params = GitConflictResolver.Params()
  params.setErrorNotificationTitle("${operationName.capitalize()}ed with conflicts")
  params.setMergeDialogCustomizer(MergeDialogCustomizer(commitHash, commitAuthor, commitMessage, operationName))
  return params
}

private class MergeDialogCustomizer(private val commitHash: String,
                                    private val commitAuthor: String,
                                    private val commitMessage: String,
                                    private val operationName: String) : MergeDialogCustomizer() {

  override fun getMultipleFileMergeDescription(files: Collection<VirtualFile>) =
    "<html>Conflicts during ${operationName}ing commit <code>$commitHash</code> " +
    "made by $commitAuthor<br/><code>\"$commitMessage\"</code></html>"

  override fun getLeftPanelTitle(file: VirtualFile) = "Local changes"

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?) =
    "<html>Changes from $operationName <code>$commitHash</code>"
}
