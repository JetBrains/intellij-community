// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.openapi.util.NlsSafe
import git4idea.GitRemoteBranch
import git4idea.repo.GitRemote

/**
 * Semi-fake remote branch if pushing to special push specs like "HEAD:refs/for/master".
 */
class GitSpecialRefRemoteBranch(ref: String, remote: GitRemote) : GitRemoteBranch(ref, remote) {
  override val nameForRemoteOperations: @NlsSafe String = ref

  override val nameForLocalOperations: @NlsSafe String = ref

  override val fullName: @NlsSafe String = ref
}
