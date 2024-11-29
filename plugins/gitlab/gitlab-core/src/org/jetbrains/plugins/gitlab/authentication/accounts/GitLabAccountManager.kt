// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.AccountManagerBase
import com.intellij.collaboration.auth.AccountsRepository
import com.intellij.collaboration.auth.PasswordSafeCredentialsRepository
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.toHttpsNormalizedURI
import org.jetbrains.plugins.gitlab.util.GitLabUtil

internal interface GitLabAccountManager : AccountManager<GitLabAccount, String> {
  fun isAccountUnique(server: GitLabServerPath, accountName: String): Boolean
}

class PersistentGitLabAccountManager :
  GitLabAccountManager,
  AccountManagerBase<GitLabAccount, String>(logger<GitLabAccountManager>()) {

  override fun accountsRepository(): AccountsRepository<GitLabAccount> = service<GitLabPersistentAccounts>()

  override fun credentialsRepository() =
    PasswordSafeCredentialsRepository<GitLabAccount, String>(
      GitLabUtil.SERVICE_NAME,
      PasswordSafeCredentialsRepository.CredentialsMapper.Simple
    )

  override fun isAccountUnique(server: GitLabServerPath, accountName: String): Boolean {
    return accountsState.value.none { account: GitLabAccount ->
      account.server.toHttpsNormalizedURI() == server.toHttpsNormalizedURI() && account.name == accountName
    }
  }
}