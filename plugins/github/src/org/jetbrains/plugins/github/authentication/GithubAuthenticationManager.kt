// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.DialogManager
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GithubLoginDialog
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component

/**
 * Entry point for interactions with Github authentication subsystem
 */
class GithubAuthenticationManager internal constructor() {
  private val accountManager: GithubAccountManager
    get() = service()

  @CalledInAny
  fun hasAccounts() = accountManager.accounts.isNotEmpty()

  @CalledInAny
  fun getAccounts(): Set<GithubAccount> = accountManager.accounts

  @CalledInAny
  internal fun getTokenForAccount(account: GithubAccount): String? = accountManager.getTokenForAccount(account)

  @CalledInAwt
  @JvmOverloads
  internal fun requestNewToken(account: GithubAccount, project: Project?, parentComponent: Component? = null): String? {
    val dialog = GithubLoginDialog(GithubApiRequestExecutor.Factory.getInstance(), project, parentComponent,
                                   message = GithubBundle.message("account.token.missing.for", account))
      .withServer(account.server.toString(), false)
      .withCredentials(account.name)
      .withToken()

    DialogManager.show(dialog)
    if (!dialog.isOK) return null

    val token = dialog.token
    account.name = dialog.login
    accountManager.updateAccountToken(account, token)
    return token
  }

  @CalledInAwt
  @JvmOverloads
  fun requestNewAccount(project: Project?, parentComponent: Component? = null): GithubAccount? {
    val dialog = GithubLoginDialog(GithubApiRequestExecutor.Factory.getInstance(), project, parentComponent, ::isAccountUnique)
    DialogManager.show(dialog)
    if (!dialog.isOK) return null

    return registerAccount(dialog.login, dialog.server, dialog.token)
  }

  @CalledInAwt
  @JvmOverloads
  fun requestNewAccountForServer(server: GithubServerPath, project: Project?, parentComponent: Component? = null): GithubAccount? {
    val dialog = GithubLoginDialog(GithubApiRequestExecutor.Factory.getInstance(), project, parentComponent, ::isAccountUnique).withServer(server.toUrl(), false)
    DialogManager.show(dialog)
    if (!dialog.isOK) return null

    return registerAccount(dialog.login, dialog.server, dialog.token)
  }

  @CalledInAwt
  @JvmOverloads
  fun requestNewAccountForServer(server: GithubServerPath,
                                 login: String,
                                 project: Project?,
                                 parentComponent: Component? = null): GithubAccount? {
    val dialog = GithubLoginDialog(GithubApiRequestExecutor.Factory.getInstance(), project, parentComponent, ::isAccountUnique)
      .withServer(server.toUrl(), false)
      .withCredentials(login, editableLogin = false)
    DialogManager.show(dialog)
    if (!dialog.isOK) return null

    return registerAccount(dialog.login, dialog.server, dialog.token)
  }

  internal fun isAccountUnique(name: String,
                               server: GithubServerPath) = accountManager.accounts.none { it.name == name && it.server == server }

  @CalledInAwt
  @JvmOverloads
  fun requestReLogin(account: GithubAccount, project: Project?, parentComponent: Component? = null): Boolean {
    val dialog = GithubLoginDialog(GithubApiRequestExecutor.Factory.getInstance(), project, parentComponent)
      .withServer(account.server.toString(), false)
      .withCredentials(account.name)

    DialogManager.show(dialog)
    if (!dialog.isOK) return false

    val token = dialog.token
    account.name = dialog.login
    accountManager.updateAccountToken(account, token)
    return true
  }

  @CalledInAwt
  internal fun removeAccount(githubAccount: GithubAccount) {
    accountManager.accounts -= githubAccount
  }

  @CalledInAwt
  internal fun updateAccountToken(account: GithubAccount, newToken: String) {
    accountManager.updateAccountToken(account, newToken)
  }

  private fun registerAccount(name: String, server: GithubServerPath, token: String): GithubAccount {
    val account = GithubAccountManager.createAccount(name, server)
    accountManager.accounts += account
    accountManager.updateAccountToken(account, token)
    return account
  }

  @CalledInAwt
  internal fun registerAccount(name: String, host: String, token: String): GithubAccount {
    return registerAccount(name, GithubServerPath.from(host), token)
  }

  @TestOnly
  fun clearAccounts() {
    for (account in accountManager.accounts) accountManager.updateAccountToken(account, null)
    accountManager.accounts = emptySet()
  }

  fun getDefaultAccount(project: Project): GithubAccount? {
    return project.service<GithubProjectDefaultAccountHolder>().account
  }

  @TestOnly
  fun setDefaultAccount(project: Project, account: GithubAccount?) {
    project.service<GithubProjectDefaultAccountHolder>().account = account
  }

  @CalledInAwt
  @JvmOverloads
  fun ensureHasAccounts(project: Project?, parentComponent: Component? = null): Boolean {
    if (!hasAccounts()) {
      if (requestNewAccount(project, parentComponent) == null) {
        return false
      }
    }
    return true
  }

  fun getSingleOrDefaultAccount(project: Project): GithubAccount? {
    project.service<GithubProjectDefaultAccountHolder>().account?.let { return it }
    val accounts = accountManager.accounts
    if (accounts.size == 1) return accounts.first()
    return null
  }

  companion object {
    @JvmStatic
    fun getInstance(): GithubAuthenticationManager {
      return service()
    }
  }
}
