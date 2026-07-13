// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.LazyLoadingAccountsDetailsProvider
import com.intellij.collaboration.auth.ui.cancelOnRemoval
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabApiUtil
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.api.request.loadImage
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabCredentialsRefreshException
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabMissingCredentialsException
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Image

@ApiStatus.Internal
class GitLabAccountsDetailsProvider private constructor(
  scope: CoroutineScope,
  private val apiClientSupplier: suspend (GitLabAccount) -> GitLabApi,
) : LazyLoadingAccountsDetailsProvider<GitLabAccount, GitLabUserDTO>(scope, CollaborationToolsIcons.Review.DefaultAvatar) {

  internal constructor(
    scope: CoroutineScope,
    apiManager: GitLabApiManager,
    accountManager: GitLabAccountManager,
    accountsModel: GitLabAccountsListModel,
  ) : this(scope, { apiManager.getClient(it, accountManager, accountsModel) }) {
    cancelOnRemoval(accountsModel.accountsListModel)
  }

  constructor(
    scope: CoroutineScope,
    apiManager: GitLabApiManager,
    accountManager: GitLabAccountManager,
  ) : this(scope, apiManager::getClient) {
    cancelOnRemoval(scope, accountManager)
  }

  override suspend fun loadDetails(account: GitLabAccount): Result<GitLabUserDTO> {
    try {
      val api = apiClientSupplier(account)
      val serversManager = service<GitLabServersManager>()
      val supported = serversManager.earliestSupportedVersion <= api.getMetadata().version
      if (!supported) return Result.Error(GitLabBundle.message("server.version.unsupported.short"), false)
      val details = api.graphQL.getCurrentUser()
      return Result.Success(details)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      if (e is GitLabCredentialsRefreshException) {
        return Result.Error(CollaborationToolsBundle.message("account.credentials.refresh.failed"), true)
      }
      if (e is GitLabMissingCredentialsException) {
        return Result.Error(CollaborationToolsBundle.message("account.token.missing"), true)
      }
      if (GitLabApiUtil.isInvalidCredentialsError(e)) {
        return Result.Error(CollaborationToolsBundle.message("account.token.invalid"), true)
      }
      @Suppress("HardCodedStringLiteral")
      return Result.Error(e.localizedMessage, false)
    }
  }

  override suspend fun loadAvatar(account: GitLabAccount, url: String): Image? {
    return try {
      val api = apiClientSupplier(account)
      val actualUrl = if (url.startsWith("http")) url else account.server.uri + url
      api.loadImage(actualUrl)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      null
    }
  }
}

private fun GitLabApiManager.getClient(
  account: GitLabAccount,
  accountManager: GitLabAccountManager,
  accountsModel: GitLabAccountsListModel,
): GitLabApi = getClient(account.server) {
  accountsModel.newCredentials[account]?.accessToken
  ?: accountManager.getAndRefreshCredentials(account).accessToken
}