// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory

abstract class NonModalCommitWorkflowHandler<W : AbstractCommitWorkflow, U : NonModalCommitWorkflowUi> :
  AbstractCommitWorkflowHandler<W, U>() {

  private var areCommitOptionsCreated = false

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

  protected fun createCommitStateCleaner(): CommitResultHandler = CommitStateCleaner()

  private inner class CommitStateCleaner : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = resetState()
    override fun onCancel() = Unit
    override fun onFailure(errors: List<VcsException>) = resetState()

    private fun resetState() {
      disposeCommitOptions()

      workflow.clearCommitContext()
      initCommitHandlers()
    }
  }
}