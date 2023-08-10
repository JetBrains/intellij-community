// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.util.URIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

internal fun createSingleProjectAndAccountState(
  cs: CoroutineScope,
  projectsManager: GitLabProjectsManager,
  accountManager: GitLabAccountManager
): StateFlow<Pair<GitLabProjectMapping, GitLabAccount>?> =
  combineState(cs, projectsManager.knownRepositoriesState, accountManager.accountsState) { repos, accounts ->
    repos.singleOrNull()?.let { repo ->
      accounts.singleOrNull { URIUtil.equalWithoutSchema(it.server.toURI(), repo.repository.serverPath.toURI()) }?.let {
        repo to it
      }
    }
  }