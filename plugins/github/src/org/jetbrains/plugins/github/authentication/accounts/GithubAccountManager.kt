// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import com.intellij.collaboration.auth.AccountManager
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.util.GithubUtil

internal val GithubAccount.isGHAccount: Boolean get() = server.isGithubDotCom

/**
 * Handles application-level Github accounts
 */
@Service
internal class GithubAccountManager
  : AccountManager<GithubAccount, String>(GithubUtil.SERVICE_DISPLAY_NAME) {

  override val persistentAccounts: GHPersistentAccounts
    get() = service()

  override fun fireAccountRemoved(account: GithubAccount) {
    getApplication().messageBus.syncPublisher(ACCOUNT_REMOVED_TOPIC).accountRemoved(account)
  }

  override fun fireCredentialsChanged(account: GithubAccount) {
    GithubApiRequestExecutorManager.getInstance().tokenChanged(account) // update cached executor tokens before calling listeners
    getApplication().messageBus.syncPublisher(ACCOUNT_TOKEN_CHANGED_TOPIC).tokenChanged(account)
  }

  override fun serializeCredentials(credentials: String): String = credentials

  override fun deserializeCredentials(credentials: String): String = credentials

  companion object {
    @JvmStatic
    val ACCOUNT_REMOVED_TOPIC = Topic("GITHUB_ACCOUNT_REMOVED", AccountRemovedListener::class.java)

    @JvmStatic
    val ACCOUNT_TOKEN_CHANGED_TOPIC = Topic("GITHUB_ACCOUNT_TOKEN_CHANGED", AccountTokenChangedListener::class.java)

    fun createAccount(name: String, server: GithubServerPath) = GithubAccount(name, server)
  }
}

interface AccountRemovedListener {
  fun accountRemoved(removedAccount: GithubAccount)
}

interface AccountTokenChangedListener {
  fun tokenChanged(account: GithubAccount)
}