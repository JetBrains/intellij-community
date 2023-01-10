// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManager
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManagerImpl
import git4idea.remote.hosting.ValidatingHostedGitRepositoryConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

internal typealias GHRepositoryConnectionManager = SingleHostedGitRepositoryConnectionManager<GHGitRepositoryMapping, GithubAccount, GHRepositoryConnection>

internal fun GHRepositoryConnectionManager(scope: CoroutineScope,
                                           repositoriesManager: GHHostedRepositoriesManager,
                                           accountManager: GHAccountManager,
                                           dataContextRepository: GHPRDataContextRepository): GHRepositoryConnectionManager {
  val connectionFactory = ValidatingHostedGitRepositoryConnectionFactory(repositoriesManager,
                                                                         accountManager) { repo, account, tokenState ->
    val tokenSupplier = GithubApiRequestExecutor.MutableTokenSupplier(tokenState.value)
    launch {
      tokenState.collect {
        tokenSupplier.token = it
      }
    }
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(tokenSupplier)

    val dataContext = dataContextRepository.getContext(repo.repository, repo.remote, account, executor)
    launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      finally {
        dataContextRepository.clearContext(repo.repository)
      }
    }
    GHRepositoryConnection(this, repo, account, dataContext)
  }

  return SingleHostedGitRepositoryConnectionManagerImpl(scope, connectionFactory)
}
