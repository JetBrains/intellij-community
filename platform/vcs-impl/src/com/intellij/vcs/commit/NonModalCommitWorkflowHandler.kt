// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vcs.checkin.CheckinHandler.ReturnResult
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitExecutors
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.properties.Delegates.observable

private val LOG = logger<NonModalCommitWorkflowHandler<*, *>>()

private val isBackgroundCommitChecksValue: RegistryValue get() = Registry.get("vcs.background.commit.checks")
internal fun isBackgroundCommitChecks(): Boolean = isBackgroundCommitChecksValue.asBoolean()

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

  protected fun setupCommitHandlersTracking() {
    isBackgroundCommitChecksValue.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = commitHandlersChanged()
    }, this)
    CheckinHandlerFactory.EP_NAME.addChangeListener(Runnable { commitHandlersChanged() }, this)
    VcsCheckinHandlerFactory.EP_NAME.addChangeListener(Runnable { commitHandlersChanged() }, this)
  }

  private fun commitHandlersChanged() {
    if (workflow.isExecuting) return

    saveCommitOptions(false)
    disposeCommitOptions()

    initCommitHandlers()
  }

  override fun vcsesChanged() {
    initCommitHandlers()
    workflow.initCommitExecutors(getCommitExecutors(project, workflow.vcses))

    updateDefaultCommitActionEnabled()
    updateDefaultCommitActionName()
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
      isAmend && isSkipCommitChecks -> message("action.amend.commit.anyway.text", commitText)
      isAmend && !isSkipCommitChecks -> message("amend.action.name", commitText)
      !isAmend && isSkipCommitChecks -> message("action.commit.anyway.text", commitText)
      else -> commitText
    }
  }

  fun updateDefaultCommitActionEnabled() {
    ui.isDefaultCommitActionEnabled = isReady()
  }

  protected open fun isReady() = workflow.vcses.isNotEmpty() && !workflow.isExecuting && !amendCommitHandler.isLoading

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = super.isExecutorEnabled(executor) && isReady()

  private fun createCommitExecutorActions(): List<AnAction> {
    val executors = workflow.commitExecutors.ifEmpty { return emptyList() }
    val group = ActionManager.getInstance().getAction("Vcs.CommitExecutor.Actions") as ActionGroup

    return group.getChildren(null).toList() + executors.filter { it.useDefaultAction() }.map { DefaultCommitExecutorAction(it) }
  }

  override fun checkCommit(executor: CommitExecutor?): Boolean =
    ui.commitProgressUi.run {
      val executorWithoutChangesAllowed = executor?.areChangesRequired() == false

      isEmptyChanges = !amendCommitHandler.isAmendWithoutChangesAllowed() && !executorWithoutChangesAllowed && isCommitEmpty()
      isEmptyMessage = getCommitMessage().isBlank()

      !isEmptyChanges && !isEmptyMessage
    }

  protected fun setupCommitChecksResultTracking() =
    getApplication().addApplicationListener(object : ApplicationListener {
      override fun writeActionStarted(action: Any) {
        isCommitChecksResultUpToDate = false
      }
    }, this)

  override fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: ReturnResult) {
    super.beforeCommitChecksEnded(isDefaultCommit, result)
    if (result == ReturnResult.COMMIT) {
      ui.commitProgressUi.clearCommitCheckFailures()
    }
  }

  fun isSkipCommitChecks(): Boolean = isBackgroundCommitChecks() && isCommitChecksResultUpToDate

  override fun doExecuteDefault(executor: CommitExecutor?): Boolean {
    if (!isBackgroundCommitChecks()) return super.doExecuteDefault(executor)

    coroutineScope.launch {
      workflow.executeDefault {
        if (isSkipCommitChecks()) return@executeDefault ReturnResult.COMMIT

        ui.commitProgressUi.startProgress()
        try {
          runAllHandlers(executor)
        }
        finally {
          ui.commitProgressUi.endProgress()
        }
      }
    }

    return true
  }

  private suspend fun runAllHandlers(executor: CommitExecutor?): ReturnResult {
    workflow.runMetaHandlers()
    FileDocumentManager.getInstance().saveAllDocuments()

    val handlersResult = workflow.runHandlers(executor)
    if (handlersResult != ReturnResult.COMMIT) return handlersResult

    val checksResult = runCommitChecks()
    if (checksResult != ReturnResult.COMMIT) isCommitChecksResultUpToDate = true
    return checksResult
  }

  private suspend fun runCommitChecks(): ReturnResult {
    var result = ReturnResult.COMMIT

    for (commitCheck in commitHandlers.filterNot { it is CheckinMetaHandler }.filterIsInstance<CommitCheck<*>>()) {
      val problem = runCommitCheck(commitCheck)
      if (problem != null) result = ReturnResult.CANCEL
    }

    return result
  }

  private suspend fun <P : CommitProblem> runCommitCheck(commitCheck: CommitCheck<P>): P? {
    val problem = workflow.runCommitCheck(commitCheck)
    problem?.let { ui.commitProgressUi.addCommitCheckFailure(it.text) { commitCheck.showDetails(it) } }
    return problem
  }

  override fun dispose() {
    coroutineScope.cancel()
    super.dispose()
  }

  fun showCommitOptions(isFromToolbar: Boolean, dataContext: DataContext) =
    ui.showCommitOptions(ensureCommitOptions(), getCommitActionName(), isFromToolbar, dataContext)

  override fun saveCommitOptions(): Boolean = saveCommitOptions(true)

  protected fun saveCommitOptions(isEnsureOptionsCreated: Boolean): Boolean {
    if (isEnsureOptionsCreated) ensureCommitOptions()
    return super.saveCommitOptions()
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

  protected open inner class CommitStateCleaner : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = resetState()
    override fun onCancel() = Unit
    override fun onFailure(errors: List<VcsException>) = resetState()

    protected open fun resetState() {
      disposeCommitOptions()

      workflow.clearCommitContext()
      initCommitHandlers()

      isCommitChecksResultUpToDate = false
      updateDefaultCommitActionName()
    }
  }
}