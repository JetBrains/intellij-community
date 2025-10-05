// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface VcsLogNavigatable {
  val repository: GitRepository?

  class Branch(override val repository: GitRepository?, val branchName: String) : VcsLogNavigatable

  class Ref(override val repository: GitRepository?, val refName: String) : VcsLogNavigatable
}