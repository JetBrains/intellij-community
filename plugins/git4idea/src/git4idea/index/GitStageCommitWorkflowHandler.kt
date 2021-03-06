// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.vcs.commit.*
import git4idea.index.ui.GitStageCommitPanel

class GitStageCommitWorkflowHandler(
  override val workflow: GitStageCommitWorkflow,
  override val ui: GitStageCommitPanel
) : NonModalCommitWorkflowHandler<GitStageCommitWorkflow, NonModalCommitWorkflowUi>(),
    CommitAuthorTracker by ui {

  private val commitMessagePolicy = GitStageCommitMessagePolicy(project)

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
    setupCommitChecksResultTracking()
    vcsesChanged()
    initCommitMessage(false)

    DelayedCommitMessageProvider.init(project, ui) { commitMessagePolicy.getCommitMessage(false) }
  }

  override fun isCommitEmpty(): Boolean = ui.rootsToCommit.isEmpty()

  override fun updateWorkflow() {
    workflow.trackerState = state
    workflow.commitState = GitStageCommitState(ui.rootsToCommit, getCommitMessage())
  }

  override fun addUnversionedFiles(): Boolean = true
  override fun saveCommitMessage(success: Boolean) = commitMessagePolicy.save(getCommitMessage(), success)
  override fun refreshChanges(callback: () -> Unit) = callback()

  private fun initCommitMessage(isAfterCommit: Boolean) = setCommitMessage(commitMessagePolicy.getCommitMessage(isAfterCommit))

  override fun checkCommit(executor: CommitExecutor?): Boolean {
    ui.commitProgressUi.isEmptyRoots = ui.includedRoots.isEmpty()
    val superCheckResult = super.checkCommit(executor)
    return superCheckResult && !ui.commitProgressUi.isEmptyRoots
  }

  private inner class GitStageCommitStateCleaner : CommitStateCleaner() {
    override fun onSuccess(commitMessage: String) {
      commitAuthor = null
      initCommitMessage(true)

      super.onSuccess(commitMessage)
    }
  }
}
