// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ui.SessionDialog
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CommitInfo
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.forEachLoggingErrors
import com.intellij.util.containers.mapNotNullLoggingErrors
import com.intellij.util.ui.UIUtil.replaceMnemonicAmpersand
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitHandlers
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler.Companion.getActionTextWithoutEllipsis
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

private val LOG = logger<AbstractCommitWorkflowHandler<*, *>>()

// Need to support '_' for mnemonics as it is supported in DialogWrapper internally
@Nls
private fun String.fixUnderscoreMnemonic() = replace('_', '&')

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
  private val commitHandlers get() = workflow.commitHandlers
  protected val commitOptions get() = workflow.commitOptions

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

  abstract fun updateDefaultCommitActionName()

  override fun inclusionChanged() = commitHandlers.forEachLoggingErrors(LOG) { it.includedChangesChanged() }

  override fun getExecutor(executorId: String): CommitExecutor? = workflow.commitExecutors.find { it.id == executorId }
  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = executor in workflow.commitExecutors

  override fun execute(executor: CommitExecutor) = executorCalled(executor)
  override fun executorCalled(executor: CommitExecutor?) =
    workflow.startExecution {
      val sessionInfo = if (executor != null) {
        val session = executor.createCommitSession(commitContext)
        CommitSessionInfo.Custom(executor, session)
      }
      else {
        CommitSessionInfo.Default
      }

      executeSession(sessionInfo)
    }

  override fun beforeCommitChecksStarted(sessionInfo: CommitSessionInfo) = ui.startBeforeCommitChecks()
  override fun beforeCommitChecksEnded(sessionInfo: CommitSessionInfo, result: CommitChecksResult) = ui.endBeforeCommitChecks(result)

  @RequiresEdt
  private fun executeSession(sessionInfo: CommitSessionInfo): Boolean {
    val proceed = checkCommit(sessionInfo) &&
                  prepareForCommitExecution(sessionInfo) &&
                  saveCommitOptionsOnCommit()
    if (!proceed) return false

    saveCommitMessage(true)
    logCommitEvent(sessionInfo)

    refreshChanges {
      invokeLater(ui.modalityState) {
        val commitInfo = DynamicCommitInfoImpl(commitContext, sessionInfo, ui, workflow)
        workflow.continueExecution {
          updateWorkflow(sessionInfo) &&
          doExecuteSession(sessionInfo, commitInfo)
        }
      }
    }
    return true
  }

  private fun logCommitEvent(sessionInfo: CommitSessionInfo) {
    CommitSessionCollector.getInstance(project).logCommit(sessionInfo.executor?.id,
                                                          ui.getIncludedChanges().size,
                                                          ui.getIncludedUnversionedFiles().size)
  }

  protected abstract fun updateWorkflow(sessionInfo: CommitSessionInfo): Boolean

  /**
   * Check that commit can be performed with given parameters.
   */
  @RequiresEdt
  protected open fun checkCommit(sessionInfo: CommitSessionInfo): Boolean {
    return workflow.canExecute(sessionInfo, getIncludedChanges())
  }

  /**
   * Prepare for the commit operation. Ex: add selected unversioned files into VCS.
   *
   * @return false if commit operation should be cancelled.
   */
  @RequiresEdt
  protected open fun prepareForCommitExecution(sessionInfo: CommitSessionInfo): Boolean {
    FileDocumentManager.getInstance().saveAllDocuments()
    return true
  }

  protected open fun doExecuteSession(sessionInfo: CommitSessionInfo, commitInfo: DynamicCommitInfo): Boolean {
    return workflow.executeSession(sessionInfo, commitInfo)
  }

  protected open fun saveCommitOptionsOnCommit(): Boolean {
    commitOptions.saveState()
    return true
  }

  protected abstract fun saveCommitMessage(success: Boolean)

  private fun getVcsOptions(commitPanel: CheckinProjectPanel,
                            vcses: Collection<AbstractVcs>,
                            commitContext: CommitContext): Map<AbstractVcs, RefreshableOnComponent> =
    vcses.sortedWith(VCS_COMPARATOR)
      .mapNotNull { vcs ->
        val optionsPanel = vcs.checkinEnvironment?.createCommitOptions(commitPanel, commitContext) ?: return@mapNotNull null
        Pair(vcs, optionsPanel)
      }
      .toMap()

  private fun getBeforeOptions(handlers: Collection<CheckinHandler>): List<RefreshableOnComponent> =
    handlers.mapNotNullLoggingErrors(LOG) { it.beforeCheckinConfigurationPanel }

  private fun getAfterOptions(handlers: Collection<CheckinHandler>, parent: Disposable): List<RefreshableOnComponent> =
    handlers.mapNotNullLoggingErrors(LOG) { it.getAfterCheckinConfigurationPanel(parent) }

  protected open fun refreshChanges(callback: () -> Unit) =
    ChangeListManager.getInstance(project).invokeAfterUpdateWithModal(true, VcsBundle.message("commit.progress.title")) {
      ui.refreshData().then {
        callback()
      }
    }

  override fun dispose() = Unit

  companion object {
    fun getDefaultCommitActionName(vcses: Collection<AbstractVcs>): @Nls String = getDefaultCommitActionName(vcses, false, false)

    fun getDefaultCommitActionName(vcses: Collection<AbstractVcs>, isAmend: Boolean, isSkipCommitChecks: Boolean): @Nls String {
      val actionName = vcses.mapNotNull { it.checkinEnvironment?.checkinOperationName }.distinct().singleOrNull()
                       ?: VcsBundle.message("commit.dialog.default.commit.operation.name")
      val commitText = replaceMnemonicAmpersand(actionName.fixUnderscoreMnemonic())

      return when {
        isAmend && isSkipCommitChecks -> VcsBundle.message("action.amend.commit.anyway.text")
        isAmend && !isSkipCommitChecks -> VcsBundle.message("amend.action.name", commitText)
        !isAmend && isSkipCommitChecks -> VcsBundle.message("action.commit.anyway.text", commitText)
        else -> commitText
      }
    }

    /**
     * Commit action name, without mnemonics and ellipsis. Ex: 'Amend Commit Anyway'.
     */
    fun getActionTextWithoutEllipsis(vcses: Collection<AbstractVcs>,
                                     executor: CommitExecutor?,
                                     isAmend: Boolean,
                                     isSkipCommitChecks: Boolean): @Nls String {
      if (executor == null) {
        val actionText = getDefaultCommitActionName(vcses, isAmend, isSkipCommitChecks)
        return cleanActionText(actionText)
      }

      if (executor is CommitExecutorWithRichDescription) {
        val state = CommitWorkflowHandlerState(isAmend, isSkipCommitChecks)
        val actionText = executor.getText(state)
        if (actionText != null) {
          return cleanActionText(actionText)
        }
      }

      // We ignore 'isAmend == true' for now - unclear how to handle without CommitExecutorWithRichDescription.
      // Ex: executor might not support this flag.
      val actionText = executor.actionText
      if (isSkipCommitChecks) {
        return VcsBundle.message("commit.checks.failed.notification.commit.anyway.action", cleanActionText(actionText))
      }
      else {
        return cleanActionText(actionText)
      }
    }

    fun configureCommitSession(project: Project,
                               sessionInfo: CommitSessionInfo,
                               changes: List<Change>,
                               commitMessage: String): Boolean {
      if (sessionInfo is CommitSessionInfo.Custom) {
        val title = cleanActionText(sessionInfo.executor.actionText)
        return SessionDialog.configureCommitSession(project, title, sessionInfo.session, changes, commitMessage)
      }
      return true
    }

    fun addUnversionedFiles(project: Project,
                            unversionedFilePaths: Iterable<FilePath>,
                            changeList: LocalChangeList,
                            inclusionModel: InclusionModel): Boolean {
      val unversionedFiles = unversionedFilePaths.mapNotNull { it.virtualFile }
      if (unversionedFiles.isEmpty()) return true

      FileDocumentManager.getInstance().saveAllDocuments()
      return ScheduleForAdditionAction.addUnversionedFilesToVcs(project, changeList, unversionedFiles,
                                                                { newChanges -> inclusionModel.addInclusion(newChanges) }, null)
    }
  }
}

