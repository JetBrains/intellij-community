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

/**
 * Base class that provides a business logic part of [GitHttpAuthDataProvider] interface,
 * that can be extended by git hosting providers such as Space, GitHub, GitLab, BitBucket, etc.
 *
 * Will do no user interaction
 *
 * This logic can be described as: if user is logged in plugin (so their account is stored in [accountManager])
 * then their token will be passed as password for http remote operations.
 *
 * If user is logged in more than one account and user has chosen the default account (should be stored in [getDefaultAccountHolder])
 * then this default account's details will be used.
 *
 * If authorization with given credentials failed, account will be stored to [HostedGitAuthenticationFailureManager]
 * and not used in the future attempts (until account token change or project reopen)
 *
 * Clients' auth data providers should be registered in plugin.xml with "Git4Idea.GitHttpAuthDataProvider" extension.
 */
@ApiStatus.Experimental
abstract class SilentHostedGitHttpAuthDataProvider<A : ServerAccount> : GitHttpAuthDataProvider {
  abstract val providerId: String

  /**
   * Account manager that holds accounts and their credentials.
   *
   * In common, it is an application service.
   */
  abstract val accountManager: AccountManager<A, String>

  /**
   * Provider of the default account selected by user.
   * So that if there are more than one account in [accountManager],
   * then [DefaultAccountHolder.account] will be used as login and credentials provider
   *
   * In common, it is a project service.
   */
  abstract fun getDefaultAccountHolder(project: Project): DefaultAccountHolder<A>

  /**
   * Holder for accounts and their creds that failed to access git remotes operations.
   * Such accounts won't be provided to git http remote operations until creds changed or IDE restarted.
   *
   * In common, clients should implement their own project service to keep such accounts.
   */
  abstract fun getAuthFailureManager(project: Project): HostedGitAuthenticationFailureManager<A>

  /**
   * Provides login that will be passed to git remote http operation.
   * Or [null] if login cannot be acquired, in this case nothing will be passed to git http remote operations and
   * user will be asked to input username and password themselves.
   */
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