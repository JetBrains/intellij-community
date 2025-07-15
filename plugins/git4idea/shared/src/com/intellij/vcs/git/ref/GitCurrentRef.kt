// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.ref

import git4idea.GitReference
import git4idea.GitReference.Companion.BRANCH_NAME_HASHING_STRATEGY
import git4idea.GitStandardLocalBranch
import git4idea.GitTag
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface GitCurrentRef {
  fun matches(ref: GitReference): Boolean

  @ApiStatus.Internal
  @Serializable
  data class LocalBranch(val branch: GitStandardLocalBranch) : GitCurrentRef {
    override fun matches(ref: GitReference): Boolean =
      ref is GitStandardLocalBranch &&
      BRANCH_NAME_HASHING_STRATEGY.equals(branch.name, ref.name)
  }

  @ApiStatus.Internal
  @Serializable
  data class Tag(val tag: GitTag) : GitCurrentRef {
    override fun matches(ref: GitReference): Boolean = ref == tag
  }

  companion object {
    fun wrap(ref: GitReference?): GitCurrentRef? = when (ref) {
      is GitStandardLocalBranch -> LocalBranch(ref)
      is GitTag -> Tag(ref)
      else -> null
    }
  }
}