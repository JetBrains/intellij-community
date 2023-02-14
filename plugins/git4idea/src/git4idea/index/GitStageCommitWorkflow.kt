// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.vcs.commit.CommitSessionInfo
import com.intellij.vcs.commit.NonModalCommitWorkflow
import com.intellij.vcs.commit.isCleanupCommitMessage
import git4idea.GitVcs
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitCommitTemplateTracker

private val LOG = logger<GitStageCommitWorkflow>()

private fun GitStageTracker.RootState.getFullyStagedPaths(): Collection<FilePath> =
  statuses.values
    .filter {
      it.getStagedStatus() != null &&
      it.getStagedStatus() != FileStatus.DELETED &&
      it.getUnStagedStatus() == null
    }
    .map { it.path(ContentVersion.STAGED) }

class GitStageCommitWorkflow(project: Project) : NonModalCommitWorkflow(project) {
  override val isDefaultCommitEnabled: Boolean get() = true

  internal var trackerState: GitStageTracker.State = GitStageTracker.State.EMPTY
  internal lateinit var commitState: GitStageCommitState

  init {
    updateVcses(setOf(GitVcs.getInstance(project)))
  }

  override fun performCommit(sessionInfo: CommitSessionInfo) {
    assert(sessionInfo.isVcsCommit) { "Custom commit sessions are not supported with staging area: ${sessionInfo.executor.toString()}" }
    LOG.debug("Do actual commit")

    commitContext.isCleanupCommitMessage = project.service<GitCommitTemplateTracker>().exists()

    val fullyStaged = trackerState.rootStates.filter { commitState.roots.contains(it.key) }.mapValues { it.value.getFullyStagedPaths() }
    val committer = GitStageCommitter(project, commitState, fullyStaged, commitContext)
    addCommonResultHandlers(sessionInfo, committer)
    committer.addResultHandler(GitStageShowNotificationCommitResultHandler(committer))

    committer.runCommit(message("stage.commit.process"), false)
  }
}
