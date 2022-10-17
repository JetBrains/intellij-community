// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import git4idea.remote.hosting.HostedGitRepositoryConnection
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

data class GHRepositoryConnection(override val repo: GHGitRepositoryMapping,
                                  override val account: GithubAccount,
                                  val executor: GithubApiRequestExecutor)
  : HostedGitRepositoryConnection<GHGitRepositoryMapping, GithubAccount>