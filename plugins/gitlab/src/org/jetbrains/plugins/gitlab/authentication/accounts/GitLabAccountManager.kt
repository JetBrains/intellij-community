// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.auth.AccountManagerBase
import com.intellij.collaboration.auth.PasswordSafeCredentialsRepository
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.gitlab.util.GitLabUtil

internal interface GitLabAccountManager : AccountManager<GitLabAccount, String>, Disposable

internal class PersistentGitLabAccountManager :
  GitLabAccountManager,
  AccountManagerBase<GitLabAccount, String>(logger<GitLabAccountManager>()) {

  override fun accountsRepository() = service<GitLabPersistentAccounts>()

  override fun credentialsRepository() =
    PasswordSafeCredentialsRepository<GitLabAccount, String>(GitLabUtil.SERVICE_NAME,
                                                             PasswordSafeCredentialsRepository.CredentialsMapper.Simple)

  override fun dispose() = Unit
}