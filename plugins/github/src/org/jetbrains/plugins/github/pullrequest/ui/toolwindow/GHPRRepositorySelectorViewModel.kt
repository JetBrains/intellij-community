// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

interface GHPRRepositorySelectorViewModel {
  val repositoriesState: StateFlow<List<GHGitRepositoryMapping>>
  val repoSelectionState: MutableStateFlow<GHGitRepositoryMapping?>

  val accountsState: StateFlow<List<GithubAccount>>
  val accountSelectionState: MutableStateFlow<GithubAccount?>

  val selectionFlow: Flow<Pair<GHGitRepositoryMapping, GithubAccount>>

  fun loginToGithub(withOAuth: Boolean = true): GithubAccount?
  fun loginToGhe(): GithubAccount?

  fun trySubmitSelection()
}
