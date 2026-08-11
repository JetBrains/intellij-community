// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.git.http

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import git4idea.remote.hosting.http.HostedGitAuthenticationFailureManager
import git4idea.remote.hosting.http.SilentHostedGitHttpAuthDataProvider
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.GitLabCredentials
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder

private val LOG = logger<GitLabSilentHttpAuthDataProvider>()

internal class GitLabSilentHttpAuthDataProvider : SilentHostedGitHttpAuthDataProvider<GitLabAccount>() {
  override val providerId: String = "GitLab Plugin"

  override val accountManager: AccountManager<GitLabAccount, String>
    get() = StringCredentialsAdapter(service<GitLabAccountManager>())

  override fun getDefaultAccountHolder(project: Project): DefaultAccountHolder<GitLabAccount> {
    return project.service<GitLabProjectDefaultAccountHolder>()
  }

  override fun getAuthFailureManager(project: Project): HostedGitAuthenticationFailureManager<GitLabAccount> {
    return project.service<GitLabGitAuthenticationFailureManager>()
  }

  override suspend fun getAccountLogin(account: GitLabAccount, token: String): String? {
    return try {
      service<GitLabApiManager>().getClient(account.server, token).graphQL.getCurrentUser().username
    }
    catch (e: ProcessCanceledException) {
      null
    }
    catch (e: Exception) {
      LOG.info("Cannot load details for $account", e)
      null
    }
  }

  // Necessary to avoid making `SilentHostedGitHttpAuthDataProvider` using generic credentials due to external API usages
  private class StringCredentialsAdapter(
    private val delegate: AccountManager<GitLabAccount, GitLabCredentials>,
  ) : AccountManager<GitLabAccount, String> {
    override val accountsState get() = delegate.accountsState
    override val canPersistCredentials get() = delegate.canPersistCredentials
    override suspend fun findCredentials(account: GitLabAccount) = delegate.findCredentials(account)?.accessToken
    override fun getCredentialsFlow(account: GitLabAccount) = delegate.getCredentialsFlow(account).map { it?.accessToken }
    override suspend fun updateAccount(account: GitLabAccount, credentials: String) = error("Use GitLabAccountManager directly")
    override suspend fun updateAccounts(accountsWithCredentials: Map<GitLabAccount, String?>) = error("Use GitLabAccountManager directly")
    override suspend fun removeAccount(account: GitLabAccount) = delegate.removeAccount(account)
  }
}
