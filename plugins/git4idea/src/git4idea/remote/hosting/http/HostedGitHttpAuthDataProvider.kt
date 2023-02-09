// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.http

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.remote.GitHttpAuthDataProvider
import git4idea.remote.hosting.GitHostingUrlUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class HostedGitHttpAuthDataProvider<A : ServerAccount> : GitHttpAuthDataProvider {
  abstract val providerId: String

  abstract val accountManager: AccountManager<A, String>

  abstract fun getDefaultAccountHolder(project: Project): DefaultAccountHolder<A>

  abstract fun getAuthFailureManager(project: Project): HostedGitAuthenticationFailureManager<A>

  abstract suspend fun getAccountLogin(account: A, token: String): String?

  override fun isSilent(): Boolean = true

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String): AuthData? = runBlockingMaybeCancellable {
    doGetAuthData(project, url)
  }

  private suspend fun doGetAuthData(project: Project, url: String): AuthData? {
    val defaultAuthData = getDefaultAccountData(project, url)
    if (defaultAuthData != null) {
      return defaultAuthData
    }

    val accountsWithTokens = getAccountsWithTokens(project, url)
    val (acc, token) = accountsWithTokens.entries.singleOrNull { it.value != null } ?: return null
    val login = getAccountLogin(acc, token!!)
    if (login == null) {
      return null
    }
    return AccountAuthData(acc, login, token, authDataProviderId = providerId)
  }

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String, login: String): AuthData? = runBlockingMaybeCancellable {
    doGetAuthData(project, url, login)
  }

  private suspend fun doGetAuthData(project: Project, url: String, login: String): AccountAuthData<A>? {
    val defaultAuthData = getDefaultAccountData(project, url)
    if (defaultAuthData != null && defaultAuthData.login == login) {
      return defaultAuthData
    }

    return getAccountsWithTokens(project, url).mapNotNull { (acc, token) ->
      if (token == null) return@mapNotNull null
      val accountLogin = getAccountLogin(acc, token) ?: return@mapNotNull null
      if (accountLogin != login) return@mapNotNull null
      AccountAuthData(acc, login, token, authDataProviderId = providerId)
    }.singleOrNull()
  }

  override fun forgetPassword(project: Project, url: String, authData: AuthData) {
    if (authData !is AccountAuthData<*> || authData.authDataProviderId != providerId) {
      return
    }

    @Suppress("UNCHECKED_CAST") // suppress since providerId check guaranties A generic type here
    getAuthFailureManager(project).ignoreAccount(url, authData.account as A)
  }

  private suspend fun getDefaultAccountData(project: Project, url: String): AccountAuthData<A>? {
    val defaultAccount = getDefaultAccountHolder(project).account ?: return null
    val authFailureManager = getAuthFailureManager(project)

    if (GitHostingUrlUtil.match(defaultAccount.server.toURI(), url) && !authFailureManager.isAccountIgnored(url, defaultAccount)) {
      val token = accountManager.findCredentials(defaultAccount) ?: return null
      val login = getAccountLogin(defaultAccount, token) ?: return null
      return AccountAuthData(defaultAccount, login, token, authDataProviderId = providerId)
    }
    return null
  }

  private suspend fun getAccountsWithTokens(project: Project, url: String): Map<A, String?> {
    return getAccountsWithTokens(accountManager, getAuthFailureManager(project), url)
  }

  private class AccountAuthData<A : ServerAccount>(
    val account: A, login: String, password: String,
    val authDataProviderId: String
  ) : AuthData(login, password)

  companion object {
    suspend fun <A : ServerAccount> getAccountsWithTokens(
      accountManager: AccountManager<A, String>,
      authFailureManager: HostedGitAuthenticationFailureManager<A>,
      url: String
    ): Map<A, String?> {
      return accountManager.accountsState.value
        .filter { GitHostingUrlUtil.match(it.server.toURI(), url) }
        .filterNot { authFailureManager.isAccountIgnored(url, it) }
        .associateWith { accountManager.findCredentials(it) }
    }
  }
}