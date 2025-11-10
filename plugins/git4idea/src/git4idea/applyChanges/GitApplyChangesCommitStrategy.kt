// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.applyChanges

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.*
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.index.showStagingArea
import git4idea.repo.GitRepository
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<GitApplyChangesCommitStrategy>()

internal sealed class GitApplyChangesCommitStrategy(project: Project) {
  protected val vcsNotifier = VcsNotifier.getInstance(project)
  protected val changeListManager = ChangeListManagerEx.getInstanceEx(project)
  protected val vcsHelper = AbstractVcsHelper.getInstance(project)

  open fun start() = Unit
  open fun finish() = Unit
  open fun afterChangesRefreshed() = Unit

  abstract fun doUserCommit(
    onSuccessfulCommit: (VcsCommitMetadata) -> Unit,
    onSkippedCommit: (VcsCommitMetadata) -> Unit,
    onCancelledCommit: (VcsCommitMetadata) -> Unit,
  ): Boolean
}

internal class ChangeListGitApplyChangesCommit(
  private val repository: GitRepository,
  private val gitApplyChangesProcess: GitApplyChangesProcess,
  private val commit: VcsCommitMetadata,
  private  val commitMessage: @NlsSafe String,
  private val preserveCommitMetadata: Boolean,
) : GitApplyChangesCommitStrategy(repository.project) {
  lateinit var changeList: LocalChangeList
  lateinit var previousDefaultChangelist: LocalChangeList

  override fun start() {
    previousDefaultChangelist = changeListManager.defaultChangeList

    val changeListName = createNameForChangeList(repository.project, commitMessage)
    val changeListData = if (preserveCommitMetadata) commit.toChangeListData() else null
    changeList = changeListManager.addChangeList(changeListName, commitMessage, changeListData)
    changeListManager.setDefaultChangeList(changeList, true)
  }

  override fun finish() {
    changeListManager.setDefaultChangeList(previousDefaultChangelist, true)
    changeListManager.scheduleAutomaticEmptyChangeListDeletion(changeList, true)
  }

  override fun doUserCommit(
    onSuccessfulCommit: (VcsCommitMetadata) -> Unit,
    onSkippedCommit: (VcsCommitMetadata) -> Unit,
    onCancelledCommit: (VcsCommitMetadata) -> Unit,
  ): Boolean = commitChangelist(repository, gitApplyChangesProcess, changeListManager, commit, commitMessage, changeListManager.defaultChangeList, vcsHelper, onSuccessfulCommit = onSuccessfulCommit, onSkippedCommit = onSkippedCommit, onCancelledCommit = onCancelledCommit)
}

internal class SimplifiedGitApplyChangesCommit(
  private val repository: GitRepository,
  private val gitApplyChangesProcess: GitApplyChangesProcess,
  private val commit: VcsCommitMetadata,
  private val commitMessage: @NlsSafe String,
  private val preserveCommitMetadata: Boolean,
) : GitApplyChangesCommitStrategy(repository.project) {

  override fun doUserCommit(
    onSuccessfulCommit: (VcsCommitMetadata) -> Unit,
    onSkippedCommit: (VcsCommitMetadata) -> Unit,
    onCancelledCommit: (VcsCommitMetadata) -> Unit,
  ): Boolean = commitChangelist(repository, gitApplyChangesProcess, changeListManager, commit, commitMessage, changeListManager.defaultChangeList, vcsHelper, onSuccessfulCommit = onSuccessfulCommit, onSkippedCommit = onSkippedCommit, onCancelledCommit = onCancelledCommit)

  override fun afterChangesRefreshed() {
    val list = changeListManager.defaultChangeList
    if (preserveCommitMetadata && changeListManager.areChangeListsEnabled() && list.changes.isNotEmpty()) {
      changeListManager.editChangeListData(list.name, commit.toChangeListData())
    }
  }
}

internal class StagingAreaGitApplyChangesCommit(
  private val project: Project,
  private val commitMessage: String,
) : GitApplyChangesCommitStrategy(project) {

  override fun doUserCommit(
    onSuccessfulCommit: (VcsCommitMetadata) -> Unit,
    onSkippedCommit: (VcsCommitMetadata) -> Unit,
    onCancelledCommit: (VcsCommitMetadata) -> Unit,
  ): Boolean {
    runInEdt {
      showStagingArea(project, commitMessage)
    }
    return false
  }
}

private fun commitChangelist(
  repository: GitRepository,
  gitApplyChangesProcess: GitApplyChangesProcess,
  changeListManager: ChangeListManagerEx,
  commit: VcsCommitMetadata,
  commitMessage: String,
  changeList: LocalChangeList,
  vcsHelper: AbstractVcsHelper,
  onSuccessfulCommit: (VcsCommitMetadata) -> Unit,
  onSkippedCommit: (VcsCommitMetadata) -> Unit,
  onCancelledCommit: (VcsCommitMetadata) -> Unit,
): Boolean {
  val actualList = changeListManager.getChangeList(changeList.id) ?: run {
    LOG.error("Couldn't find the changelist with id ${changeList.id} and name ${changeList.name} among ${changeListManager.getAllChangesInLogFriendlyPresentation()}")
    return false
  }
  val changes = actualList.changes
  if (changes.isEmpty()) {
    LOG.debug("No changes in the $actualList. All changes in the CLM: ${changeListManager.getAllChangesInLogFriendlyPresentation()}")
    onSkippedCommit(commit)
    return true
  }

  LOG.debug("Showing commit dialog for changes: ${changes}")
  gitApplyChangesProcess.cleanupBeforeCommit(repository)
  val committed = vcsHelper.showCommitDialogAndWaitForCommit(changeList, commitMessage, changes)
  if (committed) {
    repository.project.markDirty(changes)
    changeListManager.waitForUpdate()

    onSuccessfulCommit(commit)
    return true
  }
  else {
    onCancelledCommit(commit)
    return false
  }
}

private fun VcsCommitMetadata.toChangeListData(): ChangeListData = ChangeListData(author = author, date = Date(authorTime), automatic = true)

private fun ChangeListManager.getAllChangesInLogFriendlyPresentation() = changeLists.map { "[${it.name}] ${it.changes}" }

private fun Project.markDirty(changes: Collection<Change>) {
  VcsDirtyScopeManager.getInstance(this).filePathsDirty(ChangesUtil.getPaths(changes), null)
}

private fun AbstractVcsHelper.showCommitDialogAndWaitForCommit(
  changeList: LocalChangeList,
  commitMessage: String,
  changes: Collection<Change>,
): Boolean {
  val commitSucceeded = AtomicBoolean()
  val sem = Semaphore(0)
  val runnable = {
    try {
      val commitNotCancelled = commitChanges(changes, changeList, commitMessage, object : CommitResultHandler {
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
  }
  ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.nonModal())

  // Need additional waiting because commitChanges is asynchronous
  try {
    sem.acquire()
  }
  catch (e: InterruptedException) {
    LOG.error(e)
    return false
  }

  return commitSucceeded.get()
}
