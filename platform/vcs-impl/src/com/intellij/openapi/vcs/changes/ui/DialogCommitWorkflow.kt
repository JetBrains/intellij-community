// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getWarningIcon
import com.intellij.openapi.ui.Messages.showYesNoDialog
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction.addUnversionedFilesToVcs
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.changes.ui.SingleChangeListCommitter.Companion.moveToFailedList
import com.intellij.openapi.vcs.checkin.BaseCheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.openapi.vcs.impl.CheckinHandlersManager
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.impl.PartialChangesUtil.getPartialTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.NullableFunction
import com.intellij.util.PairConsumer
import com.intellij.util.containers.ContainerUtil.newUnmodifiableList
import com.intellij.util.ui.UIUtil.removeMnemonic
import java.util.*

private val LOG = logger<DialogCommitWorkflow>()

internal fun CommitOptions.saveState() = allOptions.forEach { it.saveState() }
internal fun CommitOptions.restoreState() = allOptions.forEach { it.restoreState() }
internal fun CommitOptions.refresh() = allOptions.forEach { it.refresh() }

internal val CommitOptions.changeListSpecificOptions: Sequence<CheckinChangeListSpecificComponent>
  get() = allOptions.filterIsInstance<CheckinChangeListSpecificComponent>()

internal fun CommitOptions.changeListChanged(changeList: LocalChangeList) = changeListSpecificOptions.forEach {
  it.onChangeListSelected(changeList)
}

internal fun CommitOptions.saveChangeListSpecificOptions() = changeListSpecificOptions.forEach { it.saveState() }

internal fun removeEllipsisSuffix(s: String) = s.removeSuffix("...").removeSuffix("\u2026")
internal fun CommitExecutor.getPresentableText() = removeEllipsisSuffix(removeMnemonic(actionText))

interface CommitWorkflowListener : EventListener {
  fun beforeCommitChecksStarted()
  fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult)

  fun customCommitSucceeded()
}

