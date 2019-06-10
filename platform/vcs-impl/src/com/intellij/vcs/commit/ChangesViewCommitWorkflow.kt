// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.impl.PartialChangesUtil

private val LOG = logger<ChangesViewCommitWorkflow>()

internal class CommitState(val changes: List<Change>, val commitMessage: String)

class ChangesViewCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  private val vcsManager = ProjectLevelVcsManager.getInstance(project)
  private val changeListManager = ChangeListManager.getInstance(project)

  override val isDefaultCommitEnabled: Boolean get() = true

  internal var areCommitOptionsCreated: Boolean = false
  internal lateinit var commitState: CommitState

  init {
    updateVcses(vcsManager.allActiveVcss.toSet())
  }

  internal fun getAffectedChangeList(changes: Collection<Change>): LocalChangeList =
    changes.firstOrNull()?.let { changeListManager.getChangeList(it) } ?: changeListManager.defaultChangeList

  override fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) {
    if (result == CheckinHandler.ReturnResult.COMMIT) doCommit()
  }

  override fun executeCustom(executor: CommitExecutor, session: CommitSession) =
    executeCustom(executor, session, commitState.changes, commitState.commitMessage)

  override fun processExecuteCustomChecksResult(executor: CommitExecutor, session: CommitSession, result: CheckinHandler.ReturnResult) {
    if (result == CheckinHandler.ReturnResult.COMMIT) {
      doCommitCustom(executor, session, commitState.changes, commitState.commitMessage)
    }
  }

  override fun doRunBeforeCommitChecks(checks: Runnable) =
    PartialChangesUtil.runUnderChangeList(project, getAffectedChangeList(commitState.changes), checks)

  private fun doCommit() {
    LOG.debug("Do actual commit")
    val committer = LocalChangesCommitter(project, commitState.changes, commitState.commitMessage, commitContext, commitHandlers)

    committer.addResultHandler(DefaultCommitResultHandler(committer))
    committer.addResultHandler(ResultHandler(this))
    committer.runCommit("Commit Changes", false)
  }

  private class ResultHandler(private val workflow: ChangesViewCommitWorkflow) : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = resetState()
    override fun onFailure() = resetState()

    private fun resetState() = runInEdt {
      workflow.disposeCommitOptions()
      workflow.areCommitOptionsCreated = false

      workflow.clearCommitContext()
    }
  }
}