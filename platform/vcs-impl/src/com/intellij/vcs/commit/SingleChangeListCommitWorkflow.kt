// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getWarningIcon
import com.intellij.openapi.ui.Messages.showYesNoDialog
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.changes.ui.SessionDialog
import com.intellij.vcs.commit.SingleChangeListCommitter.Companion.moveToFailedList
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.impl.PartialChangesUtil.getPartialTracker
import com.intellij.util.ui.UIUtil.removeMnemonic

private val LOG = logger<SingleChangeListCommitWorkflow>()

internal val CommitOptions.changeListSpecificOptions: Sequence<CheckinChangeListSpecificComponent>
  get() = allOptions.filterIsInstance<CheckinChangeListSpecificComponent>()

internal fun CommitOptions.changeListChanged(changeList: LocalChangeList) = changeListSpecificOptions.forEach {
  it.onChangeListSelected(changeList)
}

internal fun CommitOptions.saveChangeListSpecificOptions() = changeListSpecificOptions.forEach { it.saveState() }

internal fun removeEllipsisSuffix(s: String) = s.removeSuffix("...").removeSuffix("\u2026")
internal fun CommitExecutor.getPresentableText() = removeEllipsisSuffix(removeMnemonic(actionText))

open class SingleChangeListCommitWorkflow(
  project: Project,
  val initiallyIncluded: Collection<*>,
  val initialChangeList: LocalChangeList? = null,
  val executors: List<CommitExecutor> = emptyList(),
  final override val isDefaultCommitEnabled: Boolean = executors.isEmpty(),
  val vcsToCommit: AbstractVcs<*>? = null,
  affectedVcses: Set<AbstractVcs<*>> = if (vcsToCommit != null) setOf(vcsToCommit) else emptySet(),
  private val isDefaultChangeListFullyIncluded: Boolean = true,
  val initialCommitMessage: String? = null,
  private val resultHandler: CommitResultHandler? = null
) : AbstractCommitWorkflow(project) {

  init {
    updateVcses(affectedVcses)
  }

  val isPartialCommitEnabled: Boolean = vcses.any { it.arePartialChangelistsSupported() } && (isDefaultCommitEnabled || executors.any { it.supportsPartialCommit() })

  val commitMessagePolicy: SingleChangeListCommitMessagePolicy = SingleChangeListCommitMessagePolicy(project, initialCommitMessage)

  internal lateinit var commitState: ChangeListCommitState

  override fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) = when (result) {
    CheckinHandler.ReturnResult.COMMIT -> DefaultNameChangeListCleaner(project, commitState).use { doCommit(commitState) }
    CheckinHandler.ReturnResult.CLOSE_WINDOW ->
      moveToFailedList(project, commitState, message("commit.dialog.rejected.commit.template", commitState.changeList.name))
    CheckinHandler.ReturnResult.CANCEL -> Unit
  }

  fun executeCustom(executor: CommitExecutor, session: CommitSession, commitState: ChangeListCommitState) {
    if (!configureCommitSession(executor, session, commitState.changes, commitState.commitMessage)) return

    val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(false, executor)
    when (beforeCommitChecksResult) {
      CheckinHandler.ReturnResult.COMMIT -> {
        val success = doCommitCustom(executor, session, commitState)
        if (success) eventDispatcher.multicaster.customCommitSucceeded()
      }
      CheckinHandler.ReturnResult.CLOSE_WINDOW ->
        moveToFailedList(project, commitState, message("commit.dialog.rejected.commit.template", commitState.changeList.name))
      CheckinHandler.ReturnResult.CANCEL -> Unit
    }
  }

  private fun configureCommitSession(executor: CommitExecutor,
                                     session: CommitSession,
                                     changes: List<Change>,
                                     commitMessage: String): Boolean {
    val sessionConfigurationUi = SessionDialog.createConfigurationUI(session, changes, commitMessage) ?: return true
    val sessionDialog = SessionDialog(executor.getPresentableText(), project, session, changes, commitMessage, sessionConfigurationUi)

    if (sessionDialog.showAndGet()) return true
    else {
      session.executionCanceled()
      return false
    }
  }

  override fun doRunBeforeCommitChecks(checks: Runnable) =
    PartialChangesUtil.runUnderChangeList(project, commitState.changeList, checks)

  open fun canExecute(executor: CommitExecutor, changes: Collection<Change>): Boolean {
    if (!executor.supportsPartialCommit()) {
      val hasPartialChanges = changes.any { getPartialTracker(project, it)?.hasPartialChangesToCommit() ?: false }
      if (hasPartialChanges) {
        return Messages.YES == showYesNoDialog(
          project, message("commit.dialog.partial.commit.warning.body", executor.getPresentableText()),
          message("commit.dialog.partial.commit.warning.title"), executor.actionText, getCancelButtonText(), getWarningIcon())
      }
    }
    return true
  }

  protected open fun doCommit(commitState: ChangeListCommitState) {
    LOG.debug("Do actual commit")
    val committer = SingleChangeListCommitter(project, commitState, commitContext, commitHandlers, vcsToCommit, DIALOG_TITLE,
                                              isDefaultChangeListFullyIncluded)

    committer.addResultHandler(resultHandler ?: DefaultCommitResultHandler(committer))
    committer.runCommit(DIALOG_TITLE, false)
  }

  private fun doCommitCustom(executor: CommitExecutor, session: CommitSession, commitState: ChangeListCommitState): Boolean {
    var success = false
    val cleaner = DefaultNameChangeListCleaner(project, commitState)
    try {
      val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        { session.execute(commitState.changes, commitState.commitMessage) }, executor.actionText, true, project)

      if (completed) {
        LOG.debug("Commit successful")
        commitHandlers.forEach { it.checkinSuccessful() }
        success = true
        cleaner.clean()
      }
      else {
        LOG.debug("Commit canceled")
        session.executionCanceled()
      }
    }
    catch (e: Throwable) {
      Messages.showErrorDialog(message("error.executing.commit", executor.actionText, e.localizedMessage), executor.actionText)

      val errors = listOf(VcsException(e))
      commitHandlers.forEach { it.checkinFailed(errors) }
    }
    finally {
      finishCustom(commitState.commitMessage, success)
    }
    return success
  }

  private fun finishCustom(commitMessage: String, success: Boolean) =
    resultHandler?.let { if (success) it.onSuccess(commitMessage) else it.onFailure() }
}

private class DefaultNameChangeListCleaner(val project: Project, commitState: ChangeListCommitState) {
  private val isChangeListFullyIncluded = commitState.changeList.changes.size == commitState.changes.size
  private val isDefaultNameChangeList = commitState.changeList.hasDefaultName()

  fun use(block: () -> Unit) {
    block()
    clean()
  }

  fun clean() {
    if (isDefaultNameChangeList && isChangeListFullyIncluded) {
      ChangeListManager.getInstance(project).editComment(LocalChangeList.DEFAULT_NAME, "")
    }
  }
}