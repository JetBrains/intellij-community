// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.dialog

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import git4idea.GitReference
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorktreeAdditionalConfigCopier
import java.nio.file.Path

internal data class GitWorktreeCreationRequest(
  val repository: GitRepository,
  val workingTreePath: FilePath,
  val branch: WorktreeBranchSpec,
  val setupScriptPath: Path? = null,
  val copyWorktreeIncludeMatches: Boolean = false,
  val additionalSettingsHandles: List<GitWorktreeSettingsPanelHandle> = emptyList(),
  val enabledAdditionalConfigCopiers: Set<GitWorktreeAdditionalConfigCopier> = emptySet(),
)

internal sealed interface WorktreeBranchSpec {
  val sourceRef: GitReference

  /** The branch or tag name that the UI shows for the new worktree. */
  val presentableBranchName: @NlsSafe String

  data class CheckoutExisting(override val sourceRef: GitReference) : WorktreeBranchSpec {
    override val presentableBranchName: @NlsSafe String
      get() = sourceRef.name
  }

  data class CreateNewBranch(override val sourceRef: GitReference, val newBranchName: String) : WorktreeBranchSpec {
    override val presentableBranchName: @NlsSafe String
      get() = newBranchName
  }
}
