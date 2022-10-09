// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.async.collectWithPrevious
import com.intellij.collaboration.async.disposingMainScope
import com.intellij.collaboration.auth.AccountsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import java.awt.Component


/**
 * Entry point for interactions with Github authentication subsystem
 */
@Deprecated("deprecated in favor of GHAccountsUtil")
class GithubAuthenticationManager internal constructor() {
  private val accountManager: GHAccountManager get() = service()

  @CalledInAny
  fun getAccounts(): Set<GithubAccount> = accountManager.accountsState.value

  @CalledInAny
  fun hasAccounts() = accountManager.accountsState.value.isNotEmpty()

  @RequiresEdt
  @JvmOverloads
  fun ensureHasAccounts(project: Project?, parentComponent: Component? = null): Boolean {
    if (accountManager.accountsState.value.isNotEmpty()) return true
    return GHAccountsUtil.requestNewAccount(project = project, parentComponent = parentComponent) != null
  }

  fun getSingleOrDefaultAccount(project: Project): GithubAccount? = GHAccountsUtil.getSingleOrDefaultAccount(project)

  @Deprecated("replaced with stateFlow", ReplaceWith("accountManager.accountsState"))
  @RequiresEdt
  fun addListener(disposable: Disposable, listener: AccountsListener<GithubAccount>) {
    disposable.disposingMainScope().launch {
      accountManager.accountsState.collectWithPrevious(setOf()) { prev, current ->
        listener.onAccountListChanged(prev, current)
        current.forEach { acc ->
          async {
            accountManager.getCredentialsFlow(acc, false).collectLatest {
              listener.onAccountCredentialsChanged(acc)
            }
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubAuthenticationManager = service()
  }
}