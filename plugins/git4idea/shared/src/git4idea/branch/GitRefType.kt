// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.BranchType
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.branch.GitBranchType.LOCAL
import git4idea.branch.GitBranchType.REMOTE

sealed interface GitRefType : BranchType {
  companion object {
    fun of(reference: GitReference, recent: Boolean = false): GitRefType {
      return when {
        recent -> GitBranchType.RECENT
        reference is GitBranch -> if (reference.isRemote) REMOTE else LOCAL
        reference is GitTag -> return GitTagType
        else -> throw IllegalArgumentException()
      }
    }
  }
}

enum class GitBranchType : GitRefType {
  LOCAL {
    override fun getName() = "LOCAL"
  },
  REMOTE {
    override fun getName() = "REMOTE"
  },
  RECENT {
    override fun getName() = "RECENT"
  }
}

object GitTagType : GitRefType {
  override fun getName() = "TAG"
}
