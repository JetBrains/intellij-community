// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone.model

import com.intellij.collaboration.auth.ui.login.LoginModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager

internal interface GitLabCloneViewModel {
  val panelVm: SharedFlow<GitLabClonePanelViewModel>

  fun switchToLoginPanel(account: GitLabAccount?)

  fun switchToRepositoryList()

  fun doClone(checkoutListener: CheckoutProvider.Listener)
}

internal class GitLabCloneViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  accountManager: GitLabAccountManager
) : GitLabCloneViewModel {
  private val cs: CoroutineScope = parentCs.childScope(javaClass.name)

  private val loginVm = GitLabCloneLoginViewModelImpl(cs, accountManager)
  private val repositoriesVm = GitLabCloneRepositoriesViewModelImpl(project, cs, accountManager)

  private val accounts: SharedFlow<Set<GitLabAccount>> = accountManager.accountsState

  private val _panelVm: MutableStateFlow<GitLabClonePanelViewModel> = MutableStateFlow(repositoriesVm)
  override val panelVm: SharedFlow<GitLabClonePanelViewModel> = _panelVm.asSharedFlow()

  init {
    cs.launch {
      accounts.collectLatest { accounts ->
        if (accounts.isNotEmpty()) switchToRepositoryList() else switchToLoginPanel(null)
      }
    }

    cs.launch {
      loginVm.tokenLoginModel.loginState.collectLatest { loginState ->
        if (loginState is LoginModel.LoginState.Connected) {
          switchToRepositoryList()
        }
      }
    }
  }

  override fun switchToLoginPanel(account: GitLabAccount?) {
    loginVm.setSelectedAccount(account)
    _panelVm.value = loginVm
  }

  override fun switchToRepositoryList() {
    _panelVm.value = repositoriesVm
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    repositoriesVm.doClone(checkoutListener)
  }
}