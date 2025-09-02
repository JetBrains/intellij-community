// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import git4idea.repo.GitRemote

/**
 * The naming conventions of SVN remote branches are slightly different from the ordinary remote branches.
 *
 * No remote is specified: dot (".") is used as a remote.
 * Remote branch name has "refs/remotes/branch" format, i. e. it doesn't have a remote prefix.
 *
 * Because of these differences, GitSvnRemoteBranch doesn't [GitStandardRemoteBranch].
 */
class GitSvnRemoteBranch(private val ref: String) : GitRemoteBranch(ref, GitRemote.DOT) {
  override val nameForRemoteOperations: String = ref

  override val nameForLocalOperations: String = ref

  override val fullName: String = ref
}
