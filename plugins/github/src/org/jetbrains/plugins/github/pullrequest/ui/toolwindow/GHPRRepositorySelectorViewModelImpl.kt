// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

class GHPRRepositorySelectorViewModelImpl(private val project: Project,
                                          repoManager: GHHostedRepositoriesManager,
                                          private val authManager: GithubAuthenticationManager)
  : GHPRRepositorySelectorViewModel,
    Disposable {

  private val scope = disposingScope(SupervisorJob()) + Dispatchers.Main

  override val repositoriesState = repoManager.knownRepositoriesState.mapState(scope) { it.toList() }

  override val repoSelectionState = MutableStateFlow<GHGitRepositoryMapping?>(null)

  override val accountsState = combineState(scope, authManager.createAccountsFlow(this), repoSelectionState) { accounts, repo ->
    if (repo == null) {
      emptyList()
    }
    else {
      val server = repo.repository.serverPath
      accounts.filter { it.server.equals(server, true) }
    }
  }

  override val accountSelectionState = MutableStateFlow<GithubAccount?>(null)

  private val _selectionFlow = MutableSharedFlow<Pair<GHGitRepositoryMapping, GithubAccount>>()
  override val selectionFlow = _selectionFlow.asSharedFlow()

  init {
    scope.launch {
      repositoriesState.collect { repos ->
        if (repos.isNotEmpty()) {
          repoSelectionState.update { it ?: repos.first() }
        }
      }
    }

    scope.launch {
      accountsState.collect { accounts ->
        if (accounts.isNotEmpty()) {
          accountSelectionState.update { current ->
            current ?: accounts.find { it == authManager.getDefaultAccount(project) } ?: accounts.first()
          }
        }
      }
    }
  }

  override fun loginToGithub(withOAuth: Boolean): GithubAccount? {
    return authManager.requestNewAccountForDefaultServer(project, !withOAuth)
  }

  override fun loginToGhe(): GithubAccount? {
    val server = repoSelectionState.value?.repository?.serverPath ?: return null
    return authManager.requestNewAccountForServer(server, project)
  }

  override fun trySubmitSelection() {
    val repo = repoSelectionState.value ?: return
    val account = accountSelectionState.value ?: return
    scope.launch {
      _selectionFlow.emit(repo to account)
    }
  }

  override fun dispose() = Unit
}
