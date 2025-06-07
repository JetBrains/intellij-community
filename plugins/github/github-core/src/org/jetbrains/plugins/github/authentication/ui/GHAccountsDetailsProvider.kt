// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.LazyLoadingAccountsDetailsProvider
import com.intellij.collaboration.auth.ui.cancelOnRemoval
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.openapi.components.service
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import java.awt.Image

@ApiStatus.Internal
class GHAccountsDetailsProvider(
  scope: CoroutineScope,
  private val executorSupplier: suspend (GithubAccount) -> GithubApiRequestExecutor?
) : LazyLoadingAccountsDetailsProvider<GithubAccount, GithubUserDetailed>(scope, CollaborationToolsIcons.Review.DefaultAvatar) {

  constructor(scope: CoroutineScope, accountManager: GHAccountManager, accountsModel: GHAccountsListModel)
    : this(scope, { getExecutor(accountManager, accountsModel, it) }) {
    cancelOnRemoval(accountsModel.accountsListModel)
  }

  constructor(scope: CoroutineScope, accountManager: GHAccountManager)
    : this(scope, { getExecutor(accountManager, it) }) {
    cancelOnRemoval(scope, accountManager)
  }

  override suspend fun loadDetails(account: GithubAccount): Result<GithubUserDetailed> {
    val executor = try {
      executorSupplier(account)
    }
    catch (e: Exception) {
      null
    }
    if (executor == null) return Result.Error(CollaborationToolsBundle.message("account.token.missing"), true)
    val details = try {
      executor.executeSuspend(GithubApiRequests.CurrentUser.get(account.server))
    }
    catch (e: Throwable) {
      val errorMessage = ExceptionUtil.getPresentableMessage(e)
      val needReLogin = e is GithubAuthenticationException
      return Result.Error(errorMessage, needReLogin)
    }

    return Result.Success(details)
  }

  override suspend fun loadAvatar(account: GithubAccount, url: String): Image? {
    val apiExecutor = executorSupplier(account) ?: return null
    return CachingGHUserAvatarLoader.getInstance().loadAvatar(apiExecutor, url)
  }

  companion object {
    private suspend fun getExecutor(accountManager: GHAccountManager, accountsModel: GHAccountsListModel, account: GithubAccount)
      : GithubApiRequestExecutor? {
      return accountsModel.newCredentials.getOrElse(account) {
        accountManager.findCredentials(account)
      }?.let { token ->
        service<GithubApiRequestExecutor.Factory>().create(account.server, token)
      }
    }

    private suspend fun getExecutor(accountManager: GHAccountManager, account: GithubAccount)
      : GithubApiRequestExecutor? {
      return accountManager.findCredentials(account)?.let { token ->
        service<GithubApiRequestExecutor.Factory>().create(account.server, token)
      }
    }
  }
}