// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager

internal interface GitLabCloneLoginViewModel {
  val accounts: SharedFlow<Set<GitLabAccount>>

  fun updateAccount(account: GitLabAccount, credentials: String)

  fun isAccountUnique(serverPath: GitLabServerPath, accountName: String): Boolean
}

internal class GitLabCloneLoginViewModelImpl(
  parentCs: CoroutineScope,
  private val accountManager: GitLabAccountManager
) : GitLabCloneLoginViewModel {
  private val cs: CoroutineScope = parentCs.childScope()

  override val accounts: SharedFlow<Set<GitLabAccount>> = accountManager.accountsState

  override fun updateAccount(account: GitLabAccount, credentials: String) {
    cs.launch {
      accountManager.updateAccount(account, credentials)
    }
  }

  override fun isAccountUnique(serverPath: GitLabServerPath, accountName: String): Boolean {
    return accountManager.isAccountUnique(serverPath, accountName)
  }
}