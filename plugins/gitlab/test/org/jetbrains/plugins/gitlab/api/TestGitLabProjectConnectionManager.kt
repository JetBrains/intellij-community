// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

// easier than fiddling with mockito without mockito-kotlin
@TestOnly
internal class TestGitLabProjectConnectionManager : GitLabProjectConnectionManager {
  override val state = MutableStateFlow<GitLabProjectConnection?>(null)

  override suspend fun tryConnect(repo: GitLabProjectMapping, account: GitLabAccount) {
    state.emit(GitLabProjectConnection(repo, account, ""))
  }

  override suspend fun disconnect() {
    state.emit(null)
  }
}