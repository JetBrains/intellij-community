// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.repo

import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.ref.GitFavoriteRefs
import com.intellij.vcs.git.shared.repo.GitRepositoryState

internal class GitRepositoryFrontendModel(
  val repositoryId: RepositoryId,
  val shortName: String,
  var state: GitRepositoryState,
  var favoriteRefs: GitFavoriteRefs,
)