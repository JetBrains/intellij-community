// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.auth.ui.LoadingAccountsDetailsProvider
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.IconUtil
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GithubUserDetailed
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import java.util.concurrent.CompletableFuture

internal class GHAccountsDetailsProvider(progressIndicatorsProvider: ProgressIndicatorsProvider,
                                         private val accountManager: GHAccountManager,
                                         private val accountsModel: GHAccountsListModel)
  : LoadingAccountsDetailsProvider<GithubAccount, GithubUserDetailed>(progressIndicatorsProvider) {

  override val defaultIcon = IconUtil.resizeSquared(GithubIcons.DefaultAvatar, 40)

  override fun scheduleLoad(account: GithubAccount,
                            indicator: ProgressIndicator): CompletableFuture<DetailsLoadingResult<GithubUserDetailed>> {
    val token = accountsModel.newCredentials.getOrElse(account) {
      accountManager.findCredentials(account)
    } ?: return CompletableFuture.completedFuture(noToken())
    val executor = service<GithubApiRequestExecutor.Factory>().create(token)
    return ProgressManager.getInstance().submitIOTask(EmptyProgressIndicator()) {
      val (details, scopes) = GHSecurityUtil.loadCurrentUserWithScopes(executor, it, account.server)
      if (!GHSecurityUtil.isEnoughScopes(scopes.orEmpty())) return@submitIOTask noScopes()
      val image = details.avatarUrl?.let { url -> CachingGHUserAvatarLoader.getInstance().requestAvatar(executor, url).join() }
      DetailsLoadingResult<GithubUserDetailed>(details, image, null, false)
    }.successOnEdt(ModalityState.any()) {
      accountsModel.accountsListModel.contentsChanged(account)
      it
    }
  }

  private fun noToken() = DetailsLoadingResult<GithubUserDetailed>(null, null, GithubBundle.message("account.token.missing"), true)
  private fun noScopes() = DetailsLoadingResult<GithubUserDetailed>(null, null, GithubBundle.message("account.scopes.insufficient"), true)
}