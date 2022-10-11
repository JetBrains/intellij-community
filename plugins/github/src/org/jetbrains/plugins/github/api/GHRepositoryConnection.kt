// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import git4idea.remote.hosting.HostedGitRepositoryConnection
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

class GHRepositoryConnection(override val repo: GHGitRepositoryMapping,
                             override val account: GithubAccount,
                             token: String)
  : HostedGitRepositoryConnection<GHGitRepositoryMapping, GithubAccount> {

  private val tokenSupplier = GithubApiRequestExecutor.MutableTokenSupplier(token)
  val executor: GithubApiRequestExecutor = GithubApiRequestExecutor.Factory.getInstance().create(tokenSupplier)

  var token: String
    get() = tokenSupplier.token
    set(value) {
      tokenSupplier.token = value
    }


  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHRepositoryConnection) return false

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