// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import git4idea.repo.GitRepository

/**
 * Worktree support for the project: unsupported (feature off / no repositories), a single repository,
 * or multiple repositories. See [GitWorkingTreesService.getWorktreeSupportStatus].
 */
internal sealed class GitWorktreeSupportStatus {
  data object Unsupported : GitWorktreeSupportStatus()
  data class SingleRepository(val repository: GitRepository) : GitWorktreeSupportStatus()
  data class MultipleRepository(val repositories: List<GitRepository>) : GitWorktreeSupportStatus()
}
