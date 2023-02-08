// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.git.http

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.remote.GitHttpAuthDataProvider
import git4idea.remote.hosting.GitHostingUrlUtil.match
import kotlinx.coroutines.coroutineScope
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder

private val LOG = logger<GitLabGitHttpAuthDataProvider>()

internal class GitLabAccountAuthData(val account: GitLabAccount, login: String, password: String) : AuthData(login, password)

// TODO: extract common code with GitHub
internal class GitLabGitHttpAuthDataProvider : GitHttpAuthDataProvider {
  override fun isSilent(): Boolean = true

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String): GitLabAccountAuthData? = runBlockingMaybeCancellable {
    doGetAuthData(project, url)
  }

  private suspend fun doGetAuthData(project: Project, url: String): GitLabAccountAuthData? {
    val defaultAuthData = getDefaultAccountData(project, url)
    if (defaultAuthData != null) {
      return defaultAuthData
    }

    val accountsWithTokens = getAccountsWithTokens(project, url)
    val (acc, token) = accountsWithTokens.entries.singleOrNull { it.value != null } ?: return null
    val login = getAccountLogin(acc)
    if (login == null) {
      return null
    }
    return GitLabAccountAuthData(acc, login, token!!)
  }

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String, login: String): GitLabAccountAuthData? = runBlockingMaybeCancellable {
    doGetAuthData(project, url, login)
  }

  private suspend fun doGetAuthData(project: Project, url: String, login: String): GitLabAccountAuthData? {
    val defaultAuthData = getDefaultAccountData(project, url)
    if (defaultAuthData != null && defaultAuthData.login == login) {
      return defaultAuthData
    }

    return getAccountsWithTokens(project, url).mapNotNull { (acc, token) ->
      if (token == null) return@mapNotNull null
      val accountLogin = getAccountLogin(acc) ?: return@mapNotNull null
      if (accountLogin != login) return@mapNotNull null
      GitLabAccountAuthData(acc, login, token)
    }.singleOrNull()
  }

  override fun forgetPassword(project: Project, url: String, authData: AuthData) {
    if (authData !is GitLabAccountAuthData) return

    project.service<GitLabGitAuthenticationFailureManager>().ignoreAccount(url, authData.account)
  }

  companion object {
    private suspend fun getDefaultAccountData(project: Project, url: String): GitLabAccountAuthData? {
      val defaultAccount = project.service<GitLabProjectDefaultAccountHolder>().account ?: return null
      val authFailureManager = project.service<GitLabGitAuthenticationFailureManager>()

      if (match(defaultAccount.server.toURI(), url) && !authFailureManager.isAccountIgnored(url, defaultAccount)) {
        val token = service<GitLabAccountManager>().findCredentials(defaultAccount) ?: return null
        val login = getAccountLogin(defaultAccount) ?: return null
        return GitLabAccountAuthData(defaultAccount, login, token)
      }
      return null
    }

    suspend fun getAccountsWithTokens(project: Project, url: String): Map<GitLabAccount, String?> {
      val accountManager = service<GitLabAccountManager>()
      val authFailureManager = project.service<GitLabGitAuthenticationFailureManager>()

      return accountManager.accountsState.value
        .filter { match(it.server.toURI(), url) }
        .filterNot { authFailureManager.isAccountIgnored(url, it) }
        .associateWith { accountManager.findCredentials(it) }
    }

    suspend fun getAccountLogin(account: GitLabAccount): String? = coroutineScope {
      try {
        val apiClient = service<GitLabAccountManager>().findCredentials(account)?.let(service<GitLabApiManager>()::getClient)
                        ?: return@coroutineScope null
        return@coroutineScope apiClient.getCurrentUser(account.server)?.username
      }
      catch (e: ProcessCanceledException) {
        return@coroutineScope null
      }
      catch (e: Exception) {
        LOG.info("Cannot load details for $account", e)
        return@coroutineScope null
      }
    }
  }
}
