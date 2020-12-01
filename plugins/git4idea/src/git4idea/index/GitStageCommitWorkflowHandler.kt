// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.vcs.commit.*

class GitStageCommitWorkflowHandler(
  override val workflow: GitStageCommitWorkflow,
  override val ui: NonModalCommitWorkflowUi
) : NonModalCommitWorkflowHandler<GitStageCommitWorkflow, NonModalCommitWorkflowUi>(),
    CommitAuthorTracker by ui {

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)
  override val amendCommitHandler: NonModalAmendCommitHandler = NonModalAmendCommitHandler(this)

  var state: GitStageTracker.State = GitStageTracker.State.EMPTY

  init {
    Disposer.register(ui, this)

    workflow.addListener(this, this)
    workflow.addCommitListener(GitStageCommitStateCleaner(), this)

    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())

    setupDumbModeTracking()
    setupCommitHandlersTracking()
    vcsesChanged()
  }

  override fun isCommitEmpty(): Boolean = !state.hasStagedRoots()

  override fun updateWorkflow() {
    workflow.trackerState = state
    workflow.commitState = GitStageCommitState(state.stagedRoots, getCommitMessage())
  }

  override fun addUnversionedFiles(): Boolean = true
  override fun saveCommitMessage(success: Boolean) = Unit
  override fun refreshChanges(callback: () -> Unit) = callback()

  private inner class GitStageCommitStateCleaner : CommitStateCleaner() {
    override fun resetState() {
      commitAuthor = null
      super.resetState()
    }
  }
}