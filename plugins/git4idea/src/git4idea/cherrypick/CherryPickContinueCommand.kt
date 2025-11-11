// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.openapi.progress.coroutineToIndicator
import git4idea.cherrypick.GitCherryPickContinueProcess.isEmptyCommit
import git4idea.commands.*
import git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT
import git4idea.repo.GitRepository

internal object CherryPickContinueCommand {

  /**
   * Run git cherry-pick --continue.
   */
  internal suspend fun GitRepository.executeCherryPickContinue(): CherryPickContinueResult = coroutineToIndicator { _ ->
    val conflictDetector = GitSimpleEventDetector(CHERRY_PICK_CONFLICT)
    val localChangesOverwrittenDetector = GitLocalChangesConflictDetector()

    val handler = GitLineHandler(project, root, GitCommand.CHERRY_PICK).apply {
      addParameters("--continue")
      addLineListener(conflictDetector)
      addLineListener(localChangesOverwrittenDetector)
    }

    val commandResult = Git.getInstance().runCommand(handler)
    when {
      commandResult.success() -> CherryPickContinueResult.Success
      localChangesOverwrittenDetector.isDetected -> CherryPickContinueResult.LocalChangesOverwritten
      conflictDetector.isDetected -> CherryPickContinueResult.Conflict
      commandResult.errorOutputAsJoinedString.contains(NO_OPERATION_IN_PROGRESS_ERROR) -> CherryPickContinueResult.NoCherryPickInProgress
      commandResult.isEmptyCommit() -> CherryPickContinueResult.EmptyCommit
      else -> CherryPickContinueResult.UnknownError(commandResult)
    }
  }

  private const val NO_OPERATION_IN_PROGRESS_ERROR = "no cherry-pick or revert in progress"

  internal sealed class CherryPickContinueResult {
    data object Success : CherryPickContinueResult()
    data object Conflict : CherryPickContinueResult()
    data object LocalChangesOverwritten : CherryPickContinueResult()
    data class UnknownError(val commandResult: GitCommandResult) : CherryPickContinueResult()
    data object NoCherryPickInProgress : CherryPickContinueResult()
    data object EmptyCommit : CherryPickContinueResult()
  }
}