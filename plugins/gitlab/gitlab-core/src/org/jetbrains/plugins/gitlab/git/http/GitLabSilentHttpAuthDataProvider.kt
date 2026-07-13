// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.git.http

import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.hosting.http.HostedGitAuthenticationFailureManager
import git4idea.remote.hosting.http.SilentHostedGitHttpAuthDataProviderBase
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.GitLabCredentials
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder

private val LOG = logger<GitLabSilentHttpAuthDataProvider>()

internal class GitLabSilentHttpAuthDataProvider : SilentHostedGitHttpAuthDataProviderBase<GitLabAccount, GitLabCredentials>() {
  override val providerId: String = "GitLab Plugin"

  override val accountManager: GitLabAccountManager
    get() = service<GitLabAccountManager>()

  override fun getDefaultAccountHolder(project: Project): DefaultAccountHolder<GitLabAccount> {
    return project.service<GitLabProjectDefaultAccountHolder>()
  }

  override fun getAuthFailureManager(project: Project): HostedGitAuthenticationFailureManager<GitLabAccount> {
    return project.service<GitLabGitAuthenticationFailureManager>()
  }

  override suspend fun getAuthData(account: GitLabAccount): AuthData? {
    try {
      val credentials = accountManager.getAndRefreshCredentials(account)
      val login = service<GitLabApiManager>()
        .getClient(account.server, credentials.accessToken)
        .graphQL.getCurrentUser().username
      return AuthData(login, credentials.accessToken)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("Cannot acquire auth data for $account", e)
      return null
    }
  }
}
