// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
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
  private val accountManager: GithubAccountManager get() = service()

  @CalledInAny
  fun hasAccounts() = accountManager.accounts.isNotEmpty()

  @CalledInAny
  fun getAccounts(): Set<GithubAccount> = accountManager.accounts

  @CalledInAny
  internal fun getTokenForAccount(account: GithubAccount): String? = accountManager.getTokenForAccount(account)

  @CalledInAwt
  @JvmOverloads
  internal fun requestNewToken(account: GithubAccount, project: Project?, parentComponent: Component? = null): String? =
    login(
      project, parentComponent,
      GHLoginRequest(
        text = GithubBundle.message("account.token.missing.for", account),
        server = account.server, login = account.name
      )
    )?.updateAccount(account)

  @CalledInAwt
  @JvmOverloads
  fun requestNewAccount(project: Project?, parentComponent: Component? = null): GithubAccount? =
    login(
      project, parentComponent,
      GHLoginRequest(isCheckLoginUnique = true)
    )?.registerAccount()

  @CalledInAwt
  @JvmOverloads
  fun requestNewAccountForServer(server: GithubServerPath, project: Project?, parentComponent: Component? = null): GithubAccount? =
    login(
      project, parentComponent,
      GHLoginRequest(server = server, isCheckLoginUnique = true)
    )?.registerAccount()

  @CalledInAwt
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

  internal fun isAccountUnique(name: String, server: GithubServerPath) =
    accountManager.accounts.none { it.name == name && it.server == server }

  @CalledInAwt
  @JvmOverloads
  fun requestReLogin(account: GithubAccount, project: Project?, parentComponent: Component? = null): Boolean =
    login(
      project, parentComponent,
      GHLoginRequest(server = account.server, login = account.name)
    )?.updateAccount(account) != null

  @CalledInAwt
  internal fun login(project: Project?, parentComponent: Component?, request: GHLoginRequest): GHAccountAuthData? =
    request.loginWithPasswordOrToken(project, parentComponent)

  @CalledInAwt
  internal fun removeAccount(account: GithubAccount) {
    accountManager.accounts -= account
  }

  @CalledInAwt
  internal fun updateAccountToken(account: GithubAccount, newToken: String) =
    accountManager.updateAccountToken(account, newToken)

  @CalledInAwt
  internal fun registerAccount(name: String, server: GithubServerPath, token: String): GithubAccount =
    registerAccount(GithubAccountManager.createAccount(name, server), token)

  @CalledInAwt
  internal fun registerAccount(account: GithubAccount, token: String): GithubAccount {
    accountManager.accounts += account
    accountManager.updateAccountToken(account, token)
    return account
  }

  @TestOnly
  fun clearAccounts() {
    accountManager.accounts = emptySet()
  }

  fun getDefaultAccount(project: Project): GithubAccount? =
    project.service<GithubProjectDefaultAccountHolder>().account

  @TestOnly
  fun setDefaultAccount(project: Project, account: GithubAccount?) {
    project.service<GithubProjectDefaultAccountHolder>().account = account
  }

  @CalledInAwt
  @JvmOverloads
  fun ensureHasAccounts(project: Project?, parentComponent: Component? = null): Boolean =
    hasAccounts() || requestNewAccount(project, parentComponent) != null

  fun getSingleOrDefaultAccount(project: Project): GithubAccount? =
    project.service<GithubProjectDefaultAccountHolder>().account
    ?: accountManager.accounts.singleOrNull()

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