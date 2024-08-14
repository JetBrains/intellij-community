// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.DumbService.DumbModeListener
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil.capitalize
import com.intellij.openapi.util.text.StringUtil.toLowerCase
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.util.containers.nullize
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.PROGRESS_FRACTION_EARLY
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.PROGRESS_FRACTION_LATE
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.PROGRESS_FRACTION_MODIFICATIONS
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.PROGRESS_FRACTION_POST
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

  private val checkinErrorNotifications = SingletonNotificationManager(VcsNotifier.importantNotification().displayId,
                                                                       NotificationType.ERROR)

  private val postCommitChecksHandler: PostCommitChecksHandler get() = PostCommitChecksHandler.getInstance(project)
  private var pendingPostCommitChecks: PendingPostCommitChecks? = null

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
    workflow.initCommitExecutors(getCommitExecutors(project, workflow.vcses) + RunCommitChecksExecutor)
    initCommitHandlers()

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
    ui.defaultCommitActionName = getDefaultCommitActionName(amendCommitHandler.isAmendCommitMode, willSkipCommitChecks())
  }

  protected open fun getDefaultCommitActionName(isAmend: Boolean, isSkipCommitChecks: Boolean): @Nls String {
    return getDefaultCommitActionName(workflow.vcses, isAmend, isSkipCommitChecks)
  }

  private fun getCommitActionTextForNotification(
    executor: CommitExecutor?,
    isSkipCommitChecks: Boolean
  ): @Nls(capitalization = Nls.Capitalization.Sentence) String {
    val isAmend = amendCommitHandler.isAmendCommitMode
    val actionText: @Nls String = getActionTextWithoutEllipsis(workflow.vcses, executor, isAmend, isSkipCommitChecks,
                                                               removeMnemonic = true)
    return capitalize(toLowerCase(actionText))
  }

  fun updateDefaultCommitActionEnabled() {
    ui.isDefaultCommitActionEnabled = isReady()
  }

  protected open fun isReady() = workflow.vcses.isNotEmpty() && !workflow.isExecuting && !amendCommitHandler.isLoading

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = super.isExecutorEnabled(executor) && isReady()

  private fun createPrimaryCommitActions(): List<AnAction> {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction(VcsActions.PRIMARY_COMMIT_EXECUTORS_GROUP) as DefaultActionGroup
    return group.getChildren(actionManager).toList()
  }

  private fun createCommitExecutorActions(): List<AnAction> {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction(VcsActions.COMMIT_EXECUTORS_GROUP) as DefaultActionGroup
    val executors = workflow.commitExecutors.filter { it.useDefaultAction() }
    return group.getChildren(actionManager).toList() +
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
      override fun after(events: List<VFileEvent>) {
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

  private fun willSkipCommitChecks() = isCommitChecksResultUpToDate == RecentCommitChecks.EARLY_FAILED ||
                                       isCommitChecksResultUpToDate == RecentCommitChecks.MODIFICATIONS_FAILED ||
                                       isCommitChecksResultUpToDate == RecentCommitChecks.POST_FAILED

  private fun willSkipEarlyCommitChecks() = isCommitChecksResultUpToDate == RecentCommitChecks.EARLY_FAILED ||
                                            isCommitChecksResultUpToDate == RecentCommitChecks.MODIFICATIONS_FAILED ||
                                            isCommitChecksResultUpToDate == RecentCommitChecks.POST_FAILED

  private fun willSkipModificationCommitChecks() = isCommitChecksResultUpToDate == RecentCommitChecks.MODIFICATIONS_FAILED ||
                                                   isCommitChecksResultUpToDate == RecentCommitChecks.POST_FAILED

  private fun willSkipLateCommitChecks() = isCommitChecksResultUpToDate == RecentCommitChecks.POST_FAILED

  private fun willSkipPostCommitChecks() = isCommitChecksResultUpToDate == RecentCommitChecks.POST_FAILED


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
      val failures = ui.commitProgressUi.getCommitCheckFailures()
      val commitActionText = getCommitActionTextForNotification(executor, false)
      val commitAnywayActionText = getCommitActionTextForNotification(executor, true)
      val title = message("commit.checks.failed.notification.title", commitActionText)
      val description = getCommitCheckFailureDescription(failures)
      checkinErrorNotifications.notify(title, description.toString(), project) {
        it.setDisplayId(VcsNotificationIdsHolder.COMMIT_CHECKS_FAILED)
        it.addAction(
          NotificationAction.createExpiring(commitAnywayActionText) { _, _ ->
            if (!workflow.isExecuting) {
              executorCalled(executor)
            }
          })
        appendShowDetailsNotificationActions(it, failures)
      }
    }

    if (result is CommitChecksResult.OnlyChecks && !result.checksPassed) {
      val failures = ui.commitProgressUi.getCommitCheckFailures()
      val commitActionText = getCommitActionTextForNotification(null, false)
      val title = message("commit.checks.failed.notification.title", commitActionText)
      val description = getCommitCheckFailureDescription(failures)
      checkinErrorNotifications.notify(title, description.toString(), project) {
        it.setDisplayId(VcsNotificationIdsHolder.COMMIT_CHECKS_ONLY_FAILED)
        appendShowDetailsNotificationActions(it, failures)
      }
    }
  }

  private fun getCommitCheckFailureDescription(failures: List<CommitCheckFailure>): @NlsContexts.NotificationContent HtmlBuilder {
    return HtmlBuilder().appendWithSeparators(HtmlChunk.br(),
                                              failures.filterIsInstance<CommitCheckFailure.WithDescription>().map { it.text })
  }

  private fun appendShowDetailsNotificationActions(notification: Notification, failures: List<CommitCheckFailure>) {
    for (failure in failures.filterIsInstance<CommitCheckFailure.WithDetails>()) {
      notification.addAction(NotificationAction.create(failure.viewDetailsActionText.dropMnemonic()) { _, _ ->
        failure.viewDetails(CommitProblemPlace.NOTIFICATION)
      })
    }

    val hasGenericFailure = failures.any { it !is CommitCheckFailure.WithDetails }
    if (hasGenericFailure) {
      notification.addAction(NotificationAction.create(message("commit.checks.failed.notification.show.details.action")) { _, _ ->
        showCommitCheckFailuresPanel()
      })
    }
  }

  private fun showCommitCheckFailuresPanel() {
    val toolWindow = ChangesViewContentManager.getToolWindowFor(project, LOCAL_CHANGES)
    toolWindow?.activate {
      ChangesViewContentManager.getInstance(project).selectContent(LOCAL_CHANGES)
    }
  }

  override suspend fun doExecuteSession(sessionInfo: CommitSessionInfo, commitInfo: DynamicCommitInfo): Boolean {
    if (!sessionInfo.isVcsCommit) {
      return workflow.executeSession(sessionInfo, commitInfo)
    }

    workflow.launchAsyncSession(coroutineScope, sessionInfo) {
      pendingPostCommitChecks = null

      val isOnlyRunCommitChecks = commitContext.isOnlyRunCommitChecks
      commitContext.isOnlyRunCommitChecks = false

      val skipEarlyCommitChecks = !isOnlyRunCommitChecks && willSkipEarlyCommitChecks()
      val skipModificationCommitChecks = !isOnlyRunCommitChecks && willSkipModificationCommitChecks()
      val skipLateCommitChecks = !isOnlyRunCommitChecks && willSkipLateCommitChecks()
      val skipPostCommitChecks = !isOnlyRunCommitChecks && willSkipPostCommitChecks()
      resetCommitChecksResult()

      ui.commitProgressUi.runWithProgress(isOnlyRunCommitChecks) {
        val failure = runNonModalBeforeCommitChecks(commitInfo, skipEarlyCommitChecks, skipModificationCommitChecks,
                                                    skipLateCommitChecks, skipPostCommitChecks)
        handleCommitProblem(failure, isOnlyRunCommitChecks)
      }
    }

    return true
  }

  private suspend fun runNonModalBeforeCommitChecks(
    commitInfo: DynamicCommitInfo,
    skipEarlyCommitChecks: Boolean,
    skipModificationCommitChecks: Boolean,
    skipLateCommitChecks: Boolean,
    skipPostCommitChecks: Boolean,
  ): NonModalCommitChecksFailure? = reportSequentialProgress { reporter ->
    try {
      val handlers = workflow.commitHandlers
      val commitChecks = handlers
        .filter { it.acceptExecutor(commitInfo.executor) }
        .map { it.asCommitCheck(commitInfo) }
        .filter { it.isEnabled() }
        .groupBy { it.getExecutionOrder() }

      val earlyChecks = commitChecks[CommitCheck.ExecutionOrder.EARLY].orEmpty()
      val modificationChecks = commitChecks[CommitCheck.ExecutionOrder.MODIFICATION].orEmpty()
      val lateChecks = commitChecks[CommitCheck.ExecutionOrder.LATE].orEmpty()
      val postCommitChecks = commitChecks[CommitCheck.ExecutionOrder.POST_COMMIT].orEmpty()
      @Suppress("DEPRECATION") val metaHandlers = handlers.filterIsInstance<CheckinMetaHandler>()

      if (!skipEarlyCommitChecks) {
        reporter.nextStep(PROGRESS_FRACTION_EARLY) {
          runEarlyCommitChecks(commitInfo, earlyChecks)
        }?.let { return it }
      }

      if (!skipModificationCommitChecks) {
        reporter.nextStep(PROGRESS_FRACTION_MODIFICATIONS) {
          runModificationCommitChecks(commitInfo, modificationChecks, metaHandlers)
        }?.let { return it }
      }

      if (!skipLateCommitChecks) {
        reporter.nextStep(PROGRESS_FRACTION_LATE) {
          runLateCommitChecks(commitInfo, lateChecks)
        }?.let { return it }
      }

      if (!skipPostCommitChecks) {
        if (postCommitChecks.isNotEmpty()) {
          if (VcsConfiguration.getInstance(project).NON_MODAL_COMMIT_POSTPONE_SLOW_CHECKS &&
              commitInfo.executor?.requiresSyncCommitChecks() != true &&
              postCommitChecksHandler.canHandle(commitInfo)) {
            pendingPostCommitChecks = PendingPostCommitChecks(commitInfo.asStaticInfo(), postCommitChecks)
          }
          else {
            postCommitChecksHandler.resetPendingCommits()
            reporter.nextStep(PROGRESS_FRACTION_POST) {
              runSyncPostCommitChecks(commitInfo, postCommitChecks)
            }?.let { return it }
          }
        }
      }

      return null // checks passed
    }
    catch (e: CancellationException) {
      LOG.debug("runNonModalBeforeCommitChecks was cancelled")
      // Do not report error on cancellation
      throw e
    }
    catch (e: Throwable) {
      LOG.error(Throwable(e))
      reportCommitCheckFailure(CommitProblem.createError(e))
      return NonModalCommitChecksFailure.ERROR
    }
  }

  private suspend fun runEarlyCommitChecks(commitInfo: DynamicCommitInfo, commitChecks: List<CommitCheck>): NonModalCommitChecksFailure? {
    val problems = commitChecks.mapWithProgress { commitCheck ->
      AbstractCommitWorkflow.runCommitCheck(project, commitCheck, commitInfo)
    }.filterNotNull()
    if (problems.isEmpty()) return null

    problems.forEach { reportCommitCheckFailure(it) }
    return NonModalCommitChecksFailure.EARLY_FAILED
  }

  private suspend fun runModificationCommitChecks(commitInfo: DynamicCommitInfo,
                                                  commitChecks: List<CommitCheck>,
                                                  @Suppress("DEPRECATION")
                                                  metaHandlers: List<CheckinMetaHandler>): NonModalCommitChecksFailure? {
    if (metaHandlers.isEmpty() && commitChecks.isEmpty()) return null

    return workflow.runModificationCommitChecks underChangelist@{
      AbstractCommitWorkflow.runMetaHandlers(metaHandlers)

      reportSequentialProgress(commitChecks.size) { reporter ->
        for (commitCheck in commitChecks) {
          val problem = reporter.itemStep {
            AbstractCommitWorkflow.runCommitCheck(project, commitCheck, commitInfo)
          } ?: continue
          reportCommitCheckFailure(problem)
          return@underChangelist NonModalCommitChecksFailure.MODIFICATIONS_FAILED
        }
      }

      //readaction is not enough
      writeIntentReadAction {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
      return@underChangelist null
    }
  }

  private suspend fun runLateCommitChecks(commitInfo: DynamicCommitInfo, commitChecks: List<CommitCheck>): NonModalCommitChecksFailure? {
    reportSequentialProgress(commitChecks.size) { reporter ->
      for (commitCheck in commitChecks) {
        val problem = reporter.itemStep {
          AbstractCommitWorkflow.runCommitCheck(project, commitCheck, commitInfo)
        } ?: continue

        val solution = writeIntentReadAction { problem.showModalSolution(project, commitInfo) }
        if (solution == CheckinHandler.ReturnResult.COMMIT) continue

        reportCommitCheckFailure(problem)
        return NonModalCommitChecksFailure.ABORTED
      }
      return null
    }
  }

  private suspend fun runSyncPostCommitChecks(commitInfo: DynamicCommitInfo,
                                              commitChecks: List<CommitCheck>): NonModalCommitChecksFailure? {
    val problems = commitChecks.mapWithProgress { commitCheck ->
      AbstractCommitWorkflow.runCommitCheck(project, commitCheck, commitInfo)
    }.filterNotNull()
    if (problems.isEmpty()) return null

    problems.forEach { reportCommitCheckFailure(it) }
    return NonModalCommitChecksFailure.POST_FAILED
  }

  private fun reportCommitCheckFailure(problem: CommitProblem) {
    val checkFailure = when (problem) {
      is UnknownCommitProblem -> CommitCheckFailure.Unknown
      is CommitProblemWithDetails -> CommitCheckFailure.WithDetails(HtmlChunk.text(problem.text),
                                                                    problem.showDetailsLink,
                                                                    problem.showDetailsAction) { place ->
        CommitSessionCollector.getInstance(project).logCommitProblemViewed(problem, place)
        problem.showDetails(project)
      }
      else -> CommitCheckFailure.WithDescription(HtmlChunk.text(problem.text))
    }
    ui.commitProgressUi.addCommitCheckFailure(checkFailure)
  }

  private fun handleCommitProblem(failure: NonModalCommitChecksFailure?, isOnlyRunCommitChecks: Boolean): CommitChecksResult {
    val checksPassed = failure == null
    val aborted = failure == NonModalCommitChecksFailure.ABORTED

    when (failure) {
      null -> {
        if (isOnlyRunCommitChecks) {
          isCommitChecksResultUpToDate = RecentCommitChecks.PASSED
        }
        else {
          isCommitChecksResultUpToDate = RecentCommitChecks.UNKNOWN // We are going to commit, remembering the result is not needed.
        }
      }
      NonModalCommitChecksFailure.EARLY_FAILED -> {
        isCommitChecksResultUpToDate = RecentCommitChecks.EARLY_FAILED
      }
      NonModalCommitChecksFailure.MODIFICATIONS_FAILED -> {
        isCommitChecksResultUpToDate = RecentCommitChecks.MODIFICATIONS_FAILED
      }
      NonModalCommitChecksFailure.POST_FAILED -> {
        isCommitChecksResultUpToDate = RecentCommitChecks.POST_FAILED
      }
      NonModalCommitChecksFailure.ABORTED,
      NonModalCommitChecksFailure.ERROR -> {
        isCommitChecksResultUpToDate = RecentCommitChecks.FAILED
      }
    }

    if (aborted) {
      return CommitChecksResult.Cancelled
    }
    else if (isOnlyRunCommitChecks) {
      return CommitChecksResult.OnlyChecks(checksPassed)
    }
    else if (checksPassed) {
      return CommitChecksResult.Passed
    }
    else {
      return CommitChecksResult.Failed()
    }
  }

  override fun dispose() {
    disposeCommitOptions()
    hideCommitChecksFailureNotification()
    coroutineScope.cancel()
    project.getServiceIfCreated(PostCommitChecksHandler::class.java)?.resetPendingCommits() // null during Project dispose
    super.dispose()
  }

  fun hideCommitChecksFailureNotification() {
    checkinErrorNotifications.clear()
  }

  fun showCommitOptions(isFromToolbar: Boolean, dataContext: DataContext) =
    ui.showCommitOptions(ensureCommitOptions(), getDefaultCommitActionName(workflow.vcses), isFromToolbar, dataContext)

  override fun saveCommitOptionsOnCommit(): Boolean {
    ensureCommitOptions()
    // restore state in case settings were changed via configurable
    commitOptions.allOptions
      .filter { it is UnnamedConfigurable }
      .forEach { it.restoreState() }
    return super.saveCommitOptionsOnCommit()
  }

  private fun ensureCommitOptions(): CommitOptions {
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
    val isSkipCommitChecks = willSkipCommitChecks()
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

  protected inner class PostCommitChecksRunner : CommitterResultHandler {
    override fun onSuccess() {
      pendingPostCommitChecks?.let { postCommitChecksHandler.startPostCommitChecksTask(it.commitInfo, it.commitChecks) }
      pendingPostCommitChecks = null
    }

    override fun onCancel() {
      pendingPostCommitChecks = null
    }

    override fun onFailure() {
      pendingPostCommitChecks = null
    }
  }
}

private class PendingPostCommitChecks(val commitInfo: StaticCommitInfo, val commitChecks: List<CommitCheck>)

private enum class NonModalCommitChecksFailure { EARLY_FAILED, MODIFICATIONS_FAILED, POST_FAILED, ABORTED, ERROR }

private enum class RecentCommitChecks { UNKNOWN, PASSED, EARLY_FAILED, MODIFICATIONS_FAILED, POST_FAILED, FAILED }
