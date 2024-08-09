// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ui.SessionDialog
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CommitInfo
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.ui.CommitOptionsDialogExtension
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.forEachLoggingErrors
import com.intellij.util.containers.mapNotNullLoggingErrors
import com.intellij.util.ui.UIUtil.replaceMnemonicAmpersand
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitHandlers
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler.Companion.getActionTextWithoutEllipsis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

private val LOG = logger<AbstractCommitWorkflowHandler<*, *>>()

// Need to support '_' for mnemonics as it is supported in DialogWrapper internally
@Nls
fun String.fixUnderscoreMnemonic() = replace('_', '&')

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
  fun setCommitMessage(text: String?) {
    VcsConfiguration.getInstance(project).saveCommitMessage(text)
    ui.commitMessageUi.setText(text)
  }

  protected val commitContext get() = workflow.commitContext
  private val commitHandlers get() = workflow.commitHandlers
  protected val commitOptions get() = workflow.commitOptions

  protected open fun uiDataSnapshot(sink: DataSink) {
    sink[VcsDataKeys.COMMIT_WORKFLOW_HANDLER] = this
    sink[VcsDataKeys.COMMIT_WORKFLOW_UI] = this.ui
    sink[VcsDataKeys.COMMIT_MESSAGE_CONTROL] = this.ui.commitMessageUi as? CommitMessageI
    sink[Refreshable.PANEL_KEY] = commitPanel
  }

  protected fun initCommitHandlers() {
    var checkinHandlers = getCommitHandlers(workflow.vcses, commitPanel, commitContext)

    val executors = workflow.commitExecutors + if (workflow.isDefaultCommitEnabled) listOf(null) else emptyList()
    if (executors.isNotEmpty()) {
      checkinHandlers = checkinHandlers.filter { handler -> executors.any { executor -> handler.acceptExecutor(executor) } }
    }

    workflow.initCommitHandlers(checkinHandlers)
  }

  protected fun createCommitOptions(): CommitOptions = CommitOptionsImpl(
    if (workflow.isDefaultCommitEnabled) getVcsOptions(commitPanel, workflow.vcses, commitContext) else emptyMap(),
    getBeforeOptions(workflow.commitHandlers),
    // TODO Potential leak here for non-modal
    getAfterOptions(workflow.commitHandlers, this),
    CommitOptionsDialogExtension.EP_NAME.extensionList.flatMap { it.getOptions(project) }
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
  private suspend fun executeSession(sessionInfo: CommitSessionInfo): Boolean {
    val proceed = coroutineToIndicator {
      checkCommit(sessionInfo) &&
      WriteIntentReadAction.compute<Boolean> { saveCommitOptionsOnCommit() }
    }
    if (!proceed) return false

    saveCommitMessageBeforeCommit()
    logCommitEvent(sessionInfo)

    if (!updateWorkflow(sessionInfo)) return false

    writeIntentReadAction {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    val commitInfo = DynamicCommitInfoImpl(commitContext, sessionInfo, ui, workflow)
    return doExecuteSession(sessionInfo, commitInfo)
  }

  private fun logCommitEvent(sessionInfo: CommitSessionInfo) {
    CommitSessionCollector.getInstance(project).logCommit(sessionInfo.executor?.id,
                                                          ui.getIncludedChanges().size,
                                                          ui.getIncludedUnversionedFiles().size)
  }

  protected abstract suspend fun updateWorkflow(sessionInfo: CommitSessionInfo): Boolean

  /**
   * Check that commit can be performed with given parameters.
   */
  @RequiresEdt
  protected open fun checkCommit(sessionInfo: CommitSessionInfo): Boolean {
    return workflow.canExecute(sessionInfo, getIncludedChanges())
  }

  protected open suspend fun doExecuteSession(sessionInfo: CommitSessionInfo, commitInfo: DynamicCommitInfo): Boolean {
    return workflow.executeSession(sessionInfo, commitInfo)
  }

  protected open fun saveCommitOptionsOnCommit(): Boolean {
    commitOptions.saveState()
    return true
  }

  protected abstract fun saveCommitMessageBeforeCommit()

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
     * Commit action name, without ellipsis. Ex: 'Amend Comm&it Anyway'.
     */
    fun getActionTextWithoutEllipsis(vcses: Collection<AbstractVcs>,
                                     executor: CommitExecutor?,
                                     isAmend: Boolean,
                                     isSkipCommitChecks: Boolean,
                                     removeMnemonic: Boolean): @Nls String {
      if (executor == null) {
        val actionText = getDefaultCommitActionName(vcses, isAmend, isSkipCommitChecks)
        return cleanActionText(actionText, removeMnemonic = removeMnemonic)
      }

      if (executor is CommitExecutorWithRichDescription) {
        val state = CommitWorkflowHandlerState(isAmend, isSkipCommitChecks)
        val actionText = executor.getText(state)
        if (actionText != null) {
          return cleanActionText(actionText, removeMnemonic = removeMnemonic)
        }
      }

      // We ignore 'isAmend == true' for now - unclear how to handle without CommitExecutorWithRichDescription.
      // Ex: executor might not support this flag.
      val actionText = executor.actionText
      if (isSkipCommitChecks) {
        return VcsBundle.message("commit.checks.failed.notification.commit.anyway.action",
                                 cleanActionText(actionText, removeMnemonic = removeMnemonic))
      }
      else {
        return cleanActionText(actionText, removeMnemonic = removeMnemonic)
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

    suspend fun addUnversionedFiles(project: Project,
                                    unversionedFilePaths: Iterable<FilePath>,
                                    changeList: LocalChangeList,
                                    inclusionModel: InclusionModel): Boolean {
      val unversionedFiles = unversionedFilePaths.mapNotNull { it.virtualFile }
      if (unversionedFiles.isEmpty()) return true

      FileDocumentManager.getInstance().saveAllDocuments()
      return withContext(Dispatchers.IO) {
        blockingContext {
          ScheduleForAdditionAction.Manager.addUnversionedFilesToVcsInSync(project, changeList, unversionedFiles) { newChanges ->
            inclusionModel.addInclusion(newChanges)
          }
        }
      }
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
    get() = getActionTextWithoutEllipsis(workflow.vcses, executor, commitContext.isAmendCommitMode,
                                         isSkipCommitChecks = false, removeMnemonic = false)

  override fun asStaticInfo(): StaticCommitInfo {
    return StaticCommitInfo(commitContext, isVcsCommit, executor, commitActionText, committedChanges, affectedVcses, commitMessage)
  }
}

interface DynamicCommitInfo : CommitInfo {
  fun asStaticInfo(): StaticCommitInfo
}