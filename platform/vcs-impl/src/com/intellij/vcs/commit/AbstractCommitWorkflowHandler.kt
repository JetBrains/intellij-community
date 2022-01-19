// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.InputException
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.util.containers.forEachLoggingErrors
import com.intellij.util.containers.mapNotNullLoggingErrors
import com.intellij.util.ui.UIUtil.replaceMnemonicAmpersand
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitHandlers
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

private val LOG = logger<AbstractCommitWorkflowHandler<*, *>>()

// Need to support '_' for mnemonics as it is supported in DialogWrapper internally
@Nls
private fun String.fixUnderscoreMnemonic() = replace('_', '&')

internal fun getDefaultCommitActionName(vcses: Collection<AbstractVcs> = emptyList()): @Nls String =
  replaceMnemonicAmpersand(
    (vcses.mapNotNull { it.checkinEnvironment?.checkinOperationName }.distinct().singleOrNull()
     ?: VcsBundle.message("commit.dialog.default.commit.operation.name")
    ).fixUnderscoreMnemonic()
  )

internal fun CommitWorkflowUi.getDisplayedPaths(): List<FilePath> =
  getDisplayedChanges().map { getFilePath(it) } + getDisplayedUnversionedFiles()

internal fun CommitWorkflowUi.getIncludedPaths(): List<FilePath> =
  getIncludedChanges().map { getFilePath(it) } + getIncludedUnversionedFiles()

@get:ApiStatus.Internal
val CheckinProjectPanel.isNonModalCommit: Boolean
  get() = commitWorkflowHandler is NonModalCommitWorkflowHandler<*, *>

private val VCS_COMPARATOR = compareBy<AbstractVcs, String>(String.CASE_INSENSITIVE_ORDER) { it.keyInstanceMethod.name }

