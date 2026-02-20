// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import com.intellij.externalProcessAuthHelper.AuthenticationMode
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class GitFetchSpec(
  val repository: GitRepository,
  val remote: GitRemote,
  val refspec: String? = null,
  val unshallow: Boolean = false,
  val fetchTagsMode: GitFetchTagsMode = GitVcsSettings.getInstance(repository.project).fetchTagsMode,
  val authMode: AuthenticationMode? = null,
) {
  internal constructor(repository: GitRepository, remote: GitRemote, authMode: AuthenticationMode) :
    this(repository, remote, refspec = null, authMode = authMode)

  fun asParams(): Array<String> {
    return buildList {
      if (refspec != null) add(refspec)
      add(NO_RECURSE_SUBMODULES)
      fetchTagsMode.param?.let { add(it) }
      if (unshallow) add(UNSHALLOW)
    }.toTypedArray()
  }

  companion object {
    private const val NO_RECURSE_SUBMODULES = "--recurse-submodules=no"
    private const val UNSHALLOW = "--unshallow"
  }
}