// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

data class GitFetchSpec @JvmOverloads constructor(
  val repository: GitRepository,
  val remote: GitRemote,
  val refspec: String? = null,
  val unshallow: Boolean = false,
)