// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.git.http

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.remote.GitHttpAuthDataProvider
import git4idea.remote.hosting.GitHostingUrlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabHttpAuthDataProvider : GitHttpAuthDataProvider {
  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String, login: String): AuthData? =
    runBlockingMaybeCancellable {
      when (val loginResult = performLogin(project, url, login)) {
        is LoginResult.Success -> AuthData(loginResult.account.name, loginResult.token)
        is LoginResult.OtherMethod -> null
        is LoginResult.Failure -> throw ProcessCanceledException()
    }
  }

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String): AuthData? =
    runBlockingMaybeCancellable {
      when (val loginResult = performLogin(project, url)) {
        is LoginResult.Success -> AuthData(loginResult.account.name, loginResult.token)
        is LoginResult.OtherMethod -> null
        is LoginResult.Failure -> throw ProcessCanceledException()
    }
  }
}

private suspend fun performLogin(project: Project, gitHostUrl: String, login: String? = null): LoginResult {
  val accountManager = serviceAsync<GitLabAccountManager>()
  val accountsWithTokens = accountManager.accountsState.value
    .filter { GitHostingUrlUtil.matchHost(it.server.toURI(), gitHostUrl) }
    .associateWith { accountManager.findCredentials(it) }

  val loginResult = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    when (accountsWithTokens.size) {
      0 -> accountManager.tryCreateAccount(project, gitHostUrl, login)
      1 -> accountManager.reLogInWithAccount(project, accountsWithTokens.keys.single(), login)
      else -> accountManager.selectAccountAndLogIn(project, accountsWithTokens, gitHostUrl, login)
    }
  }

  if (loginResult is LoginResult.Success) {
    accountManager.updateAccount(loginResult.account, loginResult.token)
  }

  return loginResult
}

private suspend fun GitLabAccountManager.tryCreateAccount(
  project: Project,
  gitHostUrl: String,
  login: String?,
): LoginResult {
  val server = GitHostingUrlUtil.getUriFromRemoteUrl(gitHostUrl)?.let {
    GitLabServerPath(it.toString())
  } ?: return LoginResult.OtherMethod
  val isGitLabServer = service<GitLabServersManager>().checkIsGitLabServer(server)
  if (!isGitLabServer) return LoginResult.OtherMethod
  return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    GitLabLoginUtil.logInViaToken(project, null, server, login, loginSource = GitLabLoginSource.GIT,::isAccountUnique)
  }
}

// apparently the token is missing or incorrect, otherwise this account should've been provided by silent provider
private suspend fun GitLabAccountManager.reLogInWithAccount(
  project: Project,
  account: GitLabAccount,
  login: String?,
): LoginResult = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
  GitLabLoginUtil.updateToken(project, null, account, login, loginSource = GitLabLoginSource.GIT, ::isAccountUnique)
}

private suspend fun GitLabAccountManager.selectAccountAndLogIn(
  project: Project,
  accountsWithTokens: Map<GitLabAccount, String?>,
  url: String,
  login: String?,
): LoginResult = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
  val description = GitLabBundle.message("account.choose.git.description", url)
  val account = GitLabLoginUtil.chooseAccount(project, null, description, accountsWithTokens.keys)
                ?: return@withContext LoginResult.Failure
  val token = accountsWithTokens[account]
  if (token == null) {
    GitLabLoginUtil.updateToken(project, null, account, login, loginSource = GitLabLoginSource.GIT, ::isAccountUnique)
  }
  else {
    LoginResult.Success(account, token)
  }
}