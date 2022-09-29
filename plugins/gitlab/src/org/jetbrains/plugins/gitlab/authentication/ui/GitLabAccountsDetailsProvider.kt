// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.LazyLoadingAccountsDetailsProvider
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.api.request.loadImage
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.ui.GitLabBundle
import java.awt.Image

internal class GitLabAccountsDetailsProvider(scope: CoroutineScope,
                                             private val apiClientSupplier: suspend (GitLabAccount) -> GitLabApi?)
  : LazyLoadingAccountsDetailsProvider<GitLabAccount, GitLabUserDTO>(scope, EmptyIcon.ICON_16) {

  override suspend fun loadDetails(account: GitLabAccount): Result<GitLabUserDTO> {
    val api = apiClientSupplier(account) ?: return Result.Error(GitLabBundle.message("account.token.missing"), true)
    val details = api.getCurrentUser(account.server) ?: return Result.Error(GitLabBundle.message("account.token.invalid"), true)
    return Result.Success(details)
  }

  override suspend fun loadAvatar(account: GitLabAccount, url: String): Image? {
    val api = apiClientSupplier(account) ?: return null
    val actualUrl = account.server.uri + url
    return api.loadImage(actualUrl)
  }
}