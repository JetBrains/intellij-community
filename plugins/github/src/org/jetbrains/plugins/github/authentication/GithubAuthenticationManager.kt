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
  fun hasAccounts() = accountManager.accountsState.value.isNotEmpty()

  @CalledInAny
  fun getAccounts(): Set<GithubAccount> = accountManager.accountsState.value

  @CalledInAny
  internal fun getTokenForAccount(account: GithubAccount): String? = accountManager.findCredentials(account)

  @RequiresEdt
  @JvmOverloads
  internal fun requestNewToken(account: GithubAccount, project: Project?, parentComponent: Component? = null): String? =
    login(
      project, parentComponent,
      GHLoginRequest(
        text = GithubBundle.message("account.token.missing.for", account),
        server = account.server, login = account.name
      )
    )?.updateAccount(account)

  @RequiresEdt
  @JvmOverloads
  fun requestNewAccount(project: Project?, parentComponent: Component? = null): GithubAccount? =
    login(
      project, parentComponent,
      GHLoginRequest(isCheckLoginUnique = true)
    )?.registerAccount()

  @RequiresEdt
  @JvmOverloads
  fun requestNewAccountForServer(server: GithubServerPath, project: Project?, parentComponent: Component? = null): GithubAccount? =
    login(
      project, parentComponent,
      GHLoginRequest(server = server, isCheckLoginUnique = true)
    )?.registerAccount()

  @RequiresEdt
  @JvmOverloads
  fun requestNewAccountForServer(
    server: GithubServerPath,
    login: String,
    project: Project?,
    parentComponent: Component? = null
  ): GithubAccount? =
    login(
      project, parentComponent,
      GHLoginRequest(server = server, login = login, isLoginEditable = false, isCheckLoginUnique = true)
    )?.registerAccount()

  @RequiresEdt
  fun requestNewAccountForDefaultServer(project: Project?, authType: AuthorizationType): GithubAccount? {
    val loginRequest = GHLoginRequest(server = GithubServerPath.DEFAULT_SERVER, isCheckLoginUnique = true, authType = authType)
    return login(project, null, loginRequest)?.registerAccount()
  }

  internal fun isAccountUnique(name: String, server: GithubServerPath) =
    accountManager.accountsState.value.none { it.name == name && it.server.equals(server, true) }

  @RequiresEdt
  @JvmOverloads
  fun requestReLogin(project: Project?, account: GithubAccount, authType: AuthorizationType, parentComponent: Component? = null): Boolean =
    login(
      project, parentComponent,
      GHLoginRequest(server = account.server, login = account.name, authType = authType),
    )?.updateAccount(account) != null

  @RequiresEdt
  internal fun login(project: Project?, parentComponent: Component?, request: GHLoginRequest): GHAccountAuthData? {
    return when (request.authType) {
      AuthorizationType.OAUTH -> request.loginWithOAuth(project, parentComponent)
      AuthorizationType.TOKEN -> request.loginWithToken(project, parentComponent)
      AuthorizationType.UNDEFINED -> request.loginWithOAuthOrToken(project, parentComponent)
    }
  }

  @RequiresEdt
  internal fun removeAccount(account: GithubAccount) {
    accountManager.removeAccount(account)
  }

  @RequiresEdt
  internal fun updateAccountToken(account: GithubAccount, newToken: String) =
    accountManager.updateAccount(account, newToken)

  @RequiresEdt
  internal fun registerAccount(name: String, server: GithubServerPath, token: String): GithubAccount =
    registerAccount(GHAccountManager.createAccount(name, server), token)

  @RequiresEdt
  internal fun registerAccount(account: GithubAccount, token: String): GithubAccount {
    accountManager.updateAccount(account, token)
    return account
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

  @RequiresEdt
  @JvmOverloads
  fun ensureHasAccounts(project: Project?, parentComponent: Component? = null): Boolean =
    hasAccounts() || requestNewAccount(project, parentComponent) != null

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

  companion object {
    @JvmStatic
    fun getInstance(): GithubAuthenticationManager = service()
  }
}

private fun GHAccountAuthData.registerAccount(): GithubAccount =
  GithubAuthenticationManager.getInstance().registerAccount(login, server, token)

private fun GHAccountAuthData.updateAccount(account: GithubAccount): String {
  account.name = login
  GithubAuthenticationManager.getInstance().updateAccountToken(account, token)
  return token
}