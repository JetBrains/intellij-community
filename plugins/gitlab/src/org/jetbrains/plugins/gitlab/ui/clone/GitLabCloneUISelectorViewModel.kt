// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.ui.clone.GitLabCloneUISelectorViewModel.UIState

internal interface GitLabCloneUISelectorViewModel {
  val uiState: SharedFlow<UIState>

  fun switchToLoginPanel(account: GitLabAccount?)

  fun switchToRepositoryList()

  sealed interface UIState {
    class Login(val account: GitLabAccount?) : UIState
    object Repositories : UIState
  }
}

internal class GitLabCloneUISelectorViewModelImpl(
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager
) : GitLabCloneUISelectorViewModel {
  private val cs: CoroutineScope = parentCs.childScope()

  private val accounts: SharedFlow<Set<GitLabAccount>> = accountManager.accountsState

  private val _uiState: MutableStateFlow<UIState> = MutableStateFlow(UIState.Login(null))
  override val uiState: SharedFlow<UIState> = _uiState.asSharedFlow()

  init {
    cs.launch {
      accounts.collectLatest { accounts ->
        if (accounts.isNotEmpty()) {
          switchToRepositoryList()
        }

        accounts.forEach { account ->
          launch {
            accountManager.getCredentialsFlow(account).collectLatest {
              switchToRepositoryList()
            }
          }
        }
      }
    }
  }

  override fun switchToLoginPanel(account: GitLabAccount?) {
    _uiState.value = UIState.Login(account)
  }

  override fun switchToRepositoryList() {
    _uiState.value = UIState.Repositories
  }
}