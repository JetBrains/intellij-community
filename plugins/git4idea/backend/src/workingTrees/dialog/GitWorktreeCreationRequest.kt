// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.dialog

import com.intellij.openapi.vcs.FilePath
import git4idea.GitReference
import git4idea.repo.GitRepository

internal data class GitWorktreeCreationRequest(
  val repository: GitRepository,
  val workingTreePath: FilePath,
  val branch: WorktreeBranchSpec,
)

internal sealed interface WorktreeBranchSpec {
  val sourceRef: GitReference

  data class CheckoutExisting(override val sourceRef: GitReference) : WorktreeBranchSpec
  data class CreateNewBranch(override val sourceRef: GitReference, val newBranchName: String) : WorktreeBranchSpec
}
