// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model

import com.intellij.collaboration.async.combineState
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModelBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.authentication.GitLabCredentials
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.createSingleProjectAndAccountState
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@ApiStatus.Internal
class GitLabRepositoryAndAccountSelectorViewModel(
  internal val project: Project,
  private val scope: CoroutineScope,
  projectsManager: GitLabProjectsManager,
  val accountManager: GitLabAccountManager,
  onSelected: suspend (GitLabProjectMapping, GitLabAccount) -> Unit,
) : RepositoryAndAccountSelectorViewModelBase<GitLabProjectMapping, GitLabAccount>(
  scope,
  projectsManager,
  accountManager,
  onSelected
) {

  private val singleProjectAndAccountState = createSingleProjectAndAccountState(scope, projectsManager, accountManager)
  val isLoginAvailable: StateFlow<Boolean> =
    combineState(scope, repoSelectionState, accountSelectionState, missingCredentialsState, ::isLoginAvailable)

  private fun isLoginAvailable(repo: GitLabProjectMapping?, account: GitLabAccount?, tokenMissing: Boolean?): Boolean =
    repo != null && (account == null || tokenMissing == true)

  private val _loginRequestsFlow = MutableSharedFlow<LoginRequest>()
  val loginRequestsFlow: Flow<LoginRequest> = _loginRequestsFlow.asSharedFlow()

  init {
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      singleProjectAndAccountState.collect {
        if (it != null) {
          repoSelectionState.value = it.first
          accountSelectionState.value = it.second
        }
      }
    }
  }

  fun requestLogin(loginType: LoginRequest.Type, forceNewAccount: Boolean, submit: Boolean) {
    val repo = repoSelectionState.value ?: return
    val account = if (forceNewAccount) null else accountSelectionState.value
    scope.launch {
      _loginRequestsFlow.emit(LoginRequest(repo, account, loginType, submit))
    }
  }

  fun authenticateAccount(account: GitLabAccount, credentials: GitLabCredentials, submit: Boolean) {
    scope.launch {
      accountManager.updateAccount(account, credentials)
      accountSelectionState.value = account
      if (submit) {
        submitSelection()
      }
    }
  }
}

@ApiStatus.Internal
data class LoginRequest(
  val repo: GitLabProjectMapping,
  val account: GitLabAccount? = null,
  val type: Type,
  val submit: Boolean,
) {
  enum class Type {
    TOKEN,
    OAUTH,
    OAUTH_CUSTOM_SERVER
  }
}