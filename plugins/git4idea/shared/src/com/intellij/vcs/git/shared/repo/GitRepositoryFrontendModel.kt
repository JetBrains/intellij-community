// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.repo

import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.ref.GitFavoriteRefs
import org.jetbrains.annotations.ApiStatus

// This class is temporarily moved to the shared module until the branch widget can be fully moved to the frontend.
@ApiStatus.Internal
interface GitRepositoryFrontendModel: Comparable<GitRepositoryFrontendModel> {
  val repositoryId: RepositoryId
  val shortName: String
  val state: GitRepositoryState
  val favoriteRefs: GitFavoriteRefs
  val root: VirtualFile?

  override fun compareTo(other: GitRepositoryFrontendModel): Int =
    NaturalComparator.INSTANCE.compare(shortName, other.shortName)
}
