// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.repo

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.vcs.FilePath
import com.intellij.platform.vcs.impl.shared.RepositoryId
import com.intellij.vcs.git.ref.GitFavoriteRefs
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GitRepositoryModel: Comparable<GitRepositoryModel> {
  val repositoryId: RepositoryId
  val shortName: String
  val state: GitRepositoryState
  val favoriteRefs: GitFavoriteRefs
  val root: FilePath

  val isSubmodule: Boolean

  /**
   * Path of the '.git' directory shared by all working trees of the underlying git repository
   * (`git rev-parse --git-common-dir`). Equal for a repository and each of its linked working trees, so it identifies
   * the underlying repository regardless of which of its working trees is registered as a VCS root.
   *
   * @see com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil.mergeLinkedWorktreeRepositories
   */
  val commonGitDirPath: @NlsSafe String

  override fun compareTo(other: GitRepositoryModel): Int =
    NaturalComparator.INSTANCE.compare(shortName, other.shortName)
}
