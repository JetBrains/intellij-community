// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName

class GHRepository(id: String,
                   val owner: GHRepositoryOwnerName,
                   nameWithOwner: String,
                   val viewerPermission: GHRepositoryPermissionLevel?,
                   val mergeCommitAllowed: Boolean,
                   val squashMergeAllowed: Boolean,
                   val rebaseMergeAllowed: Boolean,
                   @Suppress("MemberVisibilityCanBePrivate") val defaultBranchRef: GHGitRefName,
                   val isFork: Boolean)
  : GHNode(id) {
  val path: GHRepositoryPath
  val defaultBranch = defaultBranchRef.name

  init {
    val split = nameWithOwner.split('/')
    path = GHRepositoryPath(split[0], split[1])
  }
}