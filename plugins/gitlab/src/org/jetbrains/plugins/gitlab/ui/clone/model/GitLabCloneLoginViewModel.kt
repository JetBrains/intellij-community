// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone.model

import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabTokenLoginPanelModel

internal interface GitLabCloneLoginViewModel : GitLabClonePanelViewModel {
  val accounts: SharedFlow<Set<GitLabAccount>>
  val tokenLoginModel: GitLabTokenLoginPanelModel
}

internal class GitLabCloneLoginViewModelImpl(
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager
) : GitLabCloneLoginViewModel {
  private val cs: CoroutineScope = parentCs.childScope()

  private var selectedAccount: GitLabAccount? = null
  override val accounts: SharedFlow<Set<GitLabAccount>> = accountManager.accountsState

  override val tokenLoginModel: GitLabTokenLoginPanelModel = GitLabTokenLoginPanelModel(
    requiredUsername = null,
    uniqueAccountPredicate = accountManager::isAccountUnique
  )

  init {
    cs.launch {
      with(tokenLoginModel) {
        loginState.collectLatest { loginState ->
          if (loginState is LoginModel.LoginState.Connected) {
            val storedAccount = selectedAccount ?: GitLabAccount(name = loginState.username, server = getServerPath())
            updateAccount(storedAccount, token)
          }
        }
      }
    }
  }

  fun setSelectedAccount(account: GitLabAccount?) {
    selectedAccount = account
    with(tokenLoginModel) {
      requiredUsername = account?.name
      uniqueAccountPredicate = if (account == null) accountManager::isAccountUnique else { _, _ -> true }
      serverUri = account?.server?.uri ?: GitLabServerPath.DEFAULT_SERVER.uri
    }
  }

  private suspend fun updateAccount(account: GitLabAccount, credentials: String) {
    accountManager.updateAccount(account, credentials)
  }
}