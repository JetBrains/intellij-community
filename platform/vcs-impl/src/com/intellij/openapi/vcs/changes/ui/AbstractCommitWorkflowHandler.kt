// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.*

abstract class AbstractCommitWorkflowHandler<W : AbstractCommitWorkflow, U : CommitWorkflowUi> :
  CommitWorkflowHandler,
  CommitExecutorListener,
  Disposable {

  abstract val workflow: W
  abstract val ui: U

  protected val project get() = workflow.project

  protected fun getIncludedChanges() = ui.getIncludedChanges()
  protected fun getIncludedUnversionedFiles() = ui.getIncludedUnversionedFiles()

  override fun executorCalled(executor: CommitExecutor?) = executor?.let { execute(it) } ?: executeDefault(null)

  override fun getExecutor(executorId: String): CommitExecutor? = null
  override fun isExecutorEnabled(executor: CommitExecutor): Boolean = false
  override fun execute(executor: CommitExecutor) {
    val session = executor.createCommitSession()

    if (session === CommitSession.VCS_COMMIT) {
      executeDefault(executor)
    }
    else {
      executeCustom(executor, session)
    }
  }

  protected open fun executeDefault(executor: CommitExecutor?) = Unit

  protected open fun executeCustom(executor: CommitExecutor, session: CommitSession) = Unit

  protected fun addUnversionedFiles(changeList: LocalChangeList): Boolean =
    workflow.addUnversionedFiles(changeList, getIncludedUnversionedFiles()) { changes -> ui.includeIntoCommit(changes) }

  override fun dispose() = Unit
}