// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.vcs.commit.CommitHandlersNotifier
import com.intellij.vcs.commit.EdtCommitResultHandler
import com.intellij.vcs.commit.NonModalCommitWorkflow
import git4idea.GitVcs
import git4idea.i18n.GitBundle.message

private val LOG = logger<GitStageCommitWorkflow>()

private fun GitStageTracker.RootState.getFullyStagedPaths(): Collection<FilePath> =
  statuses.values
    .filter { it.getStagedStatus() != null &&
              it.getStagedStatus() != FileStatus.DELETED &&
              it.getUnStagedStatus() == null }
    .map { it.path(ContentVersion.STAGED) }

class GitStageCommitWorkflow(project: Project) : NonModalCommitWorkflow(project) {
  override val isDefaultCommitEnabled: Boolean get() = true

  internal var trackerState: GitStageTracker.State = GitStageTracker.State.EMPTY
  internal lateinit var commitState: GitStageCommitState

  init {
    updateVcses(setOf(GitVcs.getInstance(project)))
  }

  override fun executeCustom(executor: CommitExecutor, session: CommitSession): Boolean = error("Not supported currently")

  override fun processExecuteDefaultChecksResult(result: CheckinHandler.ReturnResult) {
    if (result == CheckinHandler.ReturnResult.COMMIT) doCommit()
  }

  private fun doCommit() {
    LOG.debug("Do actual commit")

    val fullyStaged = trackerState.rootStates.mapValues { it.value.getFullyStagedPaths() }

    with(GitStageCommitter(project, commitState, fullyStaged, commitContext)) {
      addResultHandler(CommitHandlersNotifier(commitHandlers))
      addResultHandler(getCommitEventDispatcher())
      addResultHandler(GitStageShowNotificationCommitResultHandler(this))
      addResultHandler(EdtCommitResultHandler(getEndExecutionHandler()))

      runCommit(message("stage.commit.process"), false)
    }
  }
}