// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.branch.BranchType
import com.intellij.ide.util.treeView.PathElementIdProvider
import git4idea.GitBranch
import git4idea.GitReference
import git4idea.GitTag
import git4idea.branch.GitBranchType.LOCAL
import git4idea.branch.GitBranchType.REMOTE
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

sealed interface GitRefType : BranchType, PathElementIdProvider {
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

  override fun getPathElementId() = name

  fun getText(): @Nls String
  fun getInRepoText(repoShortName: String): @Nls String
  fun getCommonText(): @Nls String
}

enum class GitBranchType : GitRefType {
  LOCAL {
    override fun getName() = "LOCAL"

    override fun getText() = GitBundle.message("group.Git.Local.Branch.title")
    override fun getInRepoText(repoShortName: String) = GitBundle.message("branches.local.branches.in.repo", repoShortName)
    override fun getCommonText() = GitBundle.message("common.local.branches")
  },
  REMOTE {
    override fun getName() = "REMOTE"

    override fun getText() = GitBundle.message("group.Git.Remote.Branch.title")
    override fun getInRepoText(repoShortName: String) = GitBundle.message("branches.remote.branches.in.repo", repoShortName)
    override fun getCommonText() = GitBundle.message("common.remote.branches")
  },
  RECENT {
    override fun getName() = "RECENT"

    override fun getText() = GitBundle.message("group.Git.Recent.Branch.title")
    override fun getInRepoText(repoShortName: String) = GitBundle.message("group.Git.Recent.Branch.in.repo.title", repoShortName)
    override fun getCommonText() = getText()
  }
}

object GitTagType : GitRefType {
  override fun getName() = "TAG"

  override fun getText() = GitBundle.message("group.Git.Tags.title")
  override fun getInRepoText(repoShortName: String) = GitBundle.message("branches.tags.in.repo", repoShortName)
  override fun getCommonText() = GitBundle.message("common.tags")
}
