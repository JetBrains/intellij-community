// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.CommitSessionInfo
import com.intellij.vcs.commit.NonModalCommitWorkflow
import com.intellij.vcs.commit.isAmendCommitMode
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

private fun GitStageTracker.RootState.getChangedPaths(): Collection<FilePath> =
  statuses.values
    .filter {
      it.isTracked()
    }
    .map { it.path(ContentVersion.LOCAL) }

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

    commitContext.isCleanupCommitMessage = GitCommitTemplateTracker.getInstance(project).exists()

    val committer = GitStageCommitter(project, commitState, getPathsToStage(), commitContext)
    addCommonResultHandlers(sessionInfo, committer)
    committer.addResultHandler(GitStageShowNotificationCommitResultHandler(committer))

    committer.runCommit(message("stage.commit.process"), false)
  }

  private fun getPathsToStage(): Map<VirtualFile, Collection<FilePath>> {
    return trackerState.rootStates.filter { commitState.roots.contains(it.key) }.mapValues {
      if (commitState.isCommitAll && !commitContext.isAmendCommitMode) {
        return@mapValues it.value.getChangedPaths()
      }
      return@mapValues it.value.getFullyStagedPaths()
    }
  }
}
