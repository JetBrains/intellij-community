// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.diff.impl.patch.FilePatch
import org.jetbrains.plugins.github.api.data.GHCommit

class GHCommitWithPatches(val commit: GHCommit,
                          val commitPatches: List<FilePatch>,
                          val cumulativePatches: List<FilePatch>) {

  val sha = commit.oid
  val parents = commit.parents.map { it.oid }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHCommitWithPatches) return false

    if (commit != other.commit) return false

    return true
  }

  override fun hashCode(): Int {
    return commit.hashCode()
  }
}