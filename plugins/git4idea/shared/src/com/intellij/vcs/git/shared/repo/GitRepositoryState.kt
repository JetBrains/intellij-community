// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.repo

import com.intellij.vcs.git.shared.ref.GitCurrentRef
import com.intellij.vcs.git.shared.rpc.GitReferencesSet
import git4idea.GitStandardLocalBranch
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
class GitRepositoryState(
  val currentRef: GitCurrentRef?,
  val refs: GitReferencesSet,
  val recentBranches: List<GitStandardLocalBranch>,
)