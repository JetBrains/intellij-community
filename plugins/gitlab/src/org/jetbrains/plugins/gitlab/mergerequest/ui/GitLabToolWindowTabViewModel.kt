// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.mapStateScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowTabViewModel.NestedViewModel.MergeRequests
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowTabViewModel.NestedViewModel.Selectors

internal class GitLabToolWindowTabViewModel(scope: CoroutineScope,
                                            private val connectionManager: GitLabProjectConnectionManager,
                                            private val projectsManager: GitLabProjectsManager,
                                            private val accountManager: GitLabAccountManager) {

  val nestedViewModelState: StateFlow<NestedViewModel> = connectionManager.state.mapStateScoped(scope) { scope, connection ->
    if (connection != null) {
      MergeRequests(connection)
    }
    else {
      Selectors(GitLabRepositoryAndAccountSelectorViewModel(scope, connectionManager, projectsManager, accountManager))
    }
  }

  internal sealed interface NestedViewModel {
    class Selectors(val selectorVm: GitLabRepositoryAndAccountSelectorViewModel) : NestedViewModel

    class MergeRequests(val connection: GitLabProjectConnection) : NestedViewModel
  }
}