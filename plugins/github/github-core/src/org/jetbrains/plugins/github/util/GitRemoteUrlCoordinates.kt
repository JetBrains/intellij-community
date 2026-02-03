// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.util.NlsSafe
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

@Deprecated("extracted to collab",
            replaceWith = ReplaceWith("GitRemoteUrlCoordinates", "git4idea.remote.GitRemoteUrlCoordinates"))
class GitRemoteUrlCoordinates(@NlsSafe val url: String, val remote: GitRemote, val repository: GitRepository) {

  constructor(extracted: git4idea.remote.GitRemoteUrlCoordinates) :
    this(extracted.url, extracted.remote, extracted.repository)

  fun toExtracted(): git4idea.remote.GitRemoteUrlCoordinates =
    git4idea.remote.GitRemoteUrlCoordinates(url, remote, repository)

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