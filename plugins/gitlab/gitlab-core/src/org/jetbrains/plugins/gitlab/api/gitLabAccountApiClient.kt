// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.openapi.components.service
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager

/**
 * Gets a client that acquires the account credentials before each API call.
 *
 * The requests performed by the client may throw additional exceptions if something goes wrong when acquiring the credentials:
 * * [org.jetbrains.plugins.gitlab.authentication.accounts.GitLabMissingCredentialsException] - if the client could not acquire any credentials from the store
 * * [org.jetbrains.plugins.gitlab.authentication.accounts.GitLabCredentialsRefreshException] - if the credentials are expired, and the client has failed to refresh them
 */
fun GitLabApiManager.getClient(account: GitLabAccount): GitLabApi =
  getClient(account.server) {
    service<GitLabAccountManager>().getAndRefreshCredentials(account).accessToken
  }