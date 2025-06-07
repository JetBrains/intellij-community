// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.util

import com.intellij.collaboration.async.combineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

internal object GitLabMergeRequestsUtil {
  fun repoAndAccountState(
    projectsState: StateFlow<Set<GitLabProjectMapping>>,
    accountsState: StateFlow<Set<GitLabAccount>>,
    selectedUrlAndAccountId: Pair<String, String>?
  ): StateFlow<Pair<GitLabProjectMapping, GitLabAccount>?> {
    val (url, accountId) = selectedUrlAndAccountId ?: return MutableStateFlow(null)
    return projectsState.combineState(accountsState) { repositories, accounts ->
      val repo = repositories.find { it.remote.url == url } ?: return@combineState null
      val account = accounts.find { it.id == accountId } ?: return@combineState null
      repo to account
    }
  }
}