// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import git4idea.config.GitVcsSettings
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

data class GitFetchSpec @JvmOverloads constructor(
  val repository: GitRepository,
  val remote: GitRemote,
  val refspec: String? = null,
  val unshallow: Boolean = false,
  val updateHeadOk: Boolean = false, // allow fetch to update the head which corresponds to the current branch
  val fetchTagsMode: GitFetchTagsMode = GitVcsSettings.getInstance(repository.project).fetchTagsMode,
) {
  fun asParams(): Array<String> {
    return buildList {
      if (refspec != null) add(refspec)
      add(NO_RECURSE_SUBMODULES)
      fetchTagsMode.param?.let { add(it) }
      if (unshallow) add(UNSHALLOW)
      if (updateHeadOk) add(UPDATE_HEAD_OK)
    }.toTypedArray()
  }

  companion object {
    private const val NO_RECURSE_SUBMODULES = "--recurse-submodules=no"
    private const val UNSHALLOW = "--unshallow"
    private const val UPDATE_HEAD_OK = "--update-head-ok"
  }
}