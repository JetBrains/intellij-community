// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitWorkflowUi

class ChangesViewCommitWorkflowHandler(
  override val workflow: ChangesViewCommitWorkflow,
  override val ui: CommitWorkflowUi
) : AbstractCommitWorkflowHandler<ChangesViewCommitWorkflow, CommitWorkflowUi>() {

  private val changeListManager = ChangeListManager.getInstance(project)

  init {
    Disposer.register(ui, this)

    ui.addExecutorListener(this, this)
  }

  fun activate(): Boolean = ui.activate()

  override fun executeDefault(executor: CommitExecutor?) {
    addUnversionedFiles(changeListManager.defaultChangeList)
  }
}