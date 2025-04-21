// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.ref

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class GitFavoriteRefs(val localBranches: Set<String>, val remoteBranches: Set<String>, val tags: Set<String>) {
  val size: Int get() = localBranches.size + remoteBranches.size + tags.size
}
