// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.vcs.commit.CommitWorkflowHandler

abstract class BaseCommitExecutorAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    val workflowHandler = getWorkflowHandler(e)
    val executor = getCommitExecutor(workflowHandler)

    e.presentation.isVisible = workflowHandler != null && executor != null
    e.presentation.isEnabled = workflowHandler != null && executor != null && workflowHandler.isExecutorEnabled(executor)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val workflowHandler = getWorkflowHandler(e)!!
    val executor = getCommitExecutor(workflowHandler)!!

    workflowHandler.execute(executor)
  }

  protected open val executorId: String = ""
  protected open fun getCommitExecutor(handler: CommitWorkflowHandler?) = handler?.getExecutor(executorId)

  private fun getWorkflowHandler(e: AnActionEvent) = VcsDataKeys.COMMIT_WORKFLOW_HANDLER.getData(e.dataContext)
}

internal class DefaultCommitExecutorAction(private val executor: CommitExecutor) : BaseCommitExecutorAction() {
  init {
    templatePresentation.text = executor.actionText
  }

  override fun getCommitExecutor(handler: CommitWorkflowHandler?): CommitExecutor? = executor
}