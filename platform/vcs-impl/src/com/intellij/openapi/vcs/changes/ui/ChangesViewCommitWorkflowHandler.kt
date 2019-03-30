// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor

class ChangesViewCommitWorkflowHandler(
  override val workflow: ChangesViewCommitWorkflow,
  override val ui: ChangesViewCommitWorkflowUi
) : AbstractCommitWorkflowHandler<ChangesViewCommitWorkflow, ChangesViewCommitWorkflowUi>() {

  private val changeListManager = ChangeListManager.getInstance(project)

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)

  init {
    Disposer.register(ui, this)

    workflow.addListener(this, this)

    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())
  }

  override fun vcsesChanged() {
    ui.defaultCommitActionName = getDefaultCommitActionName(workflow.vcses)
    ui.isDefaultCommitActionEnabled = workflow.vcses.isNotEmpty()
  }

  fun activate(): Boolean = ui.activate()

  override fun executeDefault(executor: CommitExecutor?) {
    if (!addUnversionedFiles(changeListManager.defaultChangeList)) return
    checkEmptyCommitMessage()
  }

  override fun customCommitSucceeded() = Unit
}