class StaticCommitInfo(
  override val commitContext: CommitContext,
  override val isVcsCommit: Boolean,
  override val executor: CommitExecutor?,
  override val commitActionText: String,
  override val committedChanges: List<Change>,
  override val affectedVcses: List<AbstractVcs>,
  override val commitMessage: String,
) : CommitInfo

class DynamicCommitInfoImpl(
  override val commitContext: CommitContext,
  private val sessionInfo: CommitSessionInfo,
  private val workflowUi: CommitWorkflowUi,
  private val workflow: AbstractCommitWorkflow
) : DynamicCommitInfo {
  override val isVcsCommit: Boolean = sessionInfo.isVcsCommit
  override val executor: CommitExecutor? get() = sessionInfo.executor

  override val committedChanges: List<Change>
    get() {
      val changes = workflowUi.getIncludedChanges()
      val executor = sessionInfo.executor
      if (executor != null && !executor.supportsPartialCommit()) {
        return changes
      }
      else {
        return PartialChangesUtil.wrapPartialChanges(workflow.project, changes)
      }
    }

  override val affectedVcses: List<AbstractVcs> get() = workflow.vcses.toList()
  override val commitMessage: String get() = workflowUi.commitMessageUi.text

  override val commitActionText: String
    get() = getActionTextWithoutEllipsis(workflow.vcses, executor, commitContext.isAmendCommitMode, false)

  override fun asStaticInfo(): StaticCommitInfo {
    return StaticCommitInfo(commitContext, isVcsCommit, executor, commitActionText, committedChanges, affectedVcses, commitMessage)
  }
}

interface DynamicCommitInfo : CommitInfo {
  fun asStaticInfo(): StaticCommitInfo
}