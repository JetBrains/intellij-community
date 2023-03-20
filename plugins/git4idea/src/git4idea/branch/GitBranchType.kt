// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.BranchType
import git4idea.GitBranch

enum class GitBranchType(private val myName: String) : BranchType {
  LOCAL("LOCAL"), REMOTE("REMOTE");

  override fun getName(): String {
    return myName
  }

  companion object {
    fun of(branch: GitBranch) = if (branch.isRemote) REMOTE else LOCAL
  }
}
