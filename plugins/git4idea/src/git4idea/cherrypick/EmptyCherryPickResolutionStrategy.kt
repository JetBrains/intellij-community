// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.openapi.vcs.VcsException
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitUtil
import git4idea.applyChanges.GitApplyChangesProcess.Companion.getCommitsDetails
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

internal enum class EmptyCherryPickResolutionStrategy {
  SKIP, CREATE_EMPTY
}

internal fun EmptyCherryPickResolutionStrategy.apply(repository: GitRepository) = when (this) {
  EmptyCherryPickResolutionStrategy.SKIP -> repository.skipCherryPick()
  EmptyCherryPickResolutionStrategy.CREATE_EMPTY -> repository.createEmptyCommit()
}

internal val EmptyCherryPickResolutionStrategy.settingsMessage: @Nls String get() = message(when(this) {
  EmptyCherryPickResolutionStrategy.SKIP -> "settings.cherry.pick.empty.commit.skip"
  EmptyCherryPickResolutionStrategy.CREATE_EMPTY -> "settings.cherry.pick.empty.commit.create"
})

internal fun EmptyCherryPickResolutionStrategy.notificationMessage(appliedCommits: List<VcsCommitMetadata>): @Nls String {
  val baseMessage = when (this) {
    EmptyCherryPickResolutionStrategy.SKIP -> message("cherry.pick.empty.commit.skip.notification.message", appliedCommits.size)
    EmptyCherryPickResolutionStrategy.CREATE_EMPTY -> message("cherry.pick.empty.commit.create.notification.message", appliedCommits.size)
  }

  return buildString {
    append(baseMessage)
    if (appliedCommits.isNotEmpty()) {
      append(UIUtil.BR)
      append(appliedCommits.getCommitsDetails())
    }
  }
}

/**
 * If the last commit in the cherry-pick sequence is already cherry-picked,
 * will remain in cherry-picking state, unless '--skip' operation is explicitly called
 */
private fun GitRepository.skipCherryPick(): GitCommandResult {
  val handler = GitLineHandler(project, root, GitCommand.CHERRY_PICK).apply {
    addParameters("--skip")
  }

  return Git.getInstance().runCommand(handler)
}

private fun GitRepository.createEmptyCommit(@Nls fallbackMessage: String = message("cherry.pick.empty.cherry.pick.commit")): GitCommandResult {
  // Verify that there are actually no staged changes
  val (hasStagedChanges, errorMsg) = try {
    GitUtil.hasLocalChanges(true, project, root) to null
  } catch (e: VcsException) {
    false to e.message
  }

  if (hasStagedChanges) {
    return GitCommandResult.error(errorMsg ?: message("git.empty.commit.not.empty"))
  }

  // Use the prepared MERGE_MSG as the commit message to mimic git's cherry-pick continue behavior
  val messageFile = getRepositoryFiles().mergeMessageFile
  val handler = GitLineHandler(project, root, GitCommand.COMMIT).apply {
    addParameters("--allow-empty")
    if (messageFile.exists()) {
      addParameters("-F", messageFile.absolutePath)
    }
    else {
      // Fallback: ensure non-interactive commit with a generic message if MERGE_MSG is missing
      addParameters("-m", fallbackMessage)
    }
  }

  return Git.getInstance().runCommand(handler)
}

