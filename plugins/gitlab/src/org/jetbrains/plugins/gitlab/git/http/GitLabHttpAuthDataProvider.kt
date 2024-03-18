// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.git.http

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
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
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabBundle

class GitLabHttpAuthDataProvider : GitHttpAuthDataProvider {
  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String, login: String): AuthData? {
    val matchingServer = runBlockingMaybeCancellable {
      findKnownMatchingServer(url)
    } ?: return null

    return runBlockingMaybeCancellable {
      when (val loginResult = getLoginResult(project, url, matchingServer, login)) {
        is LoginResult.Success -> AuthData(loginResult.account.name, loginResult.token)
        is LoginResult.OtherMethod -> null
        is LoginResult.Failure -> throw ProcessCanceledException()
      }
    }
  }

  @RequiresBackgroundThread
  override fun getAuthData(project: Project, url: String): AuthData? {
    val matchingServer = runBlockingMaybeCancellable {
      findKnownMatchingServer(url)
    } ?: return null

    return runBlockingMaybeCancellable {
      when (val loginResult = getLoginResult(project, url, matchingServer, null)) {
        is LoginResult.Success -> AuthData(loginResult.account.name, loginResult.token)
        is LoginResult.OtherMethod -> null
        is LoginResult.Failure -> throw ProcessCanceledException()
      }
    }
  }
}

private suspend fun findKnownMatchingServer(url: String): GitLabServerPath? {
  return if (GitHostingUrlUtil.match(GitLabServerPath.DEFAULT_SERVER.toURI(), url)) {
    GitLabServerPath.DEFAULT_SERVER
  }
  else {
    val server = GitHostingUrlUtil.getUriFromRemoteUrl(url)?.let {
      GitLabServerPath(it.toString())
    } ?: return null
    val isGitLabServer = service<GitLabServersManager>().checkIsGitLabServer(server)
    if (isGitLabServer) server else null
  }
}

private suspend fun getLoginResult(project: Project, url: String, server: GitLabServerPath, login: String?): LoginResult {
  val accountManager = service<GitLabAccountManager>()
  val accountsWithTokens = accountManager.accountsState.value
    .filter { it.server == server }
    .associateWith { accountManager.findCredentials(it) }

  val loginResult = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    when (accountsWithTokens.size) {
      0 -> {
        GitLabLoginUtil.logInViaToken(project, null, server, login, accountManager::isAccountUnique)
      }
      1 -> {
        // apparently the token is missing or incorrect, otherwise this account should've been provided by silent provider
        val account = accountsWithTokens.keys.first()
        GitLabLoginUtil.updateToken(project, null, account, login, accountManager::isAccountUnique)
      }
      else -> {
        val description = GitLabBundle.message("account.choose.git.description", url)
        val account = GitLabLoginUtil.chooseAccount(project, null, description, accountsWithTokens.keys)
                      ?: return@withContext LoginResult.Failure
        accountsWithTokens[account]?.let { token -> LoginResult.Success(account, token) }
        ?: GitLabLoginUtil.updateToken(project, null, account, login, accountManager::isAccountUnique)
      }
    }
  }

  if (loginResult is LoginResult.Success) {
    accountManager.updateAccount(loginResult.account, loginResult.token)
  }

  return loginResult
}