// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

class GitRemoteUrlCoordinates(val url: String, val remote: GitRemote, val repository: GitRepository) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitRemoteUrlCoordinates) return false

    if (url != other.url) return false

    return true
  }

  override fun hashCode(): Int {
    return url.hashCode()
  }

  override fun toString(): String {
    return "(url='$url', remote=${remote.name}, repository=$repository)"
  }
}