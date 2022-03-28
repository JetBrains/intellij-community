// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsDetailsLoader
import com.intellij.collaboration.auth.ui.AccountsDetailsLoader.Result
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runUnderIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import java.awt.Image

internal class GHAccountsDetailsLoader(private val accountManager: GHAccountManager,
                                       private val accountsModel: GHAccountsListModel)
  : AccountsDetailsLoader<GithubAccount, GithubUserDetailed> {

  override suspend fun loadDetails(account: GithubAccount): Result<GithubUserDetailed> {
    val apiExecutor = createExecutor(account) ?: return Result.Error(GithubBundle.message("account.token.missing"), true)
    return withContext(Dispatchers.IO) {
      runUnderIndicator {
        doLoadDetails(apiExecutor, account)
      }
    }
  }

  override suspend fun loadAvatar(account: GithubAccount, url: String): Image? {
    val apiExecutor = createExecutor(account) ?: return null
    return withContext(Dispatchers.IO) {
      runUnderIndicator {
        CachingGHUserAvatarLoader.getInstance().requestAvatar(apiExecutor, url).join()
      }
    }
  }

  private fun createExecutor(account: GithubAccount): GithubApiRequestExecutor? {
    val token = accountsModel.newCredentials.getOrElse(account) {
      accountManager.findCredentials(account)
    } ?: return null
    return service<GithubApiRequestExecutor.Factory>().create(token)
  }

  private fun doLoadDetails(executor: GithubApiRequestExecutor, account: GithubAccount): Result<GithubUserDetailed> {
    val indicator = ProgressManager.getInstance().progressIndicator

    val (details, scopes) = GHSecurityUtil.loadCurrentUserWithScopes(executor, indicator, account.server)
    if (!GHSecurityUtil.isEnoughScopes(scopes.orEmpty())) return Result.Error(GithubBundle.message("account.scopes.insufficient"), true)
    return Result.Success(details)
  }
}