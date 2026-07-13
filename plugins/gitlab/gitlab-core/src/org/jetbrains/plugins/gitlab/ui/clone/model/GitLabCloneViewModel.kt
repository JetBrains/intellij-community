// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager

internal interface GitLabCloneViewModel {
  val panelVm: SharedFlow<GitLabClonePanelViewModel>

  fun switchToEntryLoginPanel(account: GitLabAccount?)
  fun switchToTokenLoginPanel(account: GitLabAccount?)
  fun switchToRepositoryList()
  fun switchBackFromTokenLogin()

  fun requestOAuthLogin()

  fun doClone(checkoutListener: CheckoutProvider.Listener)
}

internal class GitLabCloneViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  accountManager: GitLabAccountManager
) : GitLabCloneViewModel {
  private val cs: CoroutineScope = parentCs.childScope(javaClass.name)

  private val entryLoginVm = GitLabCloneLoginEntryViewModel(cs, project, accountManager)
  private val tokenLoginVm = GitLabCloneTokenLoginViewModel(cs, accountManager)
  private val repositoriesVm = GitLabCloneRepositoriesViewModelImpl(project, cs, accountManager)

  private val accounts: StateFlow<Set<GitLabAccount>> = accountManager.accountsState

  private val _panelVm: MutableStateFlow<GitLabClonePanelViewModel> = MutableStateFlow(repositoriesVm)
  override val panelVm: SharedFlow<GitLabClonePanelViewModel> = _panelVm.asSharedFlow()

  init {
    cs.launch {
      accounts.collectLatest { accounts ->
        if (accounts.isNotEmpty()) switchToRepositoryList() else switchToEntryLoginPanel(null)
      }
    }

    cs.launch {
      merge(tokenLoginVm.loginSucceeded, entryLoginVm.loginSucceeded).collect {
        switchToRepositoryList()
      }
    }
  }

  override fun switchToEntryLoginPanel(account: GitLabAccount?) {
    entryLoginVm.setSelectedAccount(account)
    _panelVm.value = entryLoginVm
  }

  override fun switchToTokenLoginPanel(account: GitLabAccount?) {
    tokenLoginVm.setSelectedAccount(account)
    _panelVm.value = tokenLoginVm
  }

  override fun switchToRepositoryList() {
    _panelVm.value = repositoriesVm
  }

  override fun switchBackFromTokenLogin() {
    if (accounts.value.isNotEmpty()) {
      switchToRepositoryList()
    }
    else {
      switchToEntryLoginPanel(null)
    }
  }

  override fun requestOAuthLogin() = entryLoginVm.loginWithOAuth()

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    repositoriesVm.doClone(checkoutListener)
  }
}