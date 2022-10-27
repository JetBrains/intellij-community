// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import git4idea.remote.hosting.HostedGitRepositoryConnectionManager
import git4idea.remote.hosting.ValidatingHostedGitRepositoryConnectionManager
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

typealias GHRepositoryConnectionManager = HostedGitRepositoryConnectionManager<GHGitRepositoryMapping, GithubAccount, GHRepositoryConnection>

internal fun GHRepositoryConnectionManager(repositoriesManager: GHHostedRepositoriesManager,
                                           accountManager: GHAccountManager): GHRepositoryConnectionManager =
  ValidatingHostedGitRepositoryConnectionManager(repositoriesManager, accountManager) { repo, account, tokenState ->
    val tokenSupplier = GithubApiRequestExecutor.MutableTokenSupplier(tokenState.value)
    launch {
      tokenState.collect {
        tokenSupplier.token = it
      }
    }
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(tokenSupplier)
    GHRepositoryConnection(this, repo, account, executor)
  }
