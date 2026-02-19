// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabToolWindowConnectedProjectViewModel

@ApiStatus.Internal
interface GitLabConnectedProjectViewModelFactory {
  fun create(
    project: Project,
    cs: CoroutineScope,
    accountManager: GitLabAccountManager,
    projectsManager: GitLabProjectsManager,
    connection: GitLabProjectConnection,
    activateProject: () -> Unit
  ): GitLabConnectedProjectViewModel
}

internal class GitLabConnectedProjectViewModelFactoryImpl : GitLabConnectedProjectViewModelFactory {
  override fun create(
    project: Project,
    cs: CoroutineScope,
    accountManager: GitLabAccountManager,
    projectsManager: GitLabProjectsManager,
    connection: GitLabProjectConnection,
    activateProject: () -> Unit
  ): GitLabConnectedProjectViewModel {
    return GitLabToolWindowConnectedProjectViewModel(cs, project, accountManager, projectsManager, connection, activateProject)
  }
}