// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.openapi.actionSystem.EdtNoGetDataProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.*
import git4idea.i18n.GitBundle
import git4idea.index.ui.GitStageCommitPanel
import org.jetbrains.annotations.Nls

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
    ui.addDataProvider(EdtNoGetDataProvider { sink -> uiDataSnapshot(sink) })
    ui.addInclusionListener(object : InclusionListener {
      override fun inclusionChanged() {
        updateDefaultCommitActionName()
      }
    }, this)

    setupDumbModeTracking()
    setupCommitHandlersTracking()
    setupCommitChecksResultTracking()
    vcsesChanged()

    commitMessagePolicy.init(this)
  }

  override fun isCommitEmpty(): Boolean = ui.rootsToCommit.isEmpty()

  override suspend fun updateWorkflow(sessionInfo: CommitSessionInfo): Boolean {
    workflow.trackerState = state
    workflow.commitState = GitStageCommitState(ui.rootsToCommit, ui.isCommitAll, getCommitMessage())
    return true
  }

  override fun getDefaultCommitActionName(isAmend: Boolean, isSkipCommitChecks: Boolean): @Nls String {
    if (!ui.isCommitAll || isAmend) return super.getDefaultCommitActionName(isAmend, isSkipCommitChecks)
    return getDefaultCommitAllActionName(isSkipCommitChecks)
  }

  override fun saveCommitMessageBeforeCommit() {
    commitMessagePolicy.onBeforeCommit()
  }

  override fun checkCommit(sessionInfo: CommitSessionInfo): Boolean {
    val superCheckResult = super.checkCommit(sessionInfo)
    ui.commitProgressUi.isEmptyRoots = ui.includedRoots.isEmpty()
    ui.commitProgressUi.isUnmerged = ui.conflictedRoots.any { ui.rootsToCommit.contains(it) }
    ui.commitProgressUi.isCommitAll = ui.isCommitAll && !amendCommitHandler.isAmendCommitMode
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

  companion object {
    private fun getDefaultCommitAllActionName(isSkipCommitChecks: Boolean): @Nls String {
      val actionName = GitBundle.message("stage.commit.all.text")
      val commitText = UIUtil.replaceMnemonicAmpersand(actionName.fixUnderscoreMnemonic())
      if (isSkipCommitChecks) VcsBundle.message("action.commit.anyway.text", commitText)
      return commitText
    }
  }
}
