// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.ref

import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class GitReferencesSet(
  val localBranches: Set<GitStandardLocalBranch>,
  val remoteBranches: Set<GitStandardRemoteBranch>,
  val tags: Set<GitTag>,
) {
  companion object {
    val EMPTY: GitReferencesSet = GitReferencesSet(emptySet(), emptySet(), emptySet())
  }
}