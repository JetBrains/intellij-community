// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.revert

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitActivity
import git4idea.applyChanges.GitApplyChangesProcess
import git4idea.actions.GitAbortOperationAction
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.NonNls

/**
 * Commits should be provided in the "UI" order, i.e. as if `git log --date-order` is called, i.e. in reverse-chronological order.
 */
internal class GitRevertProcess(
  project: Project,
  commits: List<VcsFullCommitDetails>,
) : GitApplyChangesProcess(
  project = project,
  commits = commits,
  operationName = GitBundle.message("revert.operation.name"),
  appliedWord = GitBundle.message("revert.operation.applied"),
  abortCommand = GitAbortOperationAction.Revert(),
  preserveCommitMetadata = false,
  activityName = GitBundle.message("activity.name.revert"),
  activityId = GitActivity.Revert
) {
  private val git = Git.getInstance()

  override fun cleanupBeforeCommit(repository: GitRepository) { }

  override fun generateDefaultMessage(repository: GitRepository, commit: VcsCommitMetadata): @NonNls String =
    "Revert \"${commit.subject}\"\n\nThis reverts commit ${commit.id.toShortString()}"

  override fun findStoppedCommitInSequence(repository: GitRepository, commits: List<VcsCommitMetadata>): VcsCommitMetadata = commits.first()

  override fun applyChanges(repository: GitRepository, commits: Collection<VcsCommitMetadata>, listeners: List<GitLineHandlerListener>): GitCommandResult {
    return git.revert(repository, commits.first().id.asString(), AUTO_COMMIT, *listeners.toTypedArray<GitLineHandlerListener>())
  }

  override fun isEmptyCommit(result: GitCommandResult): Boolean {
    val stdout = result.outputAsJoinedString
    return stdout.contains("nothing to commit") ||
           stdout.contains("nothing added to commit but untracked files present")
  }

  companion object {
    private const val AUTO_COMMIT = true
  }
}