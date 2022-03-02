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

  init {
    @Suppress("DEPRECATION")
    addListener(this, object : AccountsListener<GithubAccount> {
      override fun onAccountListChanged(old: Collection<GithubAccount>, new: Collection<GithubAccount>) {
        val removedPublisher = ApplicationManager.getApplication().messageBus.syncPublisher(ACCOUNT_REMOVED_TOPIC)
        for (account in (old - new)) {
          removedPublisher.accountRemoved(account)
        }
        val tokenPublisher = ApplicationManager.getApplication().messageBus.syncPublisher(ACCOUNT_TOKEN_CHANGED_TOPIC)
        for (account in (new - old)) {
          tokenPublisher.tokenChanged(account)
        }
      }

      override fun onAccountCredentialsChanged(account: GithubAccount) =
        ApplicationManager.getApplication().messageBus.syncPublisher(ACCOUNT_TOKEN_CHANGED_TOPIC).tokenChanged(account)
    })
  }

  companion object {
    @Deprecated("Use TOPIC")
    @Suppress("DEPRECATION")
    @JvmStatic
    val ACCOUNT_REMOVED_TOPIC = Topic("GITHUB_ACCOUNT_REMOVED", AccountRemovedListener::class.java)

    @Deprecated("Use TOPIC")
    @Suppress("DEPRECATION")
    @JvmStatic
    val ACCOUNT_TOKEN_CHANGED_TOPIC = Topic("GITHUB_ACCOUNT_TOKEN_CHANGED", AccountTokenChangedListener::class.java)

    fun createAccount(name: String, server: GithubServerPath) = GithubAccount(name, server)
  }
}

@Deprecated("Use GithubAuthenticationManager.addListener")
@ApiStatus.ScheduledForRemoval
interface AccountRemovedListener {
  fun accountRemoved(removedAccount: GithubAccount)
}

@Deprecated("Use GithubAuthenticationManager.addListener")
@ApiStatus.ScheduledForRemoval
interface AccountTokenChangedListener {
  fun tokenChanged(account: GithubAccount)
}