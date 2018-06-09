// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.vcs.log.Hash
import git4idea.branch.GitBranchUtil
import git4idea.config.GitSharedSettings
import git4idea.repo.GitRepository

/**
 * Checks if there is a protected remote branch among the given branches, and returns one of them, or `null` otherwise.
 *
 * `branches` are given in the "local" format, e.g. `origin/master`.
 */
fun findProtectedRemoteBranch(repository: GitRepository, branches: Collection<String>): String? {
  val settings = GitSharedSettings.getInstance(repository.project)
  // protected branches hold patterns for branch names without remote names
  return repository.branches.remoteBranches.
    filter { settings.isBranchProtected(it.nameForRemoteOperations) }.
    map { it.nameForLocalOperations }.
    firstOrNull { branches.contains(it) }
}

fun findProtectedRemoteBranchContainingCommit(repository: GitRepository, hash: Hash): String? {
  val containingBranches = GitBranchUtil.getBranches(repository.project, repository.root, false, true, hash.asString())
  return findProtectedRemoteBranch(repository, containingBranches)
}

fun isCommitPublished(repository: GitRepository, hash: Hash) : Boolean = findProtectedRemoteBranchContainingCommit(repository, hash) != null
