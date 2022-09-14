// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.combineState
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModelBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

internal class GHRepositoryAndAccountSelectorViewModel(
  private val scope: CoroutineScope,
  repositoriesManager: GHHostedRepositoriesManager,
  accountManager: GHAccountManager,
  private val onSelected: suspend (GHGitRepositoryMapping, GithubAccount) -> Unit
) : RepositoryAndAccountSelectorViewModelBase<GHGitRepositoryMapping, GithubAccount>(scope, repositoriesManager, accountManager) {

  val githubLoginAvailableState: StateFlow<Boolean> =
    combineState(scope, repoSelectionState, accountSelectionState, missingCredentialsState, ::isGithubLoginAvailable)

  private fun isGithubLoginAvailable(repo: GHGitRepositoryMapping?, account: GithubAccount?, credsMissing: Boolean): Boolean {
    if (repo == null) return false
    return repo.repository.serverPath.isGithubDotCom && (account == null || credsMissing)
  }

  val gheLoginAvailableState: StateFlow<Boolean> =
    combineState(scope, repoSelectionState, accountSelectionState, missingCredentialsState, ::isGheLoginVisible)


  private fun isGheLoginVisible(repo: GHGitRepositoryMapping?, account: GithubAccount?, credsMissing: Boolean): Boolean {
    if (repo == null) return false
    return !repo.repository.serverPath.isGithubDotCom && (account == null || credsMissing)
  }

  override fun submitSelection() {
    val repo = repoSelectionState.value ?: return
    val account = accountSelectionState.value ?: return
    scope.launch {
      onSelected(repo, account)
    }
  }
}