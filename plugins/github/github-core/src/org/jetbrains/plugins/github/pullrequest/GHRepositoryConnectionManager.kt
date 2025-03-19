// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.util.serviceGet
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManager
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManagerImpl
import git4idea.remote.hosting.ValidatingHostedGitRepositoryConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

@Service(Service.Level.PROJECT)
internal class GHRepositoryConnectionManager(project: Project, parentCs: CoroutineScope) :
  SingleHostedGitRepositoryConnectionManager<GHGitRepositoryMapping, GithubAccount, GHRepositoryConnection> {
  private val cs = parentCs.childScope(javaClass.name)

  private val dataContextRepository = project.serviceGet<GHPRDataContextRepository>()

  private val repositoriesManager = project.service<GHHostedRepositoriesManager>()
  private val accountManager = service<GHAccountManager>()

  private val connectionFactory =
    ValidatingHostedGitRepositoryConnectionFactory({ repositoriesManager }, { accountManager }) { repo, account, tokenState ->
      createConnection(this, tokenState, repo, account)
    }

  private val delegate = SingleHostedGitRepositoryConnectionManagerImpl(parentCs, connectionFactory)

  override val connectionState: StateFlow<GHRepositoryConnection?>
    get() = delegate.connectionState

  init {
    cs.launch {
      accountManager.accountsState.collect {
        val currentAccount = connectionState.value?.account
        if (currentAccount != null && !it.contains(currentAccount)) {
          closeConnection()
        }
      }
    }
  }

  private suspend fun createConnection(
    connectionScope: CoroutineScope,
    tokenState: StateFlow<String>,
    repo: GHGitRepositoryMapping,
    account: GithubAccount,
  ): GHRepositoryConnection {
    val tokenSupplier = GithubApiRequestExecutor.MutableTokenSupplier(account.server, tokenState.value)
    connectionScope.launch {
      tokenState.collect {
        tokenSupplier.token = it
      }
    }
    val executor = GithubApiRequestExecutor.Factory.getInstance().create(tokenSupplier)

    val dataContext = dataContextRepository().getContext(repo.repository, repo.remote, account, executor)
    connectionScope.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      finally {
        dataContextRepository().clearContext(repo.repository)
      }
    }
    return GHRepositoryConnection(connectionScope, repo, account, dataContext)
  }

  override suspend fun openConnection(repo: GHGitRepositoryMapping, account: GithubAccount): GHRepositoryConnection? =
    delegate.openConnection(repo, account)

  override suspend fun closeConnection() = delegate.closeConnection()
}
