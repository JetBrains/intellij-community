// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.CommitWorkflowHandler

abstract class BaseCommitExecutorAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    val handler = getWorkflowHandler(e)
    val executor = getExecutor(handler)

    e.presentation.isVisible = handler != null && executor != null
    e.presentation.isEnabled = handler != null && executor != null && handler.isExecutorEnabled(executor)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val workflowHandler = getWorkflowHandler(e)!!
    val executor = getExecutor(workflowHandler)!!

    workflowHandler.execute(executor)
  }

  protected abstract val executorId: String

  private fun getWorkflowHandler(e: AnActionEvent) = CommitWorkflowHandler.DATA_KEY.getData(e.dataContext)
  private fun getExecutor(handler: CommitWorkflowHandler?) = handler?.getExecutor(executorId)
}