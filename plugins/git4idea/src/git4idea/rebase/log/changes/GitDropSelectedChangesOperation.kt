// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.changes

import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.config.GitVcsSettings
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.interactive.getRebaseUpstreamFor
import git4idea.rebase.log.GitCommitEditingOperation
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.squash.GitSquashOperation
import git4idea.repo.GitRepository
import git4idea.stash.GitChangesSaver
import git4idea.util.GitFileUtils
import org.jetbrains.annotations.NonNls

internal class GitDropSelectedChangesOperation(
  repository: GitRepository,
  private val commit: VcsCommitMetadata,
  private val changes: List<Change>,
) : GitCommitEditingOperation(repository) {
  companion object {
    @NonNls
    private const val FIXUP_COMMIT_MESSAGE_SUFFIX = "\n\nCreated by IntelliJ Git plugin for drop selected changes operation"
  }

  private lateinit var initialHeadPosition: String

  suspend fun execute(): GitCommitEditingOperationResult {
    repository.update()
    initialHeadPosition = repository.currentRevision!!
    val changesSaver = createGitChangesSaver()
    if (!changesSaver.trySaveLocalChanges(listOf(repository.root))) {
      return GitCommitEditingOperationResult.Incomplete
    }
    var result: GitCommitEditingOperationResult = GitCommitEditingOperationResult.Incomplete
    try {
      if (canDropViaAmend()) {
        result = dropViaAmend()
      }
      else {
        val logManager = VcsProjectLog.getInstance(project).logManager
        result = logManager?.runWithFreezing { dropViaRebase() } ?: dropViaRebase()
      }
      return result
    }
    finally {
      when (result) {
        is GitCommitEditingOperationResult.Complete -> {
          changesSaver.load()
        }
        is GitCommitEditingOperationResult.Incomplete -> {
          changesSaver.notifyLocalChangesAreNotRestored(GitBundle.message("rebase.log.changes.action.operation.drop.name"))
        }
      }
    }
  }

  private fun canDropViaAmend() = commit.id.asString() == initialHeadPosition

  private fun dropViaAmend(): GitCommitEditingOperationResult {
    if (!restoreChanges()) {
      return GitCommitEditingOperationResult.Incomplete
    }
    val handler = GitLineHandler(project, repository.root, GitCommand.COMMIT).apply {
      addParameters("--amend", "--no-verify", "--no-edit")
    }
    val result = Git.getInstance().runCommand(handler)
    repository.update()
    if (result.success()) {
      val upstream = getRebaseUpstreamFor(commit)
      return GitCommitEditingOperationResult.Complete(repository, upstream, initialHeadPosition,
                                                      repository.currentRevision!!)
    }
    else {
      notifyGitCommandFailed(result)
      return GitCommitEditingOperationResult.Incomplete
    }
  }

  private fun dropViaRebase(): GitCommitEditingOperationResult {
    if (!createFixupCommit()) {
      return GitCommitEditingOperationResult.Incomplete
    }
    val fixupCommit = try {
      GitLogUtil.collectMetadata(project, repository.root, listOf(GitUtil.HEAD)).single()
    }
    catch (e: VcsException) {
      notifyOperationFailed(e)
      return GitCommitEditingOperationResult.Incomplete
    }
    val commitsToSquash = listOf(fixupCommit, commit)
    return GitSquashOperation(repository).execute(commitsToSquash, commit.fullMessage, initialHeadPosition)
  }

  private fun createFixupCommit(): Boolean {
    if (!restoreChanges()) {
      return false
    }
    val handler = GitLineHandler(project, repository.root, GitCommand.COMMIT).apply {
      addParameters("--no-verify", "-m", "fixup! ${commit.id}$FIXUP_COMMIT_MESSAGE_SUFFIX")
    }
    val result = Git.getInstance().runCommand(handler)
    return if (result.success()) {
      true
    }
    else {
      notifyGitCommandFailed(result)
      false
    }
  }

  private fun restoreChanges(): Boolean {
    val paths = changes.map { change ->
      change.beforeRevision?.file
      ?: change.afterRevision?.file
      ?: error("Can't get a path from a change")
    }
    try {
      if (commit.parents.isNotEmpty()) {
        GitFileUtils.restoreStagedAndWorktree(project, repository.root, paths, "${commit.id}~1")
      }
      else {
        GitFileUtils.deletePaths(project, repository.root, paths)
      }
      return true
    }
    catch (e: VcsException) {
      notifyOperationFailed(e)
      return false
    }
  }

  private fun notifyGitCommandFailed(result: GitCommandResult) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.DROP_CHANGES_FAILED,
      GitBundle.message("rebase.log.changes.drop.failed.title"),
      result.errorOutputAsHtmlString
    )
  }

  private fun notifyOperationFailed(exception: VcsException) {
    VcsNotifier.getInstance(project).notifyError(
      GitNotificationIdsHolder.DROP_CHANGES_FAILED,
      GitBundle.message("rebase.log.changes.drop.failed.title"),
      exception.message
    )
  }

  private suspend fun createGitChangesSaver(): GitChangesSaver {
    val gitSettings = GitVcsSettings.getInstance(project)
    val savePolicy = gitSettings.saveChangesPolicy
    return coroutineToIndicator { indicator ->
      GitChangesSaver.getSaver(
        project,
        Git.getInstance(),
        indicator,
        VcsBundle.message("stash.changes.message", GitBundle.message("rebase.log.changes.action.operation.drop.name")),
        savePolicy
      )
    }
  }
}