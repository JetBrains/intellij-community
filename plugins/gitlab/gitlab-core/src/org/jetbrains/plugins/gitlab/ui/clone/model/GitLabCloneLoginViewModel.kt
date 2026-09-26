// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone.model

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabCredentials
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabOAuthLoginOutcome
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabOAuthLoginViewModel
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel

internal sealed interface GitLabCloneLoginViewModel : GitLabClonePanelViewModel {
  val loginSucceeded: Flow<Unit>
}

internal class GitLabCloneTokenLoginViewModel(
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager,
) : GitLabCloneLoginViewModel {
  private val cs: CoroutineScope = parentCs.childScope(this::class)

  private var _selectedAccount: GitLabAccount? = null
  val selectedAccount: GitLabAccount? get() = _selectedAccount
  private val _loginSucceeded = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
  override val loginSucceeded: SharedFlow<Unit> = _loginSucceeded.asSharedFlow()
  val tokenLoginModel: GitLabTokenLoginPanelModel = GitLabTokenLoginPanelModel(
    requiredUsername = null,
    uniqueAccountPredicate = accountManager::isAccountUnique
  )

  init {
    cs.launch {
      with(tokenLoginModel) {
        loginState.collectLatest { loginState ->
          if (loginState is LoginModel.LoginState.Connected) {
            val storedAccount = _selectedAccount ?: GitLabAccount(name = loginState.username, server = getServerPath())
            accountManager.updateAccount(storedAccount, GitLabCredentials.Token(token))
            _loginSucceeded.tryEmit(Unit)
          }
        }
      }
    }
  }

  fun setSelectedAccount(account: GitLabAccount?) {
    _selectedAccount = account
    with(tokenLoginModel) {
      requiredUsername = account?.name
      uniqueAccountPredicate = if (account == null) accountManager::isAccountUnique else { _, _ -> true }
      serverUri = account?.server?.uri ?: GitLabServerPath.DEFAULT_SERVER.uri
    }
  }
}

internal class GitLabCloneLoginEntryViewModel(
  parentCs: CoroutineScope,
  private val project: Project,
  private val accountManager: GitLabAccountManager,
) : GitLabCloneLoginViewModel {
  private val cs: CoroutineScope = parentCs.childScope(this::class)

  private var _selectedAccount: GitLabAccount? = null
  val selectedAccount: GitLabAccount? get() = _selectedAccount
  private val _busyState = MutableStateFlow(false)
  val busyState: StateFlow<Boolean> = _busyState.asStateFlow()

  private val _loginSucceeded = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
  override val loginSucceeded: SharedFlow<Unit> = _loginSucceeded.asSharedFlow()

  fun setSelectedAccount(account: GitLabAccount?) {
    _selectedAccount = account
  }

  fun loginWithOAuth() {
    cs.launch {
      _busyState.value = true
      try {
        val account = _selectedAccount
        val uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean =
          if (account == null) accountManager::isAccountUnique else { _, _ -> true }

        val result = withContext(Dispatchers.EDT) {
          if (account == null) {
            GitLabLoginUtil.logInViaOAuth(project, loginSource = GitLabLoginSource.CLONE, uniqueAccountPredicate = uniqueAccountPredicate)
          }
          else {
            GitLabLoginUtil.reLogInViaOAuth(project,
                                            account,
                                            loginSource = GitLabLoginSource.CLONE,
                                            uniqueAccountPredicate = uniqueAccountPredicate)
          }
        }
        result.asSafely<LoginResult.Success>()?.let {
          accountManager.updateAccount(it.account, it.credentials)
          _loginSucceeded.tryEmit(Unit)
        }
      }
      finally {
        _busyState.value = false
      }
    }
  }
}

internal class GitLabCloneOAuthCustomServerLoginViewModel(
  parentCs: CoroutineScope,
  project: Project,
  private val accountManager: GitLabAccountManager,
) : GitLabCloneLoginViewModel {
  private val cs: CoroutineScope = parentCs.childScope(this::class)

  private var _selectedAccount: GitLabAccount? = null
  val selectedAccount: GitLabAccount? get() = _selectedAccount
  private val _busyState = MutableStateFlow(false)
  val busyState: StateFlow<Boolean> = _busyState.asStateFlow()

  private val _loginSucceeded = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
  override val loginSucceeded: SharedFlow<Unit> = _loginSucceeded.asSharedFlow()
  val loginPanelVM: GitLabOAuthLoginViewModel =
    GitLabOAuthLoginViewModel(project, parentCs, requiredServerPath = null) { server, username ->
      _selectedAccount != null || accountManager.isAccountUnique(server, username)
    }

  init {
    cs.launch {
      loginPanelVM.isLoggingIn.collect { isLoggingIn ->
        _busyState.value = isLoggingIn
      }
    }

    cs.launch {
      loginPanelVM.outcome.collect { outcome ->
        val success = outcome.asSafely<GitLabOAuthLoginOutcome.Success>() ?: return@collect

        val accountToSave = _selectedAccount?.let { existing ->
          GitLabAccount(
            id = existing.id,
            name = success.username,
            server = success.serverPath
          )
        } ?: GitLabAccount(
          name = success.username,
          server = success.serverPath
        )

        accountManager.updateAccount(accountToSave, success.credentials)
        _loginSucceeded.tryEmit(Unit)
      }
    }
  }

  fun setSelectedAccount(account: GitLabAccount?) {
    loginPanelVM.setSelectedAccount(account)
    _selectedAccount = account
  }
}