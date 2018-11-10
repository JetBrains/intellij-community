// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import git4idea.commands.GitRebaseCommandResult.CancelState.*

class GitRebaseCommandResult private constructor(val commandResult: GitCommandResult,
                                                 private val cancelState: CancelState) : GitCommandResult(commandResult.hasStartFailed(),
                                                                                                          commandResult.exitCode,
                                                                                                          commandResult.output,
                                                                                                          commandResult.errorOutput) {

  companion object {
    @JvmStatic fun normal(commandResult: GitCommandResult) = GitRebaseCommandResult(commandResult, NOT_CANCELLED)
    @JvmStatic fun cancelledInCommitList(commandResult: GitCommandResult) = GitRebaseCommandResult(commandResult, COMMIT_LIST_CANCELLED)
    @JvmStatic fun cancelledInCommitMessage(commandResult: GitCommandResult) = GitRebaseCommandResult(commandResult, EDITOR_CANCELLED)
  }

  private enum class CancelState {
    NOT_CANCELLED,
    COMMIT_LIST_CANCELLED,
    EDITOR_CANCELLED
  }

  fun wasCancelledInCommitList() = cancelState == COMMIT_LIST_CANCELLED

  fun wasCancelledInCommitMessage() = cancelState == EDITOR_CANCELLED
}