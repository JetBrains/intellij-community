// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import java.util.concurrent.ConcurrentHashMap

internal class GHGitAuthenticationFailureManager {
  private val storeMap = ConcurrentHashMap<GithubAccount, Set<String>>()

  fun ignoreAccount(url: String, account: GithubAccount) {
    storeMap.compute(account) { _, current -> current?.plus(url) ?: setOf(url) }
  }

  fun isAccountIgnored(url: String, account: GithubAccount): Boolean = storeMap[account]?.contains(url) ?: false

  class AccountTokenListener(private val project: Project) : AccountTokenChangedListener {
    override fun tokenChanged(account: GithubAccount) {
      project.service<GHGitAuthenticationFailureManager>().storeMap.remove(account)
    }
  }
}
