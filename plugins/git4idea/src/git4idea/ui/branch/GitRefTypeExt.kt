// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

internal fun GitRefType.getText(): @Nls String = when (this) {
  GitBranchType.LOCAL -> GitBundle.message("group.Git.Local.Branch.title")
  GitBranchType.REMOTE -> GitBundle.message("group.Git.Remote.Branch.title")
  GitBranchType.RECENT -> GitBundle.message("group.Git.Recent.Branch.title")
  GitTagType -> GitBundle.message("group.Git.Tags.title")
}

internal fun GitRefType.getInRepoText(repoShortName: String): @Nls String = when (this) {
  GitBranchType.LOCAL -> GitBundle.message("branches.local.branches.in.repo", repoShortName)
  GitBranchType.REMOTE -> GitBundle.message("branches.remote.branches.in.repo", repoShortName)
  GitBranchType.RECENT -> GitBundle.message("group.Git.Recent.Branch.in.repo.title", repoShortName)
  GitTagType -> GitBundle.message("branches.tags.in.repo", repoShortName)
}

internal fun GitRefType.getCommonText(): @Nls String = when (this) {
  GitBranchType.LOCAL -> GitBundle.message("common.local.branches")
  GitBranchType.REMOTE -> GitBundle.message("common.remote.branches")
  GitBranchType.RECENT -> getText()
  GitTagType -> GitBundle.message("common.tags")
}