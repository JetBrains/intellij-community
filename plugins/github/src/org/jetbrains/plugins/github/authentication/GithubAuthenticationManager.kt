// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication

import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException

/**
 * Entry point for interactions with Github authentication subsystem
 */
class GithubAuthenticationManager internal constructor(private val accountManager: GithubAccountManager) {
  @CalledInAny
  internal fun getTokenForAccount(account: GithubAccount): String {
    val token = accountManager.getTokenForAccount(account)
    if (token == null) throw GithubAuthenticationException("Missing access token for account $account")
    else return token
  }
}
