// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.openapi.vcs.FilePath
import git4idea.GitReference

internal class GitWorkingTreeDialogData private constructor(
  val workingTreePath: FilePath,
  val sourceRef: GitReference,
  val newBranchName: String?,
) {

  companion object {
    fun createForNewBranch(workingTreePath: FilePath, sourceRef: GitReference, newBranchName: String) =
      GitWorkingTreeDialogData(workingTreePath, sourceRef, newBranchName)

    fun createForExistingBranch(workingTreePath: FilePath, sourceRef: GitReference) =
      GitWorkingTreeDialogData(workingTreePath, sourceRef, null)
  }
}