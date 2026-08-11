// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.openapi.components.serviceAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.api.toHttpsNormalizedURI
import org.jetbrains.plugins.gitlab.authentication.GitLabCredentials

/**
 * An application service providing the ability to register GitLab accounts with the application
 *
 * NB: credentials will only be persisted if [com.intellij.ide.passwordSafe.PasswordSafe.isMemoryOnly] is false
 */
@ApiStatus.Experimental
interface GitLabAccountRegistrar {
  /**
   * Retrieve an actual username from the GitLab [server] and either register a new account or update the [credentials]
   * for existing account
   */
  suspend fun addAccount(server: GitLabServerPath, credentials: GitLabCredentials): GitLabAccount

  /**
   * Register a new account or update the [credentials] for account with a matching [server] and [username]
   */
  suspend fun addAccount(server: GitLabServerPath, username: String, credentials: GitLabCredentials): GitLabAccount
}

internal class GitLabAccountRegistrarImpl : GitLabAccountRegistrar {
  override suspend fun addAccount(server: GitLabServerPath, credentials: GitLabCredentials): GitLabAccount {
    val api = serviceAsync<GitLabApiManager>().getClient(server, credentials.accessToken)
    val user = withContext(Dispatchers.IO) {
      api.graphQL.getCurrentUser()
    }
    return addAccount(server, user.username, credentials)
  }

  override suspend fun addAccount(server: GitLabServerPath, username: String, credentials: GitLabCredentials): GitLabAccount {
    require(username.isNotBlank()) { "Username cannot be empty" }
    require(credentials.accessToken.isNotBlank()) { "Token cannot be empty" }

    val accountManager = serviceAsync<GitLabAccountManager>()
    val existingAccounts = accountManager.accountsState.value
    val account = existingAccounts.find { it.server.toHttpsNormalizedURI() == server.toHttpsNormalizedURI() && it.name == username }
                  ?: GitLabAccount(server = server, name = username)
    accountManager.updateAccount(account, credentials)
    return account
  }
}