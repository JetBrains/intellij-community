// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.actions.getContextCommitWorkflowHandler
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.util.ui.JButtonAction
import com.intellij.vcs.commit.CommitWorkflowHandler
import javax.swing.JButton

abstract class BaseCommitExecutorAction : JButtonAction(null) {
  init {
    isEnabledInModalContext = true
  }

  override fun createButton(): JButton = JButton().apply { isOpaque = false }

  override fun update(e: AnActionEvent) {
    val workflowHandler = e.getContextCommitWorkflowHandler()
    val executor = getCommitExecutor(workflowHandler)

    e.presentation.isVisible = workflowHandler != null && executor != null
    e.presentation.isEnabled = workflowHandler != null && executor != null && workflowHandler.isExecutorEnabled(executor)

    updateButtonFromPresentation(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val workflowHandler = e.getContextCommitWorkflowHandler()!!
    val executor = getCommitExecutor(workflowHandler)!!

    workflowHandler.execute(executor)
  }

  protected open val executorId: String = ""
  protected open fun getCommitExecutor(handler: CommitWorkflowHandler?) = handler?.getExecutor(executorId)
}

internal class DefaultCommitExecutorAction(private val executor: CommitExecutor) : BaseCommitExecutorAction() {
  init {
    templatePresentation.text = executor.actionText
  }

  override fun getCommitExecutor(handler: CommitWorkflowHandler?): CommitExecutor = executor
}