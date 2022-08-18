// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.text.StringUtil.*
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitExecutorWithRichDescription
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CheckinMetaHandler
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.openapi.vcs.impl.PartialChangesUtil
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

private val isBackgroundCommitChecksValue: RegistryValue get() = Registry.get("vcs.background.commit.checks")
fun isBackgroundCommitChecks(): Boolean = isBackgroundCommitChecksValue.asBoolean()

abstract class NonModalCommitWorkflowHandler<W : NonModalCommitWorkflow, U : NonModalCommitWorkflowUi> :
  AbstractCommitWorkflowHandler<W, U>(),
  DumbService.DumbModeListener {

  abstract override val amendCommitHandler: NonModalAmendCommitHandler

  private var areCommitOptionsCreated = false

  private val uiDispatcher = AppUIExecutor.onUiThread().coroutineDispatchingContext()
  private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
    if (exception !is ProcessCanceledException) LOG.error(exception)
  }
  private val coroutineScope =
    CoroutineScope(CoroutineName("commit workflow") + uiDispatcher + SupervisorJob() + exceptionHandler)

  private var isCommitChecksResultUpToDate: Boolean by observable(false) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable
    updateDefaultCommitActionName()
  }

  private val checkinErrorNotifications = SingletonNotificationManager(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.displayId,
                                                                       NotificationType.ERROR)

  protected fun setupCommitHandlersTracking() {
    isBackgroundCommitChecksValue.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = commitHandlersChanged()
    }, this)
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
    if (isDumb(project)) enteredDumbMode()
    project.messageBus.connect(this).subscribe(DumbService.DUMB_MODE, this)
  }

  override fun enteredDumbMode() {
    ui.commitProgressUi.isDumbMode = true
  }

  override fun exitDumbMode() {
    ui.commitProgressUi.isDumbMode = false
  }

  override fun executionStarted() = updateDefaultCommitActionEnabled()
  override fun executionEnded() = updateDefaultCommitActionEnabled()

  override fun updateDefaultCommitActionName() {
    val commitText = getCommitActionName()
    val isAmend = amendCommitHandler.isAmendCommitMode
    val isSkipCommitChecks = isSkipCommitChecks()

    ui.defaultCommitActionName = when {
      isAmend && isSkipCommitChecks -> message("action.amend.commit.anyway.text")
      isAmend && !isSkipCommitChecks -> message("amend.action.name", commitText)
      !isAmend && isSkipCommitChecks -> message("action.commit.anyway.text", commitText)
      else -> commitText
    }
  }

  private fun getCommitActionTextForNotification(
    executor: CommitExecutor?,
    isSkipCommitChecks: Boolean
  ): @Nls(capitalization = Nls.Capitalization.Sentence) String {
    if (executor is CommitExecutorWithRichDescription) {
      val isAmend = amendCommitHandler.isAmendCommitMode
      val state = CommitWorkflowHandlerState(isAmend, isSkipCommitChecks)
      val actionText = executor.getText(state)
      if (actionText != null) {
        val notificationText = removeEllipsisSuffix(actionText)
        return capitalize(toLowerCase(notificationText))
      }
    }

    val actionText = executor?.actionText ?: getCommitActionName()
    val notificationText = if (isSkipCommitChecks) {
      message("commit.checks.failed.notification.commit.anyway.action", removeEllipsisSuffix(actionText))
    }
    else {
      removeEllipsisSuffix(actionText)
    }
    return capitalize(toLowerCase(notificationText))
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
        if (!isCommitChecksResultUpToDate) {
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
        if (!isCommitChecksResultUpToDate) {
          return
        }
        val file = FileDocumentManager.getInstance().getFile(event.document)
        if (file != null && areFilesAffectsCommitChecksResult(listOf(file))) {
          resetCommitChecksResult()
        }
      }
    }, this)
  }

  private fun resetCommitChecksResult() {
    isCommitChecksResultUpToDate = false
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
    return ui.commitProgressUi.getCommitCheckFailures().joinToString { it.text }
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

  protected fun isSkipCommitChecks(): Boolean = isBackgroundCommitChecks() && isCommitChecksResultUpToDate

  override fun doExecuteSession(sessionInfo: CommitSessionInfo): Boolean {
    if (!sessionInfo.isVcsCommit) {
      return workflow.executeSession(sessionInfo)
    }

    if (!isBackgroundCommitChecks()) {
      return workflow.executeSession(sessionInfo)
    }

    coroutineScope.launch {
      workflow.executeBackgroundSession(sessionInfo) {
        val isOnlyRunCommitChecks = commitContext.isOnlyRunCommitChecks
        commitContext.isOnlyRunCommitChecks = false

        if (isSkipCommitChecks() && !isOnlyRunCommitChecks) {
          return@executeBackgroundSession CommitChecksResult.Passed
        }

        val indicator = ui.commitProgressUi.startProgress(isOnlyRunCommitChecks)
        indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
          override fun cancel() = this@launch.cancel() // cancel coroutine
        })
        try {
          indicator.start()

          PartialChangesUtil.underChangeList(project, workflow.getBeforeCommitChecksChangelist()) {
            runAllHandlers(sessionInfo, indicator, isOnlyRunCommitChecks)
          }
        }
        finally {
          indicator.stop()
        }
      }
    }

    return true
  }

  private suspend fun runAllHandlers(
    sessionInfo: CommitSessionInfo,
    indicator: ProgressIndicator,
    isOnlyRunCommitChecks: Boolean
  ): CommitChecksResult {
    val metaHandlers = commitHandlers.filterIsInstance<CheckinMetaHandler>()
    workflow.runMetaHandlers(metaHandlers, ui.commitProgressUi, indicator)
    FileDocumentManager.getInstance().saveAllDocuments()

    val plainHandlers = commitHandlers.filterNot { it is CommitCheck<*> }
    val plainHandlersResult = workflow.runBeforeCommitHandlersChecks(sessionInfo, plainHandlers)
    if (!plainHandlersResult.shouldCommit) return plainHandlersResult

    val commitChecks = commitHandlers.filterNot { it is CheckinMetaHandler }.filterIsInstance<CommitCheck<*>>()
    val checksPassed = workflow.runCommitChecks(commitChecks, ui.commitProgressUi, indicator)
    when {
      isOnlyRunCommitChecks -> {
        isCommitChecksResultUpToDate = true
        return CommitChecksResult.OnlyChecks(checksPassed)
      }
      checksPassed -> {
        return CommitChecksResult.Passed
      }
      else -> {
        isCommitChecksResultUpToDate = true
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
    val isSkipCommitChecks = isSkipCommitChecks()
    return CommitWorkflowHandlerState(isAmend, isSkipCommitChecks)
  }

  protected open inner class CommitStateCleaner : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = resetState()
    override fun onCancel() = Unit
    override fun onFailure(errors: List<VcsException>) = resetState()

    protected open fun resetState() {
      disposeCommitOptions()

      workflow.clearCommitContext()
      initCommitHandlers()

      resetCommitChecksResult()
      updateDefaultCommitActionName()
    }
  }
}
