// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.GitHttpAuthDataProvider
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider

private val LOG = logger<GithubHttpAuthDataProvider>()

class GHAccountAuthData(val account: GithubAccount, login: String, token: String) : AuthData(login, token)

class GithubHttpAuthDataProvider : GitHttpAuthDataProvider {
  override fun isSilent(): Boolean = true

  override fun getAuthData(project: Project, url: String): GHAccountAuthData? {
    val account = getGitAuthenticationAccounts(project, url, null).singleOrNull() ?: return null
    val token = GithubAuthenticationManager.getInstance().getTokenForAccount(account) ?: return null
    val accountDetails = getAccountDetails(account, token) ?: return null

    return GHAccountAuthData(account, accountDetails.login, token)
  }

  override fun getAuthData(project: Project, url: String, login: String): GHAccountAuthData? {
    val account = getGitAuthenticationAccounts(project, url, login).singleOrNull() ?: return null
    val token = GithubAuthenticationManager.getInstance().getTokenForAccount(account) ?: return null

    return GHAccountAuthData(account, login, token)
  }

  override fun forgetPassword(project: Project, url: String, authData: AuthData) {
    if (authData !is GHAccountAuthData) return

    project.service<GithubAccountGitAuthenticationFailureManager>().ignoreAccount(url, authData.account)
  }

  companion object {
    fun getGitAuthenticationAccounts(project: Project, url: String, login: String?): Set<GithubAccount> {
      val authenticationFailureManager = project.service<GithubAccountGitAuthenticationFailureManager>()
      val authenticationManager = GithubAuthenticationManager.getInstance()
      val potentialAccounts = authenticationManager.getAccounts()
        .filter { it.server.matches(url) }
        .filterNot { authenticationFailureManager.isAccountIgnored(url, it) }
        .filter { login == null || login == getAccountDetails(it)?.login }

      val defaultAccount = authenticationManager.getDefaultAccount(project)
      if (defaultAccount != null && defaultAccount in potentialAccounts) return setOf(defaultAccount)
      return potentialAccounts.toSet()
    }
  }
}

private fun getAccountDetails(account: GithubAccount, token: String? = null): GithubAuthenticatedUser? =
  try {
    service<GithubAccountInformationProvider>().getInformation(getRequestExecutor(account, token), DumbProgressIndicator(), account)
  }
  catch (e: Exception) {
    if (e !is ProcessCanceledException) LOG.info("Cannot load details for $account", e)
    null
  }

private fun getRequestExecutor(account: GithubAccount, token: String?): GithubApiRequestExecutor =
  if (token != null) GithubApiRequestExecutor.Factory.getInstance().create(token)
  else GithubApiRequestExecutorManager.getInstance().getExecutor(account)