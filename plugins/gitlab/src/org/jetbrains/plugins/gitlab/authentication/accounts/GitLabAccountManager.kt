// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.auth.AccountManagerBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.plugins.gitlab.util.GitLabUtil

@Service
internal class GitLabAccountManager : AccountManagerBase<GitLabAccount, String>(GitLabUtil.SERVICE_NAME) {

  override fun accountsRepository() = service<GitLabPersistentAccounts>()

  override fun serializeCredentials(credentials: String) = credentials
  override fun deserializeCredentials(credentials: String) = credentials
}