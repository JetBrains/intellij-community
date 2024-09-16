// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.util.NlsActions
import git4idea.GitBranch
import git4idea.actions.ref.GitSingleRefAction
import git4idea.repo.GitRepository
import java.util.function.Supplier

abstract class GitSingleBranchAction(dynamicText: Supplier<@NlsActions.ActionText String>) : GitSingleRefAction<GitBranch>(dynamicText) {
  override val refClass = GitBranch::class

  constructor() : this(Presentation.NULL_STRING)

  open val disabledForLocal: Boolean = false
  open val disabledForRemote: Boolean = false

  override fun isEnabledForRef(ref: GitBranch, repositories: List<GitRepository>) = when {
    disabledForLocal && !ref.isRemote -> false
    disabledForRemote && ref.isRemote -> false
    else -> true
  }
}
