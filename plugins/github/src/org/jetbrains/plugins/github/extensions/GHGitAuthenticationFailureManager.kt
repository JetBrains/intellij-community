// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.collaboration.async.disposingScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import java.util.concurrent.ConcurrentHashMap

internal class GHGitAuthenticationFailureManager : Disposable {
  private val storeMap = ConcurrentHashMap<GithubAccount, Set<String>>()

  init {
    disposingScope().launch {
      val accountsState = service<GHAccountManager>().accountsState
      val prev = accountsState.value
      accountsState.collect {
        prev.forEach { (acc, token) ->
          if (it[acc] != token) {
            storeMap.remove(acc)
          }
        }
      }
    }
  }

  fun ignoreAccount(url: String, account: GithubAccount) {
    storeMap.compute(account) { _, current -> current?.plus(url) ?: setOf(url) }
  }

  fun isAccountIgnored(url: String, account: GithubAccount): Boolean = storeMap[account]?.contains(url) ?: false

  override fun dispose() {
    storeMap.clear()
  }
}