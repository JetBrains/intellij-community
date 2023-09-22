// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.openapi.diff.impl.patch.FilePatch

class GitCommitShaWithPatches(val sha: String, val parents: List<String>, val patches: List<FilePatch>) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitCommitShaWithPatches) return false

    return sha == other.sha
  }

  override fun hashCode(): Int = sha.hashCode()

  override fun toString(): String = "GitCommitShaWithPatches(sha='$sha', parents=$parents, patches=$patches)"
}