abstract class AbstractCommitWorkflowHandler<W : AbstractCommitWorkflow, U : CommitWorkflowUi> :
  CommitWorkflowHandler,
  CommitWorkflowListener,
  CommitExecutorListener,
  InclusionListener,
  Disposable {

  abstract val workflow: W
  abstract val ui: U

  protected val project get() = workflow.project

  protected abstract val commitPanel: CheckinProjectPanel

  protected fun getIncludedChanges() = ui.getIncludedChanges()
  protected fun getIncludedUnversionedFiles() = ui.getIncludedUnversionedFiles()
  open fun isCommitEmpty(): Boolean = getIncludedChanges().isEmpty() && getIncludedUnversionedFiles().isEmpty()

  fun getCommitMessage(): String = ui.commitMessageUi.text
  fun setCommitMessage(text: String?) = ui.commitMessageUi.setText(text)

  protected val commitContext get() = workflow.commitContext
  protected val commitHandlers get() = workflow.commitHandlers
  protected val commitOptions get() = workflow.commitOptions

  fun getCommitActionName() = getDefaultCommitActionName(workflow.vcses)

  open fun updateDefaultCommitActionName() {
    ui.defaultCommitActionName = getCommitActionName()
  }

  protected open fun createDataProvider() = DataProvider { dataId ->
    when {
      COMMIT_WORKFLOW_HANDLER.`is`(dataId) -> this
      Refreshable.PANEL_KEY.`is`(dataId) -> commitPanel
      else -> null
    }
  }

  protected fun initCommitHandlers() = workflow.initCommitHandlers(getCommitHandlers(workflow.vcses, commitPanel, commitContext))

  protected fun createCommitOptions(): CommitOptions = CommitOptionsImpl(
    if (workflow.isDefaultCommitEnabled) getVcsOptions(commitPanel, workflow.vcses, commitContext) else emptyMap(),
    getBeforeOptions(workflow.commitHandlers),
    // TODO Potential leak here for non-modal
    getAfterOptions(workflow.commitHandlers, this)
  )

  override fun inclusionChanged() = commitHandlers.forEachLoggingErrors(LOG) { it.includedChangesChanged() }

  override fun getExecutor(executorId: String): CommitExecutor? = workflow.commitExecutors.find { it.id == executorId }
  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = executor in workflow.commitExecutors

  override fun execute(executor: CommitExecutor) = executorCalled(executor)
  override fun executorCalled(executor: CommitExecutor?) =
    workflow.startExecution {
      val session = executor?.createCommitSession(commitContext)

      if (session == null || session === CommitSession.VCS_COMMIT) executeDefault(executor)
      else executeCustom(executor, session)
    }

  override fun executionStarted() = Unit
  override fun executionEnded() = Unit

  override fun beforeCommitChecksStarted() = ui.startBeforeCommitChecks()
  override fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult) = ui.endBeforeCommitChecks(result)

  private fun executeDefault(executor: CommitExecutor?): Boolean {
    val proceed = checkCommit(executor) &&
                  addUnversionedFiles() &&
                  saveCommitOptions()
    if (!proceed) return false

    saveCommitMessage(true)
    logCommitEvent(executor)

    refreshChanges {
      workflow.continueExecution {
        updateWorkflow()
        doExecuteDefault(executor)
      }
    }
    return true
  }

  private fun executeCustom(executor: CommitExecutor, session: CommitSession): Boolean {
    val proceed = checkCommit(executor) &&
                  canExecute(executor) &&
                  saveCommitOptions()
    if (!proceed) return false

    saveCommitMessage(true)
    logCommitEvent(executor)

    refreshChanges {
      workflow.continueExecution {
        updateWorkflow()
        doExecuteCustom(executor, session)
      }
    }
    return true
  }

  private fun logCommitEvent(executor: CommitExecutor?) {
    CommitSessionCollector.getInstance(project).logCommit(executor?.id,
                                                          ui.getIncludedChanges().size,
                                                          ui.getIncludedUnversionedFiles().size)
  }

  protected open fun updateWorkflow() = Unit

  protected abstract fun checkCommit(executor: CommitExecutor?): Boolean

  protected abstract fun addUnversionedFiles(): Boolean

  protected fun addUnversionedFiles(changeList: LocalChangeList): Boolean =
    workflow.addUnversionedFiles(changeList, getIncludedUnversionedFiles().mapNotNull { it.virtualFile }) { changes ->
      ui.includeIntoCommit(changes)
    }

  protected open fun doExecuteDefault(executor: CommitExecutor?): Boolean {
    try {
      return workflow.executeDefault(executor)
    }
    catch (e: InputException) { // TODO Looks like this catch is unnecessary - check
      e.show()
      return false
    }
  }

  private fun canExecute(executor: CommitExecutor): Boolean = workflow.canExecute(executor, getIncludedChanges())
  private fun doExecuteCustom(executor: CommitExecutor, session: CommitSession): Boolean {
    return workflow.executeCustom(executor, session)
  }

  protected open fun saveCommitOptions() = try {
    commitOptions.saveState()
    true
  }
  catch (ex: InputException) {
    ex.show()
    false
  }

  protected abstract fun saveCommitMessage(success: Boolean)

  private fun getVcsOptions(commitPanel: CheckinProjectPanel, vcses: Collection<AbstractVcs>, commitContext: CommitContext) =
    vcses.sortedWith(VCS_COMPARATOR)
      .associateWith { it.checkinEnvironment?.createCommitOptions(commitPanel, commitContext) }
      .filterValues { it != null }
      .mapValues { it.value!! }

  private fun getBeforeOptions(handlers: Collection<CheckinHandler>): List<RefreshableOnComponent> =
    handlers.mapNotNullLoggingErrors(LOG) { it.beforeCheckinConfigurationPanel }

  private fun getAfterOptions(handlers: Collection<CheckinHandler>, parent: Disposable): List<RefreshableOnComponent> =
    handlers.mapNotNullLoggingErrors(LOG) { it.getAfterCheckinConfigurationPanel(parent) }

  protected open fun refreshChanges(callback: () -> Unit) =
    ChangeListManager.getInstance(project).invokeAfterUpdateWithModal(true, VcsBundle.message("commit.progress.title")) {
      ui.refreshData()
      callback()
    }

  override fun dispose() = Unit
}