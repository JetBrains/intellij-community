// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.auth.ui.AccountsDetailsLoader
import com.intellij.collaboration.auth.ui.AccountsDetailsLoader.Result
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import java.awt.Image

internal class GHAccountsDetailsLoader(private val indicatorsProvider: ProgressIndicatorsProvider,
                                       private val executorSupplier: (GithubAccount) -> GithubApiRequestExecutor?)
  : AccountsDetailsLoader<GithubAccount, GithubUserDetailed> {

  override fun loadDetailsAsync(account: GithubAccount): Deferred<Result<GithubUserDetailed>> {
    val executor = executorSupplier(account) ?: return CompletableDeferred<Result<GithubUserDetailed>>(
      Result.Error(GithubBundle.message("account.token.missing"), true))

    return ProgressManager.getInstance().submitIOTask(indicatorsProvider, true) {
      doLoadDetails(executor, it, account)
    }.asDeferred()
  }

  private fun doLoadDetails(executor: GithubApiRequestExecutor, indicator: ProgressIndicator, account: GithubAccount)
    : Result<GithubAuthenticatedUser> {

    val (details, scopes) = try {
      GHSecurityUtil.loadCurrentUserWithScopes(executor, indicator, account.server)
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

  override fun loadAvatarAsync(account: GithubAccount, url: String): Deferred<Image?> {
    val apiExecutor = executorSupplier(account) ?: return CompletableDeferred<Image?>(null).apply { complete(null) }
    return CachingGHUserAvatarLoader.getInstance().requestAvatar(apiExecutor, url).asDeferred()
  }
}