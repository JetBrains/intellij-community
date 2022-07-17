// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsDetailsLoader
import com.intellij.collaboration.auth.ui.AccountsDetailsLoader.Result
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDto
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.api.request.loadImage
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.ui.GitLabBundle
import java.awt.Image

internal class GitLabAccountsDetailsLoader(private val coroutineScope: CoroutineScope,
                                           private val accountManager: GitLabAccountManager,
                                           private val accountsModel: GitLabAccountsListModel)
  : AccountsDetailsLoader<GitLabAccount, GitLabUserDto> {

  override fun loadDetailsAsync(account: GitLabAccount): Deferred<Result<GitLabUserDto>> = coroutineScope.async {
    loadDetails(account)
  }

  private suspend fun loadDetails(account: GitLabAccount): Result<GitLabUserDto> {
    val api = getApiClient(account) ?: return Result.Error(GitLabBundle.message("account.token.missing"), true)
    val details = api.getCurrentUser(account.server) ?: return Result.Error(GitLabBundle.message("account.token.invalid"), true)
    return Result.Success(details)
  }

  override fun loadAvatarAsync(account: GitLabAccount, url: String): Deferred<Image?> = coroutineScope.async {
    loadAvatar(account, url)
  }

  private suspend fun loadAvatar(account: GitLabAccount, url: String): Image? {
    val api = getApiClient(account) ?: return null
    return api.loadImage(url)
  }

  private fun getApiClient(account: GitLabAccount): GitLabApi? {
    val token = accountsModel.newCredentials.getOrElse(account) {
      accountManager.findCredentials(account)
    } ?: return null
    return service<GitLabApiManager>().getClient(token)
  }
}