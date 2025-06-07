// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.extensions

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.http.HostedGitAuthenticationFailureManager
import git4idea.remote.hosting.http.SilentHostedGitHttpAuthDataProvider
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GHCachingAccountInformationProvider
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder

private val LOG = logger<GHSilentHttpAuthDataProvider>()

internal class GHSilentHttpAuthDataProvider : SilentHostedGitHttpAuthDataProvider<GithubAccount>() {
  override val providerId: String = "GitHub Plugin"

  override val accountManager: AccountManager<GithubAccount, String>
    get() = service<GHAccountManager>()

  override fun getDefaultAccountHolder(project: Project): DefaultAccountHolder<GithubAccount> {
    return project.service<GithubProjectDefaultAccountHolder>()
  }

  override fun getAuthFailureManager(project: Project): HostedGitAuthenticationFailureManager<GithubAccount> {
    return project.service<GHGitAuthenticationFailureManager>()
  }

  override suspend fun getAccountLogin(account: GithubAccount, token: String): String? {
    return getAccountDetails(account, token)?.login
  }

  companion object {
    suspend fun getAccountsWithTokens(project: Project, url: String): Map<GithubAccount, String?> {
      val accountManager = service<GHAccountManager>()
      val authFailureManager = project.service<GHGitAuthenticationFailureManager>()

      return getAccountsWithTokens(accountManager, authFailureManager, url)
    }

    suspend fun getAccountDetails(account: GithubAccount, token: String): GithubAuthenticatedUser? =
      try {
        val executor = GithubApiRequestExecutor.Factory.getInstance().create(account.server, token)
        service<GHCachingAccountInformationProvider>().loadInformation(executor, account)
      }
      catch (e: Exception) {
        if (e !is ProcessCanceledException) LOG.info("Cannot load details for $account", e)
        null
      }
  }
}
