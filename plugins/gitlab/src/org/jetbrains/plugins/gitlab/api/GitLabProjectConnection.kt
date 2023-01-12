// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import git4idea.remote.hosting.HostedGitRepositoryConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

class GitLabProjectConnection(
  private val scope: CoroutineScope,
  override val repo: GitLabProjectMapping,
  override val account: GitLabAccount,
  val apiClient: GitLabApi
) : HostedGitRepositoryConnection<GitLabProjectMapping, GitLabAccount> {

  override suspend fun close() {
    try {
      (scope.coroutineContext[Job] ?: error("Missing job")).cancelAndJoin()
    }
    catch (_: Exception) {
    }
  }

  override suspend fun awaitClose() {
    try {
      (scope.coroutineContext[Job] ?: error("Missing job")).join()
    }
    catch (_: Exception) {
    }
  }
}