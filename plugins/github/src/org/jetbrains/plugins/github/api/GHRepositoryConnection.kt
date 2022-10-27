// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import git4idea.remote.hosting.HostedGitRepositoryConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

class GHRepositoryConnection(private val scope: CoroutineScope,
                             override val repo: GHGitRepositoryMapping,
                             override val account: GithubAccount,
                             val executor: GithubApiRequestExecutor)
  : HostedGitRepositoryConnection<GHGitRepositoryMapping, GithubAccount> {

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