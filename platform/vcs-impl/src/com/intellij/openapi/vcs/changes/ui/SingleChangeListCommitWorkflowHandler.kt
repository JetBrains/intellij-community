// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitExecutorBase
import com.intellij.openapi.vcs.changes.CommitWorkflowHandler

class SingleChangeListCommitWorkflowHandler(
  private val workflow: DialogCommitWorkflow,
  private val ui: CommitChangeListDialog
) : CommitWorkflowHandler, CommitExecutorListener, Disposable {

  init {
    ui.addExecutorListener(this, this)
    ui.addDataProvider(DataProvider { dataId ->
      if (dataId == COMMIT_WORKFLOW_HANDLER.name) this
      else null
    })
  }

  override fun executorCalled(executor: CommitExecutor?) = executor?.let { ui.execute(it) } ?: ui.executeDefaultCommitSession(null)

  override fun getExecutor(executorId: String): CommitExecutor? = workflow.executors.find { it.id == executorId }

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean =
    ui.hasDiffs() || (executor is CommitExecutorBase && !executor.areChangesRequired())

  override fun execute(executor: CommitExecutor) = ui.execute(executor)

  override fun dispose() = Unit
}