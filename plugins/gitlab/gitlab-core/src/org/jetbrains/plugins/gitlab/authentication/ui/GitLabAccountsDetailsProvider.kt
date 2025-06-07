// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.LazyLoadingAccountsDetailsProvider
import com.intellij.collaboration.auth.ui.cancelOnRemoval
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import com.intellij.openapi.components.service
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabServersManager
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.api.request.loadImage
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabSelectorErrorStatusPresenter.Companion.isAuthorizationException
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.Image

@ApiStatus.Internal
class GitLabAccountsDetailsProvider private constructor(
  scope: CoroutineScope,
  private val apiClientSupplier: suspend (GitLabAccount) -> GitLabApi?
) : LazyLoadingAccountsDetailsProvider<GitLabAccount, GitLabUserDTO>(scope, CollaborationToolsIcons.Review.DefaultAvatar) {

  internal constructor(
    scope: CoroutineScope,
    accountsModel: GitLabAccountsListModel,
    apiClientSupplier: suspend (GitLabAccount) -> GitLabApi?
  ) : this(scope, apiClientSupplier) {
    cancelOnRemoval(accountsModel.accountsListModel)
  }

  constructor(
    scope: CoroutineScope,
    accountManager: GitLabAccountManager,
    apiClientSupplier: suspend (GitLabAccount) -> GitLabApi?
  ) : this(scope, apiClientSupplier) {
    cancelOnRemoval(scope, accountManager)
  }

  override suspend fun loadDetails(account: GitLabAccount): Result<GitLabUserDTO> {
    try {
      val api = apiClientSupplier(account) ?: return Result.Error(CollaborationToolsBundle.message("account.token.missing"), true)
      val details = runCatchingUser { api.graphQL.getCurrentUser() }.getOrElse {
        if (isAuthorizationException(it)) return Result.Error(CollaborationToolsBundle.message("account.token.invalid"), true)
        return Result.Error(it.localizedMessage, false)
      }
      val serversManager = service<GitLabServersManager>()
      val supported = serversManager.earliestSupportedVersion <= api.getMetadata().version
      if (!supported) return Result.Error(GitLabBundle.message("server.version.unsupported.short"), false)
      return Result.Success(details)
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      return Result.Error(e.message, false)
    }
  }

  override suspend fun loadAvatar(account: GitLabAccount, url: String): Image? {
    val api = apiClientSupplier(account) ?: return null
    val actualUrl = if (url.startsWith("http")) url else account.server.uri + url
    return api.loadImage(actualUrl)
  }
}