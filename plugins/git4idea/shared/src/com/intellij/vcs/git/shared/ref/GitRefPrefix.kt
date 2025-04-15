// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.ref

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@JvmInline
value class GitRefPrefix(val prefix: @NonNls String) {
  fun matches(ref: @NonNls String): Boolean = ref.startsWith(prefix)

  fun append(refName: @NonNls String): @NonNls String = "$prefix$refName"

  companion object {
    val HEADS: GitRefPrefix = GitRefPrefix(GitRefLiterals.HEADS)
    val REMOTES: GitRefPrefix = GitRefPrefix(GitRefLiterals.REMOTES)
    val TAGS: GitRefPrefix = GitRefPrefix(GitRefLiterals.TAGS)
  }
}

@ApiStatus.Internal
object GitRefLiterals {
  const val HEADS: @NonNls String = "refs/heads/"
  const val REMOTES: @NonNls String = "refs/remotes/"
  const val TAGS: @NonNls String = "refs/tags/"
}
