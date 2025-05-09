// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.repo

import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.git.shared.ref.GitCurrentRef
import com.intellij.vcs.git.shared.ref.GitReferencesSet
import git4idea.GitReference
import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.i18n.GitBundle
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Serializable
@ApiStatus.Internal
class GitRepositoryState(
  val currentRef: GitCurrentRef?,
  @NlsSafe
  val revision: GitHash?,
  val refs: GitReferencesSet,
  val recentBranches: List<GitStandardLocalBranch>,
  val operationState: GitOperationState,
  /**
   * Maps short names of local branches to their upstream branches.
   */
  private val trackingInfo: Map<String, GitStandardRemoteBranch>,
) {
  val currentBranch: GitStandardLocalBranch? get() = (currentRef as? GitCurrentRef.LocalBranch)?.branch

  fun isCurrentRef(ref: GitReference): Boolean = currentRef != null && currentRef.matches(ref)

  fun getDisplayableBranchText(): @Nls String {
    val branchOrEmpty = currentBranch?.name ?: ""
    return when (operationState) {
      GitOperationState.NORMAL -> branchOrEmpty
      GitOperationState.REBASE -> GitBundle.message("git.status.bar.widget.text.rebase", branchOrEmpty)
      GitOperationState.MERGE -> GitBundle.message("git.status.bar.widget.text.merge", branchOrEmpty)
      GitOperationState.CHERRY_PICK -> GitBundle.message("git.status.bar.widget.text.cherry.pick", branchOrEmpty)
      GitOperationState.REVERT -> GitBundle.message("git.status.bar.widget.text.revert", branchOrEmpty)
      GitOperationState.DETACHED_HEAD -> getDetachedHeadDisplayableText()
    }
  }

  fun getTrackingInfo(branch: GitStandardLocalBranch): GitStandardRemoteBranch? = trackingInfo[branch.name]

  private fun getDetachedHeadDisplayableText(): @Nls String =
    if (currentRef is GitCurrentRef.Tag) currentRef.tag.name
    else revision?.hash ?: GitBundle.message("git.status.bar.widget.text.unknown")
}
