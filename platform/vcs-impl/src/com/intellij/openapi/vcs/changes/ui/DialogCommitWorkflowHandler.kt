// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitExecutorBase

class DialogCommitWorkflowHandler(
  private val workflow: DialogCommitWorkflow,
  private val ui: CommitChangeListDialog
) : CommitWorkflowHandler {

  init {
    ui.setDataProvider { dataId ->
      if (dataId == CommitWorkflowHandler.DATA_KEY.name) this
      else null
    }
  }

  override fun getExecutor(executorId: String): CommitExecutor? = ui.executors.find { it.id == executorId }

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean =
    ui.hasDiffs() || (executor is CommitExecutorBase && !executor.areChangesRequired())

  override fun execute(executor: CommitExecutor) = ui.execute(executor)
}