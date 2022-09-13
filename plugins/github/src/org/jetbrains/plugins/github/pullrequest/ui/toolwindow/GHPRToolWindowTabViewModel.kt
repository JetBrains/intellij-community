// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.mapStateScoped
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import git4idea.remote.hosting.HostedGitRepositoryAutoConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GHRepositoryConnection
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

internal class GHPRToolWindowTabViewModel(private val scope: CoroutineScope,
                                          private val project: Project,
                                          private val repositoriesManager: GHHostedRepositoriesManager,
                                          private val accountManager: GHAccountManager,
                                          private val connectionManager: GHRepositoryConnectionManager) {

  private val autoConnector = HostedGitRepositoryAutoConnector(scope, connectionManager, repositoriesManager, accountManager)

  val viewState: StateFlow<GHPRTabContentViewModel> = connectionManager.state.mapStateScoped(scope) { scope, connection ->
    if (connection != null) {
      createConnectedVm(scope, connection)
    }
    else {
      createNotConnectedVm(scope)
    }
  }

  private fun createNotConnectedVm(scope: CoroutineScope): GHPRTabContentViewModel.Selectors {
    val selectorVm = GHRepositoryAndAccountSelectorViewModel(scope, repositoriesManager, accountManager) { repo, account ->
      connectionManager.tryConnect(repo, account)
    }
    return GHPRTabContentViewModel.Selectors(selectorVm)
  }

  private fun createConnectedVm(scope: CoroutineScope, connection: GHRepositoryConnection): GHPRTabContentViewModel.PullRequests {
    return GHPRTabContentViewModel.PullRequests(scope, project.service(), connection)
  }

  fun canSelectDifferentRepoOrAccount(): Boolean {
    return connectionManager.state.value != null && !autoConnector.canAutoConnectState.value
  }

  fun selectDifferentRepoOrAccount() {
    scope.launch {
      connectionManager.disconnect()
    }
  }
}

internal sealed interface GHPRTabContentViewModel {

  class Selectors(val selectorVm: GHRepositoryAndAccountSelectorViewModel)
    : GHPRTabContentViewModel

  class PullRequests(scope: CoroutineScope,
                     private val dataContextRepo: GHPRDataContextRepository,
                     private val connection: GHRepositoryConnection)
    : GHPRTabContentViewModel, Disposable {

    val loadingModel = GHCompletableFutureLoadingModel<GHPRDataContext>(this)

    val account = connection.account

    init {
      Disposer.register(scope.nestedDisposable(), this)
      reloadContext()
    }

    fun reloadContext() {
      dataContextRepo.clearContext(connection.repo.repository)
      loadingModel.future = dataContextRepo.acquireContext(connection.repo.repository, connection.repo.remote,
                                                           connection.account, connection.executor)
    }

    override fun dispose() {
      dataContextRepo.clearContext(connection.repo.repository)
    }
  }
}
