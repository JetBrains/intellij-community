// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.actions.commit.getContextCommitWorkflowHandler
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitExecutorWithRichDescription
import com.intellij.util.ui.JButtonAction
import com.intellij.vcs.commit.CommitWorkflowHandler
import org.jetbrains.annotations.ApiStatus
import javax.swing.JButton

/**
 * @see com.intellij.openapi.vcs.VcsActions.PRIMARY_COMMIT_EXECUTORS_GROUP,
 * @see com.intellij.openapi.vcs.VcsActions.COMMIT_EXECUTORS_GROUP
 */
@ApiStatus.Internal
abstract class CommitExecutorAction : JButtonAction(null) {
  init {
    isEnabledInModalContext = true
  }

  override fun createButton(): JButton = JButton()

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    val workflowHandler = e.getContextCommitWorkflowHandler()
    val executor = if (workflowHandler != null) getCommitExecutor(workflowHandler) else null

    if (workflowHandler != null && executor is CommitExecutorWithRichDescription) {
      val state = workflowHandler.getState()
      val actionText = executor.getText(state)
      if (actionText != null) {
        e.presentation.text = actionText
      }
    }

    e.presentation.isVisible = workflowHandler != null && executor != null
    e.presentation.isEnabled = workflowHandler != null && executor != null && workflowHandler.isExecutorEnabled(executor)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val workflowHandler = e.getContextCommitWorkflowHandler()!!
    val executor = getCommitExecutor(workflowHandler)!!

    workflowHandler.execute(executor)
  }

  protected abstract fun getCommitExecutor(handler: CommitWorkflowHandler): CommitExecutor?
}

internal class DefaultCommitExecutorAction(private val executor: CommitExecutor) : CommitExecutorAction() {
  init {
    templatePresentation.text = executor.actionText
  }

  override fun getCommitExecutor(handler: CommitWorkflowHandler): CommitExecutor = executor
}

abstract class BaseCommitExecutorAction : CommitExecutorAction() {
  protected abstract val executorId: String
  override fun getCommitExecutor(handler: CommitWorkflowHandler) = handler.getExecutor(executorId)
}