open class DialogCommitWorkflow(val project: Project,
                                val initiallyIncluded: Collection<*>,
                                val initialChangeList: LocalChangeList? = null,
                                val executors: List<CommitExecutor> = emptyList(),
                                val isDefaultCommitEnabled: Boolean = executors.isEmpty(),
                                val vcsToCommit: AbstractVcs<*>? = null,
                                val affectedVcses: Set<AbstractVcs<*>> = if (vcsToCommit != null) setOf(vcsToCommit) else emptySet(),
                                private val isDefaultChangeListFullyIncluded: Boolean = true,
                                val initialCommitMessage: String? = null,
                                private val resultHandler: CommitResultHandler? = null) {
  val isPartialCommitEnabled: Boolean = affectedVcses.any { it.arePartialChangelistsSupported() } && (isDefaultCommitEnabled || executors.any { it.supportsPartialCommit() })

  val commitContext: CommitContext = CommitContext()

  // TODO Probably unify with "CommitContext"
  private val _additionalData = PseudoMap<Any, Any>()
  val additionalDataConsumer: PairConsumer<Any, Any> get() = _additionalData
  protected val additionalData: NullableFunction<Any, Any> get() = _additionalData

  private val _commitHandlers = mutableListOf<CheckinHandler>()
  val commitHandlers: List<CheckinHandler> get() = newUnmodifiableList(_commitHandlers)

  private val _commitOptions = MutableCommitOptions()
  val commitOptions: CommitOptions get() = _commitOptions.toUnmodifiableOptions()

  val commitMessagePolicy: SingleChangeListCommitMessagePolicy = SingleChangeListCommitMessagePolicy(project, initialCommitMessage)

  private val eventDispatcher = EventDispatcher.create(CommitWorkflowListener::class.java)

  fun addListener(listener: CommitWorkflowListener, parent: Disposable) = eventDispatcher.addListener(listener, parent)

  internal fun initCommitHandlers(handlers: List<CheckinHandler>) {
    _commitHandlers.clear()
    _commitHandlers += handlers
  }

  internal fun initCommitOptions(options: CommitOptions) {
    _commitOptions.clear()
    _commitOptions.add(options)
  }

  fun addUnversionedFiles(changeList: LocalChangeList, unversionedFiles: List<VirtualFile>, callback: (List<Change>) -> Unit): Boolean {
    if (unversionedFiles.isEmpty()) return true

    FileDocumentManager.getInstance().saveAllDocuments()
    return addUnversionedFilesToVcs(project, changeList, unversionedFiles, callback, null)
  }

  fun executeDefault(executor: CommitExecutor?, commitState: ChangeListCommitState) {
    val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(true, executor, commitState.changeList)
    @Suppress("NON_EXHAUSTIVE_WHEN")
    when (beforeCommitChecksResult) {
      CheckinHandler.ReturnResult.COMMIT -> DefaultNameChangeListCleaner(project, commitState).use { doCommit(commitState) }
      CheckinHandler.ReturnResult.CLOSE_WINDOW ->
        moveToFailedList(project, commitState, message("commit.dialog.rejected.commit.template", commitState.changeList.name))
    }
  }

  fun executeCustom(executor: CommitExecutor, session: CommitSession, commitState: ChangeListCommitState) {
    if (!configureCommitSession(executor, session, commitState.changes, commitState.commitMessage)) return

    val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(false, executor, commitState.changeList)
    @Suppress("NON_EXHAUSTIVE_WHEN")
    when (beforeCommitChecksResult) {
      CheckinHandler.ReturnResult.COMMIT -> {
        val success = doCommitCustom(executor, session, commitState)
        if (success) eventDispatcher.multicaster.customCommitSucceeded()
      }
      CheckinHandler.ReturnResult.CLOSE_WINDOW ->
        moveToFailedList(project, commitState, message("commit.dialog.rejected.commit.template", commitState.changeList.name))
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

  private fun runBeforeCommitChecksWithEvents(isDefaultCommit: Boolean,
                                              executor: CommitExecutor?,
                                              changeList: LocalChangeList): CheckinHandler.ReturnResult {
    eventDispatcher.multicaster.beforeCommitChecksStarted()
    val result = runBeforeCommitChecks(executor, changeList)
    eventDispatcher.multicaster.beforeCommitChecksEnded(isDefaultCommit, result)

    return result
  }

  private fun runBeforeCommitChecks(executor: CommitExecutor?, changeList: LocalChangeList): CheckinHandler.ReturnResult {
    var result: CheckinHandler.ReturnResult? = null
    val checks = Runnable {
      FileDocumentManager.getInstance().saveAllDocuments()
      result = runBeforeCommitHandlersChecks(executor)
    }

    doRunBeforeCommitChecks(changeList, wrapWithCommitMetaHandlers(checks))

    return result ?: CheckinHandler.ReturnResult.CANCEL
  }

  protected open fun doRunBeforeCommitChecks(changeList: LocalChangeList, checks: Runnable) =
    PartialChangesUtil.runUnderChangeList(project, changeList, checks)

  private fun wrapWithCommitMetaHandlers(block: Runnable): Runnable {
    var result = block
    commitHandlers.filterIsInstance<CheckinMetaHandler>().forEach { metaHandler ->
      val previousResult = result
      result = Runnable {
        LOG.debug("CheckinMetaHandler.runCheckinHandlers: $metaHandler")
        metaHandler.runCheckinHandlers(previousResult)
      }
    }
    return result
  }

  private fun runBeforeCommitHandlersChecks(executor: CommitExecutor?): CheckinHandler.ReturnResult {
    commitHandlers.asSequence().filter { it.acceptExecutor(executor) }.forEach { handler ->
      LOG.debug("CheckinHandler.beforeCheckin: $handler")

      val result = handler.beforeCheckin(executor, additionalDataConsumer)
      if (result != CheckinHandler.ReturnResult.COMMIT) return result
    }

    return CheckinHandler.ReturnResult.COMMIT
  }

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
    val committer = SingleChangeListCommitter(project, commitState, commitHandlers, additionalData, vcsToCommit, DIALOG_TITLE,
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

  companion object {
    @JvmStatic
    fun getCommitHandlerFactories(project: Project): List<BaseCheckinHandlerFactory> =
      CheckinHandlersManager.getInstance().getRegisteredCheckinHandlerFactories(ProjectLevelVcsManager.getInstance(project).allActiveVcss)

    @JvmStatic
    fun getCommitHandlers(commitPanel: CheckinProjectPanel, commitContext: CommitContext) =
      getCommitHandlerFactories(commitPanel.project)
        .map { it.createHandler(commitPanel, commitContext) }
        .filter { it != CheckinHandler.DUMMY }
  }
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