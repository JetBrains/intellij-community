// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.repo

import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.git.ref.GitCurrentRef
import git4idea.GitReference
import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface GitRepositoryState {
  val currentRef: GitCurrentRef?
  val revision: @NlsSafe GitHash?
  val localBranches: Set<GitStandardLocalBranch>
  val remoteBranches: Set<GitStandardRemoteBranch>
  val tags: Set<GitTag>
  val recentBranches: List<GitStandardLocalBranch>
  val operationState: GitOperationState

  val currentBranch: GitStandardLocalBranch? get() = (currentRef as? GitCurrentRef.LocalBranch)?.branch

  /**
   * For a fresh repository a list of local branches is empty.
   * However, it still makes sense to show the current branch in the UI.
   */
  val localBranchesOrCurrent: Set<GitStandardLocalBranch>
    get() = localBranches.ifEmpty { setOfNotNull(currentBranch) }

  fun isCurrentRef(ref: GitReference): Boolean = currentRef?.matches(ref) ?: false

  fun getDisplayableBranchText(): @Nls String

  fun getTrackingInfo(branch: GitStandardLocalBranch): GitStandardRemoteBranch?
}
