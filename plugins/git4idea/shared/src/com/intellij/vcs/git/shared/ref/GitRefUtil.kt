// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.ref

import git4idea.GitBranch
import git4idea.GitTag
import org.jetbrains.annotations.NonNls

object GitRefUtil {
  private val knownPrefixes = listOf(GitBranch.REFS_HEADS_PREFIX, GitBranch.REFS_REMOTES_PREFIX, GitTag.REFS_TAGS_PREFIX)

  @JvmStatic
  fun stripRefsPrefix(@NonNls refName: String): @NonNls String {
    for (prefix in knownPrefixes) {
      if (refName.startsWith(prefix)) return refName.substring(prefix.length)
    }

    return refName
  }
}