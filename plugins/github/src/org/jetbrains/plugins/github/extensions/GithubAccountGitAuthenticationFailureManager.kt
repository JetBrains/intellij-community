// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.github.authentication.accounts.AccountRemovedListener
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import java.util.concurrent.ConcurrentHashMap

class GithubAccountGitAuthenticationFailureManager {
  private val storeMap = ConcurrentHashMap<GithubAccount, Set<String>>()

  init {
    val busConnection = ApplicationManager.getApplication().messageBus.connect()
    busConnection.subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, object : AccountTokenChangedListener {
      override fun tokenChanged(account: GithubAccount) {
        storeMap.remove(account)
      }
    })
    busConnection.subscribe(GithubAccountManager.ACCOUNT_REMOVED_TOPIC, object : AccountRemovedListener {
      override fun accountRemoved(removedAccount: GithubAccount) {
        storeMap.remove(removedAccount)
      }
    })
  }

  fun ignoreAccount(url: String, account: GithubAccount) {
    storeMap.compute(account) { _, current -> current?.plus(url) ?: setOf(url) }
  }

  fun isAccountIgnored(url: String, account: GithubAccount): Boolean = storeMap[account]?.contains(url) ?: false
}
