// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import git4idea.remote.hosting.HostedGitRepositoryConnection
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

class GitLabProjectConnection(
  override val repo: GitLabProjectMapping,
  override val account: GitLabAccount,
  internal var token: String
) : HostedGitRepositoryConnection<GitLabProjectMapping, GitLabAccount> {

  val apiClient: GitLabApi = GitLabApi { token }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabProjectConnection) return false

    if (repo != other.repo) return false
    if (account != other.account) return false

    return true
  }

  override fun hashCode(): Int {
    var result = repo.hashCode()
    result = 31 * result + account.hashCode()
    return result
  }
}