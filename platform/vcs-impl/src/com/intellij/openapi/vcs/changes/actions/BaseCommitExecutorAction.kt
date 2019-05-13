// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitExecutorBase
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.openapi.vcs.ui.Refreshable

abstract class BaseCommitExecutorAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    val dialog = getCommitDialog(e)
    val executor = getCommitExecutor(dialog)

    e.presentation.isVisible = dialog != null && executor != null
    e.presentation.isEnabled = dialog != null && executor != null && isEnabled(dialog, executor)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = getCommitDialog(e)!!
    val executor = getCommitExecutor(dialog)!!

    dialog.execute(executor)
  }

  protected abstract val executorId: String

  protected fun getCommitDialog(e: AnActionEvent): CommitChangeListDialog? = Refreshable.PANEL_KEY.getData(e.dataContext) as? CommitChangeListDialog

  protected fun getCommitExecutor(dialog: CommitChangeListDialog?): CommitExecutor? = dialog?.executors?.find { it.id == executorId }

  protected fun isEnabled(dialog: CheckinProjectPanel, executor: CommitExecutor): Boolean =
    dialog.hasDiffs() || (executor is CommitExecutorBase && !executor.areChangesRequired())
}