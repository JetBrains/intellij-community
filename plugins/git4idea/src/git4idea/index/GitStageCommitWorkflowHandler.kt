// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.vcs.commit.*
import git4idea.index.ui.GitStageCommitPanel

class GitStageCommitWorkflowHandler(
  override val workflow: GitStageCommitWorkflow,
  override val ui: GitStageCommitPanel
) : NonModalCommitWorkflowHandler<GitStageCommitWorkflow, NonModalCommitWorkflowUi>() {

  private val commitMessagePolicy = GitStageCommitMessagePolicy(project, ui.commitMessageUi)

  override val commitPanel: CheckinProjectPanel = CommitProjectPanelAdapter(this)
  override val amendCommitHandler: NonModalAmendCommitHandler = NonModalAmendCommitHandler(this)
  override val commitAuthorTracker: CommitAuthorTracker get() = ui
  var state: GitStageTracker.State = GitStageTracker.State.EMPTY

  init {
    Disposer.register(ui, this)

    workflow.addListener(this, this)
    workflow.addVcsCommitListener(GitStageCommitStateCleaner(), this)
    workflow.addVcsCommitListener(PostCommitChecksRunner(), this)

    ui.addExecutorListener(this, this)
    ui.addDataProvider(createDataProvider())

    setupDumbModeTracking()
    setupCommitHandlersTracking()
    setupCommitChecksResultTracking()
    vcsesChanged()

    commitMessagePolicy.init(this)
  }

  override fun isCommitEmpty(): Boolean = ui.rootsToCommit.isEmpty()

  override suspend fun updateWorkflow(sessionInfo: CommitSessionInfo): Boolean {
    workflow.trackerState = state
    workflow.commitState = GitStageCommitState(ui.rootsToCommit, getCommitMessage())
    return true
  }

  override fun saveCommitMessageBeforeCommit() {
    commitMessagePolicy.onBeforeCommit()
  }

  override fun checkCommit(sessionInfo: CommitSessionInfo): Boolean {
    val superCheckResult = super.checkCommit(sessionInfo)
    ui.commitProgressUi.isEmptyRoots = ui.includedRoots.isEmpty()
    ui.commitProgressUi.isUnmerged = ui.conflictedRoots.any { ui.rootsToCommit.contains(it) }
    return superCheckResult &&
           !ui.commitProgressUi.isEmptyRoots &&
           !ui.commitProgressUi.isUnmerged
  }

  override fun isExecutorEnabled(executor: CommitExecutor): Boolean {
    val session = executor.createCommitSession(CommitContext())
    return session == CommitSession.VCS_COMMIT &&
           super.isExecutorEnabled(executor)
  }

  private inner class GitStageCommitStateCleaner : CommitStateCleaner() {
    override fun onSuccess() {
      ui.commitAuthor = null
      commitMessagePolicy.onAfterCommit()

      super.onSuccess()
    }
  }
}
