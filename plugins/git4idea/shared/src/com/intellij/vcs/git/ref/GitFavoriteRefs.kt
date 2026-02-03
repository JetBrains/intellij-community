// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.ref

import git4idea.GitReference
import git4idea.GitStandardLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitTag
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class GitFavoriteRefs(val localBranches: Set<String>, val remoteBranches: Set<String>, val tags: Set<String>) {
  val size: Int get() = localBranches.size + remoteBranches.size + tags.size

  operator fun contains(ref: GitReference): Boolean = when (ref) {
    is GitStandardLocalBranch -> ref.name in localBranches
    is GitTag -> ref.name in tags
    is GitStandardRemoteBranch -> ref.name in remoteBranches
    else -> false
  }
}
