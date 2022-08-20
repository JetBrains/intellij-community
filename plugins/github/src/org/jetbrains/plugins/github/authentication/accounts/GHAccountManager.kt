// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.collaboration.auth.AccountManagerBase
import com.intellij.collaboration.auth.AccountsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.util.GithubUtil

internal val GithubAccount.isGHAccount: Boolean get() = server.isGithubDotCom

/**
 * Handles application-level Github accounts
 */
@Service
internal class GHAccountManager
  : AccountManagerBase<GithubAccount, String>(GithubUtil.SERVICE_DISPLAY_NAME) {

  override fun accountsRepository() = service<GHPersistentAccounts>()

  override fun serializeCredentials(credentials: String): String = credentials
  override fun deserializeCredentials(credentials: String): String = credentials

  companion object {
    fun createAccount(name: String, server: GithubServerPath) = GithubAccount(name, server)
  }
}