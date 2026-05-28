// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.openapi.vcs.FilePath
import git4idea.GitBranch

internal class GitWorkingTreeDialogData private constructor(
  val workingTreePath: FilePath,
  val sourceBranch: GitBranch,
  val newBranchName: String?,
) {

  companion object {
    fun createForNewBranch(workingTreePath: FilePath, sourceBranch: GitBranch, newBranchName: String) =
      GitWorkingTreeDialogData(workingTreePath, sourceBranch, newBranchName)

    fun createForExistingBranch(workingTreePath: FilePath, sourceBranch: GitBranch) =
      GitWorkingTreeDialogData(workingTreePath, sourceBranch, null)
  }
}