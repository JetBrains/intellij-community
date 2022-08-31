// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsDetailsLoader
import com.intellij.collaboration.auth.ui.AccountsDetailsLoader.Result
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runUnderIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import java.awt.Image

internal class GHAccountsDetailsLoader(private val executorSupplier: (GithubAccount) -> GithubApiRequestExecutor?)
  : AccountsDetailsLoader<GithubAccount, GithubUserDetailed> {

  override suspend fun loadDetails(account: GithubAccount): Result<GithubUserDetailed> {
    val executor = try {
      executorSupplier(account)
    }
    catch (e: Exception) {
      null
    }
    if (executor == null) return Result.Error(GithubBundle.message("account.token.missing"), true)
    return withContext(Dispatchers.IO) {
      runUnderIndicator {
        doLoadDetails(executor, account)
      }
    }
  }

  private fun doLoadDetails(executor: GithubApiRequestExecutor, account: GithubAccount)
    : Result<GithubAuthenticatedUser> {

    val (details, scopes) = try {
      GHSecurityUtil.loadCurrentUserWithScopes(executor, ProgressManager.getInstance().progressIndicator, account.server)
    }
    catch (e: Throwable) {
      val errorMessage = ExceptionUtil.getPresentableMessage(e)
      return Result.Error(errorMessage, false)
    }
    if (!GHSecurityUtil.isEnoughScopes(scopes.orEmpty())) {
      return Result.Error(GithubBundle.message("account.scopes.insufficient"), true)
    }

    return Result.Success(details)
  }

  override suspend fun loadAvatar(account: GithubAccount, url: String): Image? {
    val apiExecutor = executorSupplier(account) ?: return null
    return CachingGHUserAvatarLoader.getInstance().requestAvatar(apiExecutor, url).await()
  }
}