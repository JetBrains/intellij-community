// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import org.jetbrains.plugins.github.api.GHRepositoryPath

class GHRepositoryPermission(id: String,
                             nameWithOwner: String,
                             val viewerPermission: GHRepositoryPermissionLevel?,
                             val mergeCommitAllowed: Boolean,
                             val squashMergeAllowed: Boolean,
                             val rebaseMergeAllowed: Boolean)
  : GHNode(id) {
  val path: GHRepositoryPath

  init {
    val split = nameWithOwner.split('/')
    path = GHRepositoryPath(split[0], split[1])
  }
}