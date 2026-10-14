// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount


/**
 * Entry point for interactions with Github authentication subsystem
 */
@Service
@Deprecated("deprecated in favor of GHAccountsUtil")
class GithubAuthenticationManager internal constructor() {
  private val accountManager: GHAccountManager get() = service()

  @CalledInAny
  fun getAccounts(): Set<GithubAccount> = accountManager.accountsState.value

  companion object {
    @JvmStatic
    fun getInstance(): GithubAuthenticationManager = service()
  }
}