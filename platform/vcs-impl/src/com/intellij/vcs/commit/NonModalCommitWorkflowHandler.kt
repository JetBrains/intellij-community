// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil.*
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitExecutorWithRichDescription
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.containers.nullize
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitExecutors
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nls
import java.lang.Runnable
import kotlin.properties.Delegates.observable

private val LOG = logger<NonModalCommitWorkflowHandler<*, *>>()

abstract class NonModalCommitWorkflowHandler<W : NonModalCommitWorkflow, U : NonModalCommitWorkflowUi> :
  AbstractCommitWorkflowHandler<W, U>() {

  abstract override val amendCommitHandler: NonModalAmendCommitHandler

  private var areCommitOptionsCreated = false

  private val coroutineScope =
    CoroutineScope(CoroutineName("commit workflow") + Dispatchers.EDT + SupervisorJob())

  private var isCommitChecksResultUpToDate: RecentCommitChecks by observable(RecentCommitChecks.UNKNOWN) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    updateDefaultCommitActionName()
  }

  private val checkinErrorNotifications = SingletonNotificationManager(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.displayId,
                                                                       NotificationType.ERROR)

  protected fun setupCommitHandlersTracking() {
    CheckinHandlerFactory.EP_NAME.addChangeListener(Runnable { commitHandlersChanged() }, this)
    VcsCheckinHandlerFactory.EP_NAME.addChangeListener(Runnable { commitHandlersChanged() }, this)
  }

  private fun commitHandlersChanged() {
    if (workflow.isExecuting) return

    commitOptions.saveState()
    disposeCommitOptions()

    initCommitHandlers()
  }

  override fun vcsesChanged() {
    initCommitHandlers()
    workflow.initCommitExecutors(getCommitExecutors(project, workflow.vcses) + RunCommitChecksExecutor)

    updateDefaultCommitActionEnabled()
    updateDefaultCommitActionName()
    ui.setPrimaryCommitActions(createPrimaryCommitActions())
    ui.setCustomCommitActions(createCommitExecutorActions())
  }

  protected fun setupDumbModeTracking() {
    if (isDumb(project)) ui.commitProgressUi.isDumbMode = true
    project.messageBus.connect(this).subscribe(DumbService.DUMB_MODE, object : DumbModeListener {
      override fun enteredDumbMode() {
        ui.commitProgressUi.isDumbMode = true
      }

      override fun exitDumbMode() {
        ui.commitProgressUi.isDumbMode = false
      }
    })
  }

  override fun executionStarted() = updateDefaultCommitActionEnabled()
  override fun executionEnded() = updateDefaultCommitActionEnabled()

  override fun updateDefaultCommitActionName() {
    val isAmend = amendCommitHandler.isAmendCommitMode
    val isSkipCommitChecks = isCommitChecksResultUpToDate == RecentCommitChecks.FAILED
    ui.defaultCommitActionName = getCommitActionName(isAmend, isSkipCommitChecks)
  }

  private fun getCommitActionName(isAmend: Boolean, isSkipCommitChecks: Boolean): @Nls String {
    val commitText = getCommitActionName()
    return when {
      isAmend && isSkipCommitChecks -> message("action.amend.commit.anyway.text")
      isAmend && !isSkipCommitChecks -> message("amend.action.name", commitText)
      !isAmend && isSkipCommitChecks -> message("action.commit.anyway.text", commitText)
      else -> commitText
    }
  }

  private fun getActionTextWithoutEllipsis(executor: CommitExecutor?,
                                           isAmend: Boolean,
                                           isSkipCommitChecks: Boolean): @Nls String {
    if (executor == null) {
      val actionText = getCommitActionName(isAmend, isSkipCommitChecks)
      return removeEllipsisSuffix(actionText)
    }

    if (executor is CommitExecutorWithRichDescription) {
      val state = CommitWorkflowHandlerState(isAmend, isSkipCommitChecks)
      val actionText = executor.getText(state)
      if (actionText != null) {
        return removeEllipsisSuffix(actionText)
      }
    }

    // We ignore 'isAmend == true' for now - unclear how to handle without CommitExecutorWithRichDescription.
    // Ex: executor might not support this flag.
    val actionText = executor.actionText
    if (isSkipCommitChecks) {
      return message("commit.checks.failed.notification.commit.anyway.action", removeEllipsisSuffix(actionText))
    }
    else {
      return removeEllipsisSuffix(actionText)
    }
  }

  private fun getCommitActionTextForNotification(
    executor: CommitExecutor?,
    isSkipCommitChecks: Boolean
  ): @Nls(capitalization = Nls.Capitalization.Sentence) String {
    val isAmend = amendCommitHandler.isAmendCommitMode
    val actionText: @Nls String = getActionTextWithoutEllipsis(executor, isAmend, isSkipCommitChecks)
    return capitalize(toLowerCase(actionText))
  }

  fun updateDefaultCommitActionEnabled() {
    ui.isDefaultCommitActionEnabled = isReady()
  }

  protected open fun isReady() = workflow.vcses.isNotEmpty() && !workflow.isExecuting && !amendCommitHandler.isLoading

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = super.isExecutorEnabled(executor) && isReady()

  private fun createPrimaryCommitActions(): List<AnAction> {
    val group = ActionManager.getInstance().getAction(VcsActions.PRIMARY_COMMIT_EXECUTORS_GROUP) as ActionGroup
    return group.getChildren(null).toList()
  }

  private fun createCommitExecutorActions(): List<AnAction> {
    val group = ActionManager.getInstance().getAction(VcsActions.COMMIT_EXECUTORS_GROUP) as ActionGroup
    val executors = workflow.commitExecutors.filter { it.useDefaultAction() }
    return group.getChildren(null).toList() +
           executors.map { DefaultCommitExecutorAction(it) }
  }

  override fun checkCommit(sessionInfo: CommitSessionInfo): Boolean {
    val superCheckResult = super.checkCommit(sessionInfo)
    val executorWithoutChangesAllowed = sessionInfo.executor?.areChangesRequired() == false
    ui.commitProgressUi.isEmptyChanges = !amendCommitHandler.isAmendWithoutChangesAllowed() && !executorWithoutChangesAllowed && isCommitEmpty()
    ui.commitProgressUi.isEmptyMessage = getCommitMessage().isBlank()
    return superCheckResult &&
           !ui.commitProgressUi.isEmptyChanges &&
           !ui.commitProgressUi.isEmptyMessage
  }

  /**
   * Subscribe to VFS and documents changes to reset commit checks results
   */
  protected fun setupCommitChecksResultTracking() {
    fun areFilesAffectsCommitChecksResult(files: Collection<VirtualFile>): Boolean {
      val vcsManager = ProjectLevelVcsManager.getInstance(project)
      val filesFromVcs = files.filter { vcsManager.getVcsFor(it) != null }.nullize() ?: return false

      val changeListManager = ChangeListManager.getInstance(project)
      val fileIndex = ProjectRootManagerEx.getInstanceEx(project).fileIndex
      return filesFromVcs.any {
        fileIndex.isInContent(it) && changeListManager.getStatus(it) != FileStatus.IGNORED
      }
    }

    // reset commit checks on VFS updates
    project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        if (isCommitChecksResultUpToDate == RecentCommitChecks.UNKNOWN) {
          return
        }
        val updatedFiles = events.mapNotNull { it.file }
        if (areFilesAffectsCommitChecksResult(updatedFiles)) {
          resetCommitChecksResult()
        }
      }
    })

    // reset commit checks on documents modification (e.g. user typed in the editor)
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (isCommitChecksResultUpToDate == RecentCommitChecks.UNKNOWN) {
          return
        }
        val file = FileDocumentManager.getInstance().getFile(event.document)
        if (file != null && areFilesAffectsCommitChecksResult(listOf(file))) {
          resetCommitChecksResult()
        }
      }
    }, this)
  }

  protected fun resetCommitChecksResult() {
    isCommitChecksResultUpToDate = RecentCommitChecks.UNKNOWN
    hideCommitChecksFailureNotification()
  }

  override fun beforeCommitChecksStarted(sessionInfo: CommitSessionInfo) {
    super.beforeCommitChecksStarted(sessionInfo)
    hideCommitChecksFailureNotification()
  }

  override fun beforeCommitChecksEnded(sessionInfo: CommitSessionInfo, result: CommitChecksResult) {
    hideCommitChecksFailureNotification()
    super.beforeCommitChecksEnded(sessionInfo, result)
    if (result.shouldCommit) {
      ui.commitProgressUi.clearCommitCheckFailures()
    }

    if (result is CommitChecksResult.Failed ||
        result is CommitChecksResult.ExecutionError) {
      val executor = sessionInfo.executor
      val commitActionText = getCommitActionTextForNotification(executor, false)
      val commitAnywayActionText = getCommitActionTextForNotification(executor, true)
      val title = message("commit.checks.failed.notification.title", commitActionText)
      val description = getCommitCheckFailureDescription()
      checkinErrorNotifications.notify(title, description, project) {
        it.setDisplayId(VcsNotificationIdsHolder.COMMIT_CHECKS_FAILED)
        it.addAction(
          NotificationAction.createExpiring(commitAnywayActionText) { _, _ ->
            if (!workflow.isExecuting) {
              executorCalled(executor)
            }
          })
        it.addAction(createShowDetailsNotificationAction())
      }
    }

    if (result is CommitChecksResult.OnlyChecks && !result.checksPassed) {
      val commitActionText = getCommitActionTextForNotification(null, false)
      val title = message("commit.checks.failed.notification.title", commitActionText)
      val description = getCommitCheckFailureDescription()
      checkinErrorNotifications.notify(title, description, project) {
        it.setDisplayId(VcsNotificationIdsHolder.COMMIT_CHECKS_ONLY_FAILED)
        it.addAction(createShowDetailsNotificationAction())
      }
    }
  }

  private fun getCommitCheckFailureDescription(): @NlsContexts.NotificationContent String {
    return ui.commitProgressUi.getCommitCheckFailures().mapNotNull { it.text }.joinToString()
  }

  private fun createShowDetailsNotificationAction(): NotificationAction {
    return NotificationAction.create(message("commit.checks.failed.notification.show.details.action")) { _, _ ->
      val detailsViewer = ui.commitProgressUi.getCommitCheckFailures().mapNotNull { it.detailsViewer }.singleOrNull()
      if (detailsViewer != null) {
        detailsViewer()
      }
      else {
        val toolWindow = ChangesViewContentManager.getToolWindowFor(project, LOCAL_CHANGES)
        toolWindow?.activate {
          ChangesViewContentManager.getInstance(project).selectContent(LOCAL_CHANGES)
        }
      }
    }
  }

  override fun doExecuteSession(sessionInfo: CommitSessionInfo): Boolean {
    val isAmend = amendCommitHandler.isAmendCommitMode
    val actionName = getActionTextWithoutEllipsis(sessionInfo.executor, isAmend, false)
    val commitInfo = CommitInfoImpl(actionName)

    if (!sessionInfo.isVcsCommit) {
      return workflow.executeSession(sessionInfo, commitInfo)
    }

    workflow.asyncSession(coroutineScope, sessionInfo) {
      val isOnlyRunCommitChecks = commitContext.isOnlyRunCommitChecks
      commitContext.isOnlyRunCommitChecks = false

      val isSkipCommitChecks = isCommitChecksResultUpToDate == RecentCommitChecks.FAILED
      if (isSkipCommitChecks && !isOnlyRunCommitChecks) {
        return@asyncSession CommitChecksResult.Passed
      }

      ui.commitProgressUi.runWithProgress(isOnlyRunCommitChecks) {
        val problem = workflow.runBackgroundBeforeCommitChecks(sessionInfo)
        handleCommitProblem(problem, isOnlyRunCommitChecks, commitInfo)
      }
    }

    return true
  }

  private fun handleCommitProblem(problem: CommitProblem?,
                                  isOnlyRunCommitChecks: Boolean,
                                  commitInfo: CommitInfo): CommitChecksResult {
    if (problem != null) {
      val checkFailure = when (problem) {
        is UnknownCommitProblem -> CommitCheckFailure(null, null)
        is CommitProblemWithDetails -> CommitCheckFailure(problem.text) { problem.showDetails(project, commitInfo) }
        else -> CommitCheckFailure(problem.text, null)
      }
      ui.commitProgressUi.addCommitCheckFailure(checkFailure)
    }

    val checksPassed = problem == null
    when {
      isOnlyRunCommitChecks -> {
        if (checksPassed) {
          isCommitChecksResultUpToDate = RecentCommitChecks.PASSED
        }
        else {
          isCommitChecksResultUpToDate = RecentCommitChecks.FAILED
        }
        return CommitChecksResult.OnlyChecks(checksPassed)
      }
      checksPassed -> {
        isCommitChecksResultUpToDate = RecentCommitChecks.UNKNOWN // We are going to commit, remembering the result is not needed.
        return CommitChecksResult.Passed
      }
      else -> {
        isCommitChecksResultUpToDate = RecentCommitChecks.FAILED
        return CommitChecksResult.Failed()
      }
    }
  }

  override fun dispose() {
    hideCommitChecksFailureNotification()
    coroutineScope.cancel()
    super.dispose()
  }

  fun hideCommitChecksFailureNotification() {
    checkinErrorNotifications.clear()
  }

  fun showCommitOptions(isFromToolbar: Boolean, dataContext: DataContext) =
    ui.showCommitOptions(ensureCommitOptions(), getCommitActionName(), isFromToolbar, dataContext)

  override fun saveCommitOptionsOnCommit(): Boolean {
    ensureCommitOptions()
    // restore state in case settings were changed via configurable
    commitOptions.allOptions
      .filter { it is UnnamedConfigurable }
      .forEach { it.restoreState() }
    return super.saveCommitOptionsOnCommit()
  }

  protected fun ensureCommitOptions(): CommitOptions {
    if (!areCommitOptionsCreated) {
      areCommitOptionsCreated = true

      workflow.initCommitOptions(createCommitOptions())
      commitOptions.restoreState()

      commitOptionsCreated()
    }
    return commitOptions
  }

  protected open fun commitOptionsCreated() = Unit

  protected fun disposeCommitOptions() {
    workflow.disposeCommitOptions()
    areCommitOptionsCreated = false
  }

  override fun getState(): CommitWorkflowHandlerState {
    val isAmend = amendCommitHandler.isAmendCommitMode
    val isSkipCommitChecks = isCommitChecksResultUpToDate == RecentCommitChecks.FAILED
    return CommitWorkflowHandlerState(isAmend, isSkipCommitChecks)
  }

  protected open inner class CommitStateCleaner : CommitterResultHandler {
    override fun onSuccess() = resetState()
    override fun onCancel() = Unit
    override fun onFailure() = resetState()

    protected open fun resetState() {
      disposeCommitOptions()

      workflow.clearCommitContext()
      initCommitHandlers()

      resetCommitChecksResult()
      updateDefaultCommitActionName()
    }
  }
}

private enum class RecentCommitChecks { UNKNOWN, PASSED, FAILED }
