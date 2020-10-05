// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog.showEmptyCommitMessageConfirmation
import com.intellij.vcs.commit.*
import kotlin.properties.Delegates.observable

class GitStageCommitWorkflowHandler(
  override val workflow: GitStageCommitWorkflow,
  override val ui: NonModalCommitWorkflowUi
) : AbstractCommitWorkflowHandler<GitStageCommitWorkflow, NonModalCommitWorkflowUi>() {

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)
  override val amendCommitHandler: AmendCommitHandler = AmendCommitHandlerImpl(this)

  var state: GitStageTracker.State by observable(GitStageTracker.State.EMPTY) { _, _, _ ->
    updateDefaultCommitActionEnabled()
  }

  // TODO clear CommitContext after commit - implement with adding commit options support
  init {
    Disposer.register(ui, this)

    workflow.addListener(this, this)

    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())

    vcsesChanged()
  }

  override fun vcsesChanged() {
    updateDefaultCommitActionEnabled()
    ui.defaultCommitActionName = getCommitActionName()
  }

  override fun executionStarted() = updateDefaultCommitActionEnabled()
  override fun executionEnded() = updateDefaultCommitActionEnabled()

  private fun updateDefaultCommitActionEnabled() {
    ui.isDefaultCommitActionEnabled = !workflow.isExecuting && state.hasStagedRoots()
  }

  override fun checkCommit(executor: CommitExecutor?): Boolean =
    state.stagedRoots.isNotEmpty() && (getCommitMessage().isNotBlank() || showEmptyCommitMessageConfirmation(project))

  override fun updateWorkflow() {
    workflow.commitState = GitStageCommitState(state.stagedRoots, getCommitMessage())
  }

  override fun addUnversionedFiles(): Boolean = true
  override fun saveCommitMessage(success: Boolean) = Unit
  override fun refreshChanges(callback: () -> Unit) = callback()
}