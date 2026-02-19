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
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder

private val LOG = logger<GitLabSilentHttpAuthDataProvider>()

internal class GitLabSilentHttpAuthDataProvider : SilentHostedGitHttpAuthDataProvider<GitLabAccount>() {
  override val providerId: String = "GitLab Plugin"

  override val accountManager: AccountManager<GitLabAccount, String>
    get() = service<GitLabAccountManager>()

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
}
