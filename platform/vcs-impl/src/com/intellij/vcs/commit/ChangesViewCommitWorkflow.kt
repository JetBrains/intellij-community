// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitResultHandler
import com.intellij.openapi.vcs.checkin.CheckinHandler

private val LOG = logger<ChangesViewCommitWorkflow>()

internal class CommitState(val changes: List<Change>, val commitMessage: String)

class ChangesViewCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  private val vcsManager = ProjectLevelVcsManager.getInstance(project)

  override val isDefaultCommitEnabled: Boolean get() = true

  internal var areCommitOptionsCreated: Boolean = false
  internal lateinit var commitState: CommitState

  init {
    val connection = project.messageBus.connect()
    connection.subscribe(VCS_CONFIGURATION_CHANGED, VcsListener {
      Disposer.dispose(connection)

      runInEdt { updateVcses(vcsManager.allActiveVcss.toSet()) }
    })
  }

  override fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) {
    if (result == CheckinHandler.ReturnResult.COMMIT) doCommit()
  }

  private fun doCommit() {
    LOG.debug("Do actual commit")
    val committer = LocalChangesCommitter(project, commitState.changes, commitState.commitMessage, commitContext, commitHandlers)

    committer.addResultHandler(DefaultCommitResultHandler(committer))
    committer.addResultHandler(ResultHandler(this))
    committer.runCommit("Commit Changes", false)
  }

  // TODO Looks like CommitContext should also be cleared
  private class ResultHandler(private val workflow: ChangesViewCommitWorkflow) : CommitResultHandler {
    override fun onSuccess(commitMessage: String) = clearCommitOptions()
    override fun onFailure() = clearCommitOptions()

    private fun clearCommitOptions() = runInEdt {
      workflow.clearCommitOptions()
      workflow.areCommitOptionsCreated = false
    }
  }
}