// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.DialogManager
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GithubLoginDialog
import javax.swing.JComponent

/**
 * Entry point for interactions with Github authentication subsystem
 */
class GithubAuthenticationManager internal constructor(private val accountManager: GithubAccountManager) {
  @CalledInAny
  fun hasAccounts(): Boolean = accountManager.accounts.isNotEmpty()

  @CalledInAny
  fun getAccounts(): Set<GithubAccount> = accountManager.accounts

  @CalledInAny
  internal fun getTokenForAccount(account: GithubAccount): String? = accountManager.getTokenForAccount(account)

  @JvmOverloads
  @CalledInAny
  internal fun getOrRequestTokenForAccount(account: GithubAccount,
                                           project: Project? = null,
                                           parentComponent: JComponent? = null,
                                           modalityStateSupplier: () -> ModalityState = { ModalityState.any() }): String? {
    return getTokenForAccount(account) ?: invokeAndWaitIfNeed(modalityStateSupplier()) { requestNewToken(account, project, parentComponent) }
  }

  @CalledInAwt
  private fun requestNewToken(account: GithubAccount, project: Project?, parentComponent: JComponent?): String? {
    val dialog = GithubLoginDialog(project, parentComponent, message = "Missing access token for $account")
      .withServer(account.server.toString(), false)
      .withCredentials(account.name)
      .withToken()

    DialogManager.show(dialog)
    if (!dialog.isOK) return null

    val token = dialog.getToken()
    account.name = dialog.getLogin()
    accountManager.updateAccountToken(account, token)
    return token
  }

  @CalledInAwt
  fun requestNewAccount(project: Project): GithubAccount? {
    fun isAccountUnique(name: String, server: GithubServerPath) =
      accountManager.accounts.none { it.name == name && it.server == server }

    val dialog = GithubLoginDialog(project, null, ::isAccountUnique)
    DialogManager.show(dialog)
    if (!dialog.isOK) return null

    val account = GithubAccountManager.createAccount(dialog.getLogin(), dialog.getServer())
    accountManager.accounts += account
    accountManager.updateAccountToken(account, dialog.getToken())
    return account
  }

  @TestOnly
  fun registerAccount(name: String, host: String, token: String): GithubAccount {
    val account = GithubAccountManager.createAccount(name, GithubServerPath.from(host))
    accountManager.accounts += account
    accountManager.updateAccountToken(account, token)
    return account
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

  fun ensureHasAccounts(project: Project): Boolean {
    if (!hasAccounts()) {
      if (requestNewAccount(project) == null) {
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
