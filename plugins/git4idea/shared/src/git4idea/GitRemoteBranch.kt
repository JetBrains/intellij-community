// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.util.NlsSafe
import git4idea.repo.GitRemote

abstract class GitRemoteBranch protected constructor(name: String, val remote: GitRemote) : GitBranch(name) {
  /**
   * The name of this remote branch to be used in remote operations: fetch, push, pull.
   * It is the name of this branch how it is defined on the remote.
   * For example, "master".
   *
   * @see [nameForLocalOperations]
   */
  abstract val nameForRemoteOperations: @NlsSafe String

  /**
   * The name of this remote branch to be used in local operations: checkout, merge, rebase.
   * It is the name of this branch how it is references in this local repository.
   * For example, "origin/master".
   */
  abstract val nameForLocalOperations: @NlsSafe String

  override val isRemote: Boolean = true
}
