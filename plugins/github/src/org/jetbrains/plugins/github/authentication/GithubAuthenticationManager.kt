// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.collaboration.async.collectWithPrevious
import com.intellij.collaboration.async.disposingMainScope
import com.intellij.collaboration.auth.AccountsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component

internal class GHAccountAuthData(val account: GithubAccount, login: String, token: String) : AuthData(login, token) {
  val server: GithubServerPath get() = account.server
  val token: String get() = password!!
}

/**
 * Entry point for interactions with Github authentication subsystem
 */
class GithubAuthenticationManager internal constructor() {
  internal val accountManager: GHAccountManager get() = service()

  @CalledInAny
  fun getAccounts(): Set<GithubAccount> = accountManager.accountsState.value

  internal suspend fun getTokenForAccount(account: GithubAccount): String? = accountManager.findCredentials(account)

  @RequiresEdt
  @JvmOverloads
  internal fun requestNewToken(
    account: GithubAccount,
    project: Project?,
    parentComponent: Component? = null
  ): String? =
    login(
      project, parentComponent,
      GHLoginRequest(
        text = GithubBundle.message("account.token.missing.for", account),
        server = account.server, login = account.name
      )
    )?.updateAccount(account)

  @RequiresEdt
  @JvmOverloads
  internal fun requestReLogin(
    account: GithubAccount,
    authType: AuthorizationType = AuthorizationType.UNDEFINED,
    project: Project?,
    parentComponent: Component? = null
  ): GHAccountAuthData? =
    login(
      project, parentComponent,
      GHLoginRequest(
        server = account.server, login = account.name, authType = authType
      )
    )?.apply {
      updateAccount(account)
    }

  @RequiresEdt
  @JvmOverloads
  internal fun requestNewAccountForServer(
    server: GithubServerPath,
    login: String? = null,
    project: Project?,
    parentComponent: Component? = null,
    authType: AuthorizationType = AuthorizationType.UNDEFINED
  ): GHAccountAuthData? =
    login(
      project, parentComponent,
      GHLoginRequest(server = server, login = login, isLoginEditable = login != null, isCheckLoginUnique = true, authType = authType)
    )?.apply {
      registerAccount()
    }

  @RequiresEdt
  internal fun login(project: Project?, parentComponent: Component?, request: GHLoginRequest): GHAccountAuthData? {
    return when (request.authType) {
      AuthorizationType.OAUTH -> request.loginWithOAuth(project, parentComponent)
      AuthorizationType.TOKEN -> request.loginWithToken(project, parentComponent)
      AuthorizationType.UNDEFINED -> request.loginWithOAuthOrToken(project, parentComponent)
    }
  }

  @TestOnly
  fun clearAccounts() {
    accountManager.updateAccounts(emptyMap())
  }

  fun getDefaultAccount(project: Project): GithubAccount? =
    project.service<GithubProjectDefaultAccountHolder>().account

  fun setDefaultAccount(project: Project, account: GithubAccount?) {
    project.service<GithubProjectDefaultAccountHolder>().account = account
  }

  @Suppress("unused") // used externally
  @CalledInAny
  fun hasAccounts() = accountManager.accountsState.value.isNotEmpty()

  @Suppress("unused") // used externally
  @RequiresEdt
  @JvmOverloads
  fun ensureHasAccounts(project: Project?, parentComponent: Component? = null): Boolean {
    if (accountManager.accountsState.value.isNotEmpty()) return true
    return login(
      project, parentComponent,
      GHLoginRequest(isCheckLoginUnique = true)
    )?.registerAccount() != null
  }

  fun getSingleOrDefaultAccount(project: Project): GithubAccount? =
    project.service<GithubProjectDefaultAccountHolder>().account
    ?: accountManager.accountsState.value.singleOrNull()

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

  private fun GHAccountAuthData.registerAccount(): GithubAccount {
    val account = GHAccountManager.createAccount(login, server)
    accountManager.updateAccount(account, token)
    return account
  }

  private fun GHAccountAuthData.updateAccount(account: GithubAccount): String {
    account.name = login
    accountManager.updateAccount(account, token)
    return token
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubAuthenticationManager = service()
  }
}