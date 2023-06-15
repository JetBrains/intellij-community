// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabApiImpl
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneViewModel.UIState
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

internal interface GitLabCloneViewModel {
  val uiState: Flow<UIState>

  val isLoading: Flow<Boolean>
  val accounts: Flow<Set<GitLabAccount>>
  val errorLogin: Flow<Throwable?>
  val selectedItem: Flow<GitLabCloneListItem?>

  val loginModel: GitLabTokenLoginPanelModel
  val accountDetailsProvider: GitLabAccountsDetailsProvider

  fun runTask(block: suspend () -> Unit)

  fun selectItem(item: GitLabCloneListItem?)

  fun switchToLoginPanel()

  fun switchToRepositoryList()

  suspend fun collectAccountRepositories(account: GitLabAccount): List<GitLabCloneListItem>

  suspend fun login()

  enum class UIState {
    LOGIN,
    REPOSITORY_LIST
  }
}

internal class GitLabCloneViewModelImpl(
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager
) : GitLabCloneViewModel {
  private val cs: CoroutineScope = parentCs.childScope()
  private val taskLauncher: SingleCoroutineLauncher = SingleCoroutineLauncher(cs)

  private val _uiState: MutableStateFlow<UIState> = MutableStateFlow(UIState.LOGIN)
  override val uiState: Flow<UIState> = _uiState.asSharedFlow()

  override val isLoading: Flow<Boolean> = taskLauncher.busy
  override val accounts: Flow<Set<GitLabAccount>> = accountManager.accountsState

  private val _errorLogin: MutableStateFlow<Throwable?> = MutableStateFlow(null)
  override val errorLogin: Flow<Throwable?> = _errorLogin.asSharedFlow()

  private val _selectedItem: MutableStateFlow<GitLabCloneListItem?> = MutableStateFlow(null)
  override val selectedItem: Flow<GitLabCloneListItem?> = _selectedItem.asSharedFlow()

  override val loginModel: GitLabTokenLoginPanelModel = GitLabTokenLoginPanelModel(
    requiredUsername = null,
    uniqueAccountPredicate = accountManager::isAccountUnique
  ).apply {
    serverUri = GitLabServerPath.DEFAULT_SERVER.uri
  }

  override val accountDetailsProvider = GitLabAccountsDetailsProvider(cs) { account ->
    val token = accountManager.findCredentials(account) ?: return@GitLabAccountsDetailsProvider null
    GitLabApiImpl { token }
  }

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      accounts.collectLatest { accounts ->
        if (accounts.isNotEmpty()) {
          _uiState.value = UIState.REPOSITORY_LIST
        }
      }
    }

    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      loginModel.loginState.collectLatest { loginState ->
        when (loginState) {
          is LoginModel.LoginState.Connected -> {
            val account = GitLabAccount(name = loginState.username, server = loginModel.getServerPath())
            accountManager.updateAccount(account, loginModel.token)
          }
          LoginModel.LoginState.Connecting -> {
            _errorLogin.value = null
          }
          LoginModel.LoginState.Disconnected -> {}
          is LoginModel.LoginState.Failed -> {
            _errorLogin.value = loginState.error
          }
        }
      }
    }
  }

  override fun runTask(block: suspend () -> Unit) = taskLauncher.launch {
    block()
  }

  override fun selectItem(item: GitLabCloneListItem?) {
    _selectedItem.value = item
  }

  override fun switchToLoginPanel() {
    _uiState.value = UIState.LOGIN
  }

  override fun switchToRepositoryList() {
    _uiState.value = UIState.REPOSITORY_LIST
  }

  override suspend fun collectAccountRepositories(account: GitLabAccount): List<GitLabCloneListItem> {
    val token = accountManager.findCredentials(account) ?: return emptyList() // TODO: missing token
    val apiClient = GitLabApiImpl { token }
    val currentUser = apiClient.graphQL.getCurrentUser(account.server) ?: return emptyList() // TODO: expired token
    val accountRepositories = currentUser.projectMemberships.map { projectMember ->
      GitLabCloneListItem(account, projectMember)
    }

    return accountRepositories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.presentation() })
  }

  override suspend fun login() {
    loginModel.login()
  }
}