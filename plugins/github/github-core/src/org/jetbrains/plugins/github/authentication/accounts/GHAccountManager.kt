// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts

import com.intellij.collaboration.auth.AccountManagerBase
import com.intellij.collaboration.auth.AccountsRepository
import com.intellij.collaboration.auth.PasswordSafeCredentialsRepository
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.util.GithubUtil

internal val GithubAccount.isGHAccount: Boolean get() = server.isGithubDotCom

/**
 * Handles application-level Github accounts
 */
@Service
class GHAccountManager : AccountManagerBase<GithubAccount, String>(logger<GHAccountManager>()), Disposable {

  override fun accountsRepository(): AccountsRepository<GithubAccount> = service<GHPersistentAccounts>()

  override fun credentialsRepository() =
    PasswordSafeCredentialsRepository<GithubAccount, String>(GithubUtil.SERVICE_DISPLAY_NAME,
                                                             PasswordSafeCredentialsRepository.CredentialsMapper.Simple)

  companion object {
    fun createAccount(name: String, server: GithubServerPath) = GithubAccount(name, server)
  }

  override fun dispose() = Unit

  fun isAccountUnique(server: GithubServerPath, accountName: String): Boolean {
    return accountsState.value.none { account: GithubAccount ->
      account.server.equals(server, true) && account.name == accountName
    }
  }
}