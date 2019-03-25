// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.CommonBundle.getCancelButtonText
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getWarningIcon
import com.intellij.openapi.ui.Messages.showYesNoDialog
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction.addUnversionedFilesToVcs
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.DIALOG_TITLE
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.getExecutorPresentableText
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

interface CommitWorkflowListener : EventListener {
  fun beforeCommitChecksStarted()
  fun beforeCommitChecksEnded(result: CheckinHandler.ReturnResult)
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
                                val resultHandler: CommitResultHandler? = null) {
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

  fun executeDefault(executor: CommitExecutor?, changeList: LocalChangeList, changes: List<Change>, commitMessage: String) {
    val beforeCommitChecksResult = runBeforeCommitChecksWithEvents(executor, changeList)
    @Suppress("NON_EXHAUSTIVE_WHEN")
    when (beforeCommitChecksResult) {
      CheckinHandler.ReturnResult.COMMIT -> DefaultNameChangeListCleaner(project, changeList, changes).use {
        doCommit(changeList, changes, commitMessage)
      }
      CheckinHandler.ReturnResult.CLOSE_WINDOW ->
        moveToFailedList(project, changeList, commitMessage, changes, message("commit.dialog.rejected.commit.template", changeList.name))
    }
  }

  private fun runBeforeCommitChecksWithEvents(executor: CommitExecutor?, changeList: LocalChangeList): CheckinHandler.ReturnResult {
    eventDispatcher.multicaster.beforeCommitChecksStarted()
    val result = runBeforeCommitChecks(executor, changeList)
    eventDispatcher.multicaster.beforeCommitChecksEnded(result)

    return result
  }

  fun runBeforeCommitChecks(executor: CommitExecutor?, changeList: LocalChangeList): CheckinHandler.ReturnResult {
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
          project, message("commit.dialog.partial.commit.warning.body", getExecutorPresentableText(executor)),
          message("commit.dialog.partial.commit.warning.title"), executor.actionText, getCancelButtonText(), getWarningIcon())
      }
    }
    return true
  }

  protected open fun doCommit(changeList: LocalChangeList, changes: List<Change>, commitMessage: String) {
    LOG.debug("Do actual commit")
    val committer = SingleChangeListCommitter(project, changeList, changes, commitMessage, commitHandlers, additionalData, vcsToCommit,
                                              DIALOG_TITLE, isDefaultChangeListFullyIncluded)

    committer.addResultHandler(resultHandler ?: DefaultCommitResultHandler(committer))
    committer.runCommit(DIALOG_TITLE, false)
  }

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

private class DefaultNameChangeListCleaner(val project: Project, changeList: LocalChangeList, changes: List<Change>) {
  private val isChangeListFullyIncluded = changeList.changes.size == changes.size
  private val isDefaultNameChangeList = changeList.hasDefaultName()

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