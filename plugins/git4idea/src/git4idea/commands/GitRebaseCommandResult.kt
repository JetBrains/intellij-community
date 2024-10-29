// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import git4idea.rebase.GitRebaseEditingResult

class GitRebaseCommandResult @JvmOverloads constructor(
  val commandResult: GitCommandResult,
  private val editingResult: GitRebaseEditingResult? = null,
) : GitCommandResult(commandResult.hasStartFailed(),
                     commandResult.exitCode,
                     commandResult.output,
                     commandResult.errorOutput,
                     commandResult.myRootName) {

  companion object {
    @JvmStatic
    fun normal(commandResult: GitCommandResult) = GitRebaseCommandResult(commandResult, null)
  }

  fun wasCancelledInCommitList() = editingResult == GitRebaseEditingResult.CommitListEditorCancelled

  fun wasCancelledInCommitMessage() = editingResult == GitRebaseEditingResult.UnstructuredEditorCancelled

  fun getFailureCause(): Exception? = (editingResult as? GitRebaseEditingResult.Failed)?.cause
}