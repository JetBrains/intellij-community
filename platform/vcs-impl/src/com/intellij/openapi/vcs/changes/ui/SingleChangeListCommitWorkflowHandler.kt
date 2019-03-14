// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsDataKeys.COMMIT_WORKFLOW_HANDLER
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitExecutorBase
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.openapi.vcs.changes.CommitWorkflowHandler

class SingleChangeListCommitWorkflowHandler(
  private val workflow: DialogCommitWorkflow,
  private val ui: CommitChangeListDialog
) : CommitWorkflowHandler, CommitExecutorListener, Disposable {

  private val project get() = workflow.project
  private val vcsConfiguration = VcsConfiguration.getInstance(project)

  private fun getChangeList() = ui.getChangeList()
  private fun getIncludedChanges() = ui.getIncludedChanges()
  private fun getIncludedUnversionedFiles() = ui.getIncludedUnversionedFiles()

  private fun getCommitMessage() = ui.commitMessage

  init {
    ui.addExecutorListener(this, this)
    ui.addDataProvider(DataProvider { dataId ->
      if (dataId == COMMIT_WORKFLOW_HANDLER.name) this
      else null
    })
  }

  override fun executorCalled(executor: CommitExecutor?) = executor?.let { execute(it) } ?: executeDefault(null)

  override fun getExecutor(executorId: String): CommitExecutor? = workflow.executors.find { it.id == executorId }

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean =
    ui.hasDiffs() || (executor is CommitExecutorBase && !executor.areChangesRequired())

  override fun execute(executor: CommitExecutor) {
    val session = executor.createCommitSession()

    if (session === CommitSession.VCS_COMMIT) {
      executeDefault(executor)
    }
    else {
      executeCustom(executor, session)
    }
  }

  private fun executeDefault(executor: CommitExecutor?) {
    if (!addUnversionedFiles()) return
    if (!checkEmptyCommitMessage()) return

    ui.executeDefaultCommitSession(executor)
  }

  private fun executeCustom(executor: CommitExecutor, session: CommitSession) {
    if (!workflow.canExecute(executor, getIncludedChanges())) return
    if (!checkEmptyCommitMessage()) return

    ui.execute(executor, session)
  }

  private fun addUnversionedFiles(): Boolean =
    workflow.addUnversionedFiles(getChangeList(), getIncludedUnversionedFiles()) { changes -> ui.includeIntoCommit(changes) }

  private fun checkEmptyCommitMessage(): Boolean =
    getCommitMessage().isNotEmpty() || !vcsConfiguration.FORCE_NON_EMPTY_COMMENT || ui.confirmCommitWithEmptyMessage()

  override fun dispose() = Unit
}