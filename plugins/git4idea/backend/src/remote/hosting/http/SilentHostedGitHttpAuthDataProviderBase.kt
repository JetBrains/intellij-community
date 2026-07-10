// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 *
 * NB: [getAuthData] must be overriden for this class to work!
 *
 * @param A account type
 * @param C stored credentials type
 */
@ApiStatus.Experimental
abstract class SilentHostedGitHttpAuthDataProviderBase<A : ServerAccount, C : Any> : GitHttpAuthDataProvider {
  protected abstract val providerId: String

  /**
   * Account manager that holds accounts and their credentials.
   *
   * In common, it is an application service.
   */
  protected abstract val accountManager: AccountManager<A, C>

  /**
   * Provider of the default account selected by user.
   * So that if there are more than one account in [accountManager],
   * then [DefaultAccountHolder.account] will be used as login and credentials provider
   *
   * In common, it is a project service.
   */
  protected abstract fun getDefaultAccountHolder(project: Project): DefaultAccountHolder<A>

  /**
   * Holder for accounts and their creds that failed to access git remotes operations.
   * Such accounts won't be provided to git http remote operations until creds changed or IDE restarted.
   *
   * In common, clients should implement their own project service to keep such accounts.
   */
  protected abstract fun getAuthFailureManager(project: Project): HostedGitAuthenticationFailureManager<A>

  /**
   * Provides the login and password that will be passed to git remote http operation.
   * Or `null` if login or password cannot be acquired, in this case nothing will be passed to git http remote operations and
   * user will be asked to input the data themselves.
   */
  // open and not abstract to preserve binary compatibility
  protected open suspend fun getAuthData(account: A): AuthData? = TODO("Must be implemented")

  final override fun isSilent(): Boolean = true

  @RequiresBackgroundThread
  final override fun getAuthData(project: Project, url: String): AuthData? = runBlockingMaybeCancellable {
    doGetAuthData(project, url)
  }

  private suspend fun doGetAuthData(project: Project, url: String): AuthData? {
    val defaultAuthData = getDefaultAccountData(project, url)
    if (defaultAuthData != null) {
      return defaultAuthData
    }

    val account = getAccounts(project, url).singleOrNull() ?: return null
    return getAuthData(account)?.let {
      AccountAuthData(account, it, authDataProviderId = providerId)
    }
  }

  @RequiresBackgroundThread
  final override fun getAuthData(project: Project, url: String, login: String): AuthData? = runBlockingMaybeCancellable {
    doGetAuthData(project, url, login)
  }

  private suspend fun doGetAuthData(project: Project, url: String, login: String): AccountAuthData<A>? {
    val defaultAuthData = getDefaultAccountData(project, url)
    if (defaultAuthData != null && defaultAuthData.login == login) {
      return defaultAuthData
    }

    return getAccounts(project, url).mapNotNull { acc ->
      val authData = getAuthData(acc) ?: return@mapNotNull null
      if (authData.login != login) return@mapNotNull null
      AccountAuthData(acc, authData, authDataProviderId = providerId)
    }.singleOrNull()
  }

  final override fun forgetPassword(project: Project, url: String, authData: AuthData) {
    if (authData !is AccountAuthData<*> || authData.authDataProviderId != providerId) {
      return
    }

    @Suppress("UNCHECKED_CAST") // suppress since providerId check guaranties A generic type here
    getAuthFailureManager(project).ignoreAccount(url, authData.account as A)
  }

  private suspend fun getDefaultAccountData(project: Project, gitHostUrl: String): AccountAuthData<A>? {
    val defaultAccount = getDefaultAccountHolder(project).account ?: return null
    val authFailureManager = getAuthFailureManager(project)

    if (GitHostingUrlUtil.matchHost(defaultAccount.server.toURI(), gitHostUrl)
        && !authFailureManager.isAccountIgnored(gitHostUrl, defaultAccount)) {
      val authData = getAuthData(defaultAccount) ?: return null
      return AccountAuthData(defaultAccount, authData, authDataProviderId = providerId)
    }
    return null
  }

  private fun getAccounts(project: Project, url: String): Collection<A> {
    val authFailureManager = getAuthFailureManager(project)
    return accountManager.accountsState.value
      .filter { GitHostingUrlUtil.matchHost(it.server.toURI(), url) }
      .filterNot { authFailureManager.isAccountIgnored(url, it) }
  }

  private class AccountAuthData<A : ServerAccount>(
    val account: A,
    authData: AuthData,
    val authDataProviderId: String,
  ) : AuthData(authData.login, authData.password)

  companion object {
    suspend fun <A : ServerAccount, C : Any> getAccountsWithTokens(
      accountManager: AccountManager<A, C>,
      authFailureManager: HostedGitAuthenticationFailureManager<A>,
      url: String,
    ): Map<A, C?> {
      return accountManager.accountsState.value
        .filter { GitHostingUrlUtil.matchHost(it.server.toURI(), url) }
        .filterNot { authFailureManager.isAccountIgnored(url, it) }
        .associateWith { accountManager.findCredentials(it) }
    }
  }
}