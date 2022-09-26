// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import git4idea.remote.hosting.HostedGitRepositoryConnectionManager
import git4idea.remote.hosting.HostedGitRepositoryConnectionValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

interface GitLabProjectConnectionManager : HostedGitRepositoryConnectionManager<GitLabProjectMapping, GitLabAccount, GitLabProjectConnection>

internal class GitLabProjectConnectionManagerImpl(scope: CoroutineScope,
                                                  repositoriesManager: GitLabProjectsManager,
                                                  accountManager: GitLabAccountManager)
  : GitLabProjectConnectionManager {

  override val state = MutableStateFlow<GitLabProjectConnection?>(null)

  init {
    scope.launch {
      HostedGitRepositoryConnectionValidator.validate(state, repositoriesManager, accountManager)
    }
  }

  override suspend fun tryConnect(repo: GitLabProjectMapping, account: GitLabAccount) {
    state.emit(GitLabProjectConnection(repo, account))
  }

  override suspend fun disconnect() {
    state.emit(null)
  }
}
