// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.combineState
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModelBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

internal class GitLabRepositoryAndAccountSelectorViewModel(
  private val scope: CoroutineScope,
  private val connectionManager: GitLabProjectConnectionManager,
  projectsManager: GitLabProjectsManager,
  private val accountManager: GitLabAccountManager)
  : RepositoryAndAccountSelectorViewModelBase<GitLabProjectMapping, GitLabAccount>(scope, projectsManager, accountManager) {

  val tokenLoginAvailableState: StateFlow<Boolean> =
    combineState(scope, repoSelectionState, accountSelectionState, missingCredentialsState, ::isTokenLoginAvailable)

  private fun isTokenLoginAvailable(repo: GitLabProjectMapping?, account: GitLabAccount?, tokenMissing: Boolean): Boolean =
    repo != null && (account == null || tokenMissing)

  private val _loginRequestsFlow = MutableSharedFlow<TokenLoginRequest>()
  val loginRequestsFlow: Flow<TokenLoginRequest> = _loginRequestsFlow.asSharedFlow()

  fun requestTokenLogin(forceNewAccount: Boolean, submit: Boolean) {
    val repo = repoSelectionState.value ?: return
    val account = if (forceNewAccount) null else accountSelectionState.value
    scope.launch {
      _loginRequestsFlow.emit(TokenLoginRequest(repo, account, submit))
    }
  }

  override fun submitSelection() {
    val repo = repoSelectionState.value ?: return
    val account = accountSelectionState.value ?: return
    scope.launch {
      connectionManager.tryConnect(repo, account)
    }
  }

  inner class TokenLoginRequest(val repo: GitLabProjectMapping,
                                val account: GitLabAccount? = null,
                                private val submit: Boolean) {

    val accounts: Set<GitLabAccount>
      get() = accountManager.accountsState.value

    fun login(account: GitLabAccount, token: String) {
      accountManager.updateAccount(account, token)
      accountSelectionState.value = account
      if (submit) {
        submitSelection()
      }
    }
  }
}