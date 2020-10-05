// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.vcs.commit.AbstractCommitWorkflow
import com.intellij.vcs.commit.EdtCommitResultHandler
import git4idea.GitVcs
import git4idea.i18n.GitBundle.message

private val LOG = logger<GitStageCommitWorkflow>()

class GitStageCommitWorkflow(project: Project) : AbstractCommitWorkflow(project) {
  override val isDefaultCommitEnabled: Boolean get() = true

  internal lateinit var commitState: GitStageCommitState

  init {
    updateVcses(setOf(GitVcs.getInstance(project)))
  }

  override fun runBeforeCommitChecks(executor: CommitExecutor?): CheckinHandler.ReturnResult {
    FileDocumentManager.getInstance().saveAllDocuments()

    return CheckinHandler.ReturnResult.COMMIT
  }

  override fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) {
    if (result == CheckinHandler.ReturnResult.COMMIT) doCommit()
  }

  override fun executeCustom(executor: CommitExecutor, session: CommitSession): Boolean = error("Not supported currently")

  private fun doCommit() {
    LOG.debug("Do actual commit")

    with(GitStageCommitter(project, commitState, commitContext)) {
      addResultHandler(getCommitEventDispatcher())
      addResultHandler(GitStageShowNotificationCommitResultHandler(this))
      addResultHandler(EdtCommitResultHandler(getEndExecutionHandler()))

      runCommit(message("stage.commit.process"), false)
    }
  }
}