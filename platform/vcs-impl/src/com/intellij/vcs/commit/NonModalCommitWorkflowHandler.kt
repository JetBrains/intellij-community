// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.changes.actions.DefaultCommitExecutorAction
import com.intellij.openapi.vcs.checkin.CheckinHandler.ReturnResult
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.CommitProblem
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.vcs.commit.AbstractCommitWorkflow.Companion.getCommitExecutors
import kotlinx.coroutines.*
import java.lang.Runnable

private val LOG = logger<NonModalCommitWorkflowHandler<*, *>>()

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

  protected fun setupCommitHandlersTracking() {
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
    ui.defaultCommitActionName = getCommitActionName()
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

  override fun doExecuteDefault(executor: CommitExecutor?): Boolean {
    if (!Registry.`is`("vcs.background.commit.checks")) return super.doExecuteDefault(executor)

    coroutineScope.launch {
      workflow.executeDefault {
        var result = ReturnResult.COMMIT

        ui.commitProgressUi.startProgress()
        try {
          for (commitCheck in commitHandlers.filterIsInstance<CommitCheck<*>>()) {
            val problem = runCommitCheck(commitCheck)
            if (problem != null) result = ReturnResult.CANCEL
          }
        }
        finally {
          ui.commitProgressUi.endProgress()
        }

        result
      }
    }

    return true
  }

  private suspend fun <P : CommitProblem> runCommitCheck(commitCheck: CommitCheck<P>): P? {
    val problem = workflow.runCommitCheck(commitCheck)
    problem?.let { ui.commitProgressUi.addCommitCheckFailure(it.text) { commitCheck.showDetails(it) } }
    return problem
  }

  override fun dispose() = coroutineScope.cancel()

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
    }
  }
}