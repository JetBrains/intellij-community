// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.branch

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.util.NlsActions
import git4idea.GitBranch
import git4idea.GitTag
import git4idea.actions.tag.GitSingleRefAction
import java.util.function.Supplier

abstract class GitSingleBranchAction(dynamicText: Supplier<@NlsActions.ActionText String>) : GitSingleRefAction<GitBranch>(dynamicText) {

  constructor() : this(Presentation.NULL_STRING)

  open val disabledForLocal: Boolean = false
  open val disabledForRemote: Boolean = false

  override fun getRef(branches: List<GitBranch>?, tags: List<GitTag>?): GitBranch? = branches?.singleOrNull()

  override fun isDisabledForRef(ref: GitBranch): Boolean {
    if (disabledForLocal) {
      if (!ref.isRemote) return true
    }

    if (disabledForRemote) {
      if (ref.isRemote) return true
    }
    return false
  }
}
