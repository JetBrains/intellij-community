// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.AccountManagerBase
import com.intellij.collaboration.auth.AccountsRepository
import com.intellij.collaboration.auth.PasswordSafeCredentialsRepository
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.toHttpsNormalizedURI
import org.jetbrains.plugins.gitlab.authentication.GitLabCredentials
import org.jetbrains.plugins.gitlab.authentication.GitLabOAuthService
import org.jetbrains.plugins.gitlab.util.GitLabUtil

@ApiStatus.Internal
interface GitLabAccountManager : AccountManager<GitLabAccount, GitLabCredentials> {
  fun isAccountUnique(server: GitLabServerPath, accountName: String): Boolean

  /**
   * Acquires the credentials and attempts to refresh if needed
   *
   * @throws GitLabMissingCredentialsException if the credentials could not be acquired for any reason
   * @throws GitLabCredentialsRefreshException if the credentials are expired, and the refresh failed
   */
  @Throws(GitLabMissingCredentialsException::class, GitLabCredentialsRefreshException::class)
  suspend fun getAndRefreshCredentials(account: GitLabAccount): GitLabCredentials
}

internal class PersistentGitLabAccountManager : GitLabAccountManager,
                                                AccountManagerBase<GitLabAccount, GitLabCredentials>(logger<GitLabAccountManager>()) {
  override suspend fun getAndRefreshCredentials(account: GitLabAccount): GitLabCredentials {
    return dataGuard.withLock {
      doGetAndRefreshCredentials(account)
    }
  }

  // done under [dataGuard] to avoid a token refresh race
  // it can still happen if credentials repository is updated externally, but the chance of this is negligible
  private suspend fun doGetAndRefreshCredentials(account: GitLabAccount): GitLabCredentials {
    val credentials = try {
      super.findCredentials(account) ?: throw GitLabMissingCredentialsException(account)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      throw GitLabMissingCredentialsException(account, e)
    }
    return when (credentials) {
      is GitLabCredentials.Token -> credentials
      is GitLabCredentials.OAuth -> {
        if (credentials.isAccessTokenValid()) return credentials
        try {
          GitLabOAuthService.instance.refreshToken(account.server, credentials.refreshToken).also {
            doUpdateAccount(account, it)
          }
        }
        catch (e: Exception) {
          rethrowControlFlowException(e)
          throw GitLabCredentialsRefreshException(account, e)
        }
      }
    }
  }

  override fun accountsRepository(): AccountsRepository<GitLabAccount> = service<GitLabPersistentAccounts>()

  override fun credentialsRepository() =
    PasswordSafeCredentialsRepository<GitLabAccount, GitLabCredentials>(GitLabUtil.SERVICE_NAME, GitLabCredentialsMapper)

  override fun isAccountUnique(server: GitLabServerPath, accountName: String): Boolean {
    return accountsState.value.none { account: GitLabAccount ->
      account.server.toHttpsNormalizedURI() == server.toHttpsNormalizedURI() && account.name == accountName
    }
  }

  private object GitLabCredentialsMapper : PasswordSafeCredentialsRepository.CredentialsMapper<GitLabCredentials> {
    override fun serialize(credentials: GitLabCredentials): String = Json.encodeToString(credentials)
    override fun deserialize(credentials: String): GitLabCredentials = try {
      Json.decodeFromString(credentials)
    }
    catch (_: IllegalArgumentException) {
      GitLabCredentials.Token(credentials)
    }
  }
}