// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.InputException
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.util.ui.UIUtil.replaceMnemonicAmpersand
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitHandlers
import com.intellij.vcsUtil.VcsUtil.getFilePath

// Need to support '_' for mnemonics as it is supported in DialogWrapper internally
private fun String.fixUnderscoreMnemonic() = replace('_', '&')

internal fun getDefaultCommitActionName(vcses: Collection<AbstractVcs<*>> = emptyList()): String =
  replaceMnemonicAmpersand(
    (vcses.mapNotNull { it.checkinEnvironment?.checkinOperationName }.distinct().singleOrNull()
     ?: VcsBundle.getString("commit.dialog.default.commit.operation.name")
    ).fixUnderscoreMnemonic()
  )

internal fun CommitWorkflowUi.getDisplayedPaths(): List<FilePath> =
  getDisplayedChanges().map { getFilePath(it) } + getDisplayedUnversionedFiles().map { getFilePath(it) }

internal fun CommitWorkflowUi.getIncludedPaths(): List<FilePath> =
  getIncludedChanges().map { getFilePath(it) } + getIncludedUnversionedFiles().map { getFilePath(it) }

internal val CheckinProjectPanel.isNonModalCommit: Boolean get() = commitWorkflowHandler is ChangesViewCommitWorkflowHandler

private val VCS_COMPARATOR = compareBy<AbstractVcs<*>, String>(String.CASE_INSENSITIVE_ORDER) { it.keyInstanceMethod.name }

abstract class AbstractCommitWorkflowHandler<W : AbstractCommitWorkflow, U : CommitWorkflowUi> :
  CommitWorkflowHandler,
  CommitWorkflowListener,
  CommitExecutorListener,
  InclusionListener,
  Disposable {

  abstract val workflow: W
  abstract val ui: U

  protected val project get() = workflow.project
  private val vcsConfiguration get() = VcsConfiguration.getInstance(project)

  protected abstract val commitPanel: CheckinProjectPanel

  protected fun getIncludedChanges() = ui.getIncludedChanges()
  protected fun getIncludedUnversionedFiles() = ui.getIncludedUnversionedFiles()
  internal fun isCommitEmpty(): Boolean = getIncludedChanges().isEmpty() && getIncludedUnversionedFiles().isEmpty()

  fun getCommitMessage(): String = ui.commitMessageUi.text
  fun setCommitMessage(text: String?) = ui.commitMessageUi.setText(text)

  protected val commitContext get() = workflow.commitContext
  protected val commitHandlers get() = workflow.commitHandlers
  protected val commitOptions get() = workflow.commitOptions

  protected fun createDataProvider() = DataProvider { dataId ->
    when {
      COMMIT_WORKFLOW_HANDLER.`is`(dataId) -> this
      Refreshable.PANEL_KEY.`is`(dataId) -> commitPanel
      else -> null
    }
  }

  protected fun initCommitHandlers() = workflow.initCommitHandlers(getCommitHandlers(commitPanel, commitContext))

  protected fun createCommitOptions(): CommitOptions = CommitOptionsImpl(
    if (workflow.isDefaultCommitEnabled) getVcsOptions(commitPanel, workflow.vcses, commitContext) else emptyMap(),
    getBeforeOptions(workflow.commitHandlers),
    // TODO Potential leak here for non-modal
    getAfterOptions(workflow.commitHandlers, this)
  )

  override fun inclusionChanged() = commitHandlers.forEach { it.includedChangesChanged() }

  override fun executorCalled(executor: CommitExecutor?) = executor?.let { execute(it) } ?: executeDefault(null)

  override fun getExecutor(executorId: String): CommitExecutor? = workflow.commitExecutors.find { it.id == executorId }
  override fun isExecutorEnabled(executor: CommitExecutor): Boolean =
    executor in workflow.commitExecutors && (!isCommitEmpty() || (executor is CommitExecutorBase && !executor.areChangesRequired()))
  override fun execute(executor: CommitExecutor) {
    val session = executor.createCommitSession(commitContext)

    if (session === CommitSession.VCS_COMMIT) {
      executeDefault(executor)
    }
    else {
      executeCustom(executor, session)
    }
  }

  override fun beforeCommitChecksStarted() = ui.startBeforeCommitChecks()
  override fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult) = ui.endBeforeCommitChecks(result)

  private fun executeDefault(executor: CommitExecutor?) {
    if (!addUnversionedFiles()) return
    if (!checkEmptyCommitMessage()) return
    if (!saveCommitOptions()) return
    saveCommitMessage(true)

    refreshChanges {
      updateWorkflow()
      doExecuteDefault(executor)
    }
  }

  private fun executeCustom(executor: CommitExecutor, session: CommitSession) {
    if (!canExecute(executor)) return
    if (!checkEmptyCommitMessage()) return
    if (!saveCommitOptions()) return
    saveCommitMessage(true)

    refreshChanges {
      updateWorkflow()
      doExecuteCustom(executor, session)
    }
  }

  protected open fun updateWorkflow() = Unit

  protected abstract fun addUnversionedFiles(): Boolean

  protected fun addUnversionedFiles(changeList: LocalChangeList): Boolean =
    workflow.addUnversionedFiles(changeList, getIncludedUnversionedFiles()) { changes -> ui.includeIntoCommit(changes) }

  private fun doExecuteDefault(executor: CommitExecutor?) = try {
    workflow.executeDefault(executor)
  }
  catch (e: InputException) { // TODO Looks like this catch is unnecessary - check
    e.show()
  }

  private fun canExecute(executor: CommitExecutor): Boolean = workflow.canExecute(executor, getIncludedChanges())
  private fun doExecuteCustom(executor: CommitExecutor, session: CommitSession) = workflow.executeCustom(executor, session)

  private fun checkEmptyCommitMessage(): Boolean =
    getCommitMessage().isNotEmpty() || !vcsConfiguration.FORCE_NON_EMPTY_COMMENT || ui.confirmCommitWithEmptyMessage()

  protected open fun saveCommitOptions() = try {
    commitOptions.saveState()
    true
  }
  catch (ex: InputException) {
    ex.show()
    false
  }

  protected abstract fun saveCommitMessage(success: Boolean)

  private fun getVcsOptions(commitPanel: CheckinProjectPanel, vcses: Collection<AbstractVcs<*>>, commitContext: CommitContext) =
    vcses.sortedWith(VCS_COMPARATOR)
      .associateWith { it.checkinEnvironment?.createCommitOptions(commitPanel, commitContext) }
      .filterValues { it != null }
      .mapValues { it.value!! }

  private fun getBeforeOptions(handlers: Collection<CheckinHandler>) = handlers.mapNotNull { it.beforeCheckinConfigurationPanel }

  private fun getAfterOptions(handlers: Collection<CheckinHandler>, parent: Disposable) =
    handlers.mapNotNull { it.getAfterCheckinConfigurationPanel(parent) }

  protected fun refreshChanges(callback: () -> Unit) =
    ChangeListManager.getInstance(project).invokeAfterUpdate(
      {
        ui.refreshData()
        callback()
      },
      InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, "Commit", ModalityState.current()
    )

  override fun dispose() = Unit
}