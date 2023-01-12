// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.collaboration.async.disposingScope
import com.intellij.collaboration.auth.AccountUrlAuthenticationFailuresHolder
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

internal class GHGitAuthenticationFailureManager : Disposable {
  private val holder = AccountUrlAuthenticationFailuresHolder(disposingScope()) {
    service<GHAccountManager>()
  }.also {
    Disposer.register(this, it)
  }

  fun ignoreAccount(url: String, account: GithubAccount) {
    holder.markFailed(account, url)
  }

  fun isAccountIgnored(url: String, account: GithubAccount): Boolean = holder.isFailed(account, url)

  override fun dispose() = Unit
}