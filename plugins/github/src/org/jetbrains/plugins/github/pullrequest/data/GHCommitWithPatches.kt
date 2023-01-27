// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.diff.impl.patch.FilePatch

class GHCommitWithPatches(val sha: String, val parents: List<String>, val patches: List<FilePatch>) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHCommitWithPatches) return false

    return sha == other.sha
  }

  override fun hashCode(): Int {
    return sha.hashCode()
  }
}