// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.PasswordSafeSettingsListener
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.util.GithubUtil
import kotlin.properties.Delegates.observable

internal val GithubAccount.isGHAccount: Boolean get() = server.isGithubDotCom

/**
 * Handles application-level Github accounts
 */
@State(name = "GithubAccounts", storages = [
  Storage(value = "github.xml"),
  Storage(value = "github_settings.xml", deprecated = true)
], reportStatistic = false)
internal class GithubAccountManager : PersistentStateComponent<Array<GithubAccount>> {
  var accounts: Set<GithubAccount> by observable(setOf()) { _, oldValue, newValue ->
    oldValue.filter { it !in newValue }.forEach(this::accountRemoved)
    LOG.debug("Account list changed to: $newValue")
  }

  private fun accountRemoved(account: GithubAccount) {
    updateAccountToken(account, null)
    getApplication().messageBus.syncPublisher(ACCOUNT_REMOVED_TOPIC).accountRemoved(account)
  }

  /**
   * Add/update/remove Github OAuth token from application
   */
  fun updateAccountToken(account: GithubAccount, token: String?) {
    PasswordSafe.instance.set(createCredentialAttributes(account.id), token?.let { createCredentials(account.id, it) })
    LOG.debug((if (token == null) "Cleared" else "Updated") + " OAuth token for account: $account")

    fireTokenChanged(account)
  }

  private fun fireTokenChanged(account: GithubAccount) {
    GithubApiRequestExecutorManager.getInstance().tokenChanged(account) // update cached executor tokens before calling listeners
    getApplication().messageBus.syncPublisher(ACCOUNT_TOKEN_CHANGED_TOPIC).tokenChanged(account)
  }

  /**
   * Retrieve OAuth token for account from password safe
   */
  fun getTokenForAccount(account: GithubAccount): String? = PasswordSafe.instance.get(createCredentialAttributes(account.id))?.getPasswordAsString()

  override fun getState() = accounts.toTypedArray()

  override fun loadState(state: Array<GithubAccount>) {
    accounts = state.toHashSet()
  }

  companion object {
    private val LOG = logger<GithubAccountManager>()

    @JvmStatic
    val ACCOUNT_REMOVED_TOPIC = Topic("GITHUB_ACCOUNT_REMOVED", AccountRemovedListener::class.java)

    @JvmStatic
    val ACCOUNT_TOKEN_CHANGED_TOPIC = Topic("GITHUB_ACCOUNT_TOKEN_CHANGED", AccountTokenChangedListener::class.java)

    fun createAccount(name: String, server: GithubServerPath) = GithubAccount(name, server)
  }

  class PasswordStorageClearedListener : PasswordSafeSettingsListener {
    override fun credentialStoreCleared() {
      val accountManager = service<GithubAccountManager>()
      accountManager.accounts.forEach { accountManager.fireTokenChanged(it) }
    }
  }
}

private fun createCredentialAttributes(accountId: String) = CredentialAttributes(createServiceName(accountId))

private fun createCredentials(accountId: String, token: String) = Credentials(accountId, token)

private fun createServiceName(accountId: String): String = generateServiceName(GithubUtil.SERVICE_DISPLAY_NAME, accountId)

interface AccountRemovedListener {
  fun accountRemoved(removedAccount: GithubAccount)
}

interface AccountTokenChangedListener {
  fun tokenChanged(account: GithubAccount)
}