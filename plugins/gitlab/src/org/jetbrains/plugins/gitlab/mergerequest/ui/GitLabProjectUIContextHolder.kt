// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapStateScoped
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowViewModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.createSingleProjectAndAccountState
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectUIContext.Companion.GitLabProjectUIContext
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

@Service(Service.Level.PROJECT)
internal class GitLabProjectUIContextHolder(
  private val project: Project,
  parentCs: CoroutineScope
) : ReviewToolwindowViewModel<GitLabProjectUIContext> {
  private val cs = parentCs.childScope(Dispatchers.Main)

  val connectionManager: GitLabProjectConnectionManager = project.service<GitLabProjectConnectionManager>()
  val projectsManager: GitLabProjectsManager = project.service<GitLabProjectsManager>()
  val accountManager: GitLabAccountManager = service<GitLabAccountManager>()

  override val projectContext: StateFlow<GitLabProjectUIContext?> =
    connectionManager.connectionState.mapStateScoped(cs) { connection ->
      connection?.let { GitLabProjectUIContext(project, it) }
    }

  private val singleProjectAndAccountState: StateFlow<Pair<GitLabProjectMapping, GitLabAccount>?> =
    createSingleProjectAndAccountState(cs, projectsManager, accountManager)

  val canSwitchProject: StateFlow<Boolean> =
    combineState(cs, projectContext, singleProjectAndAccountState) { currentProjectContext, currentSingleRepoAndAccountState ->
      // project can be switched when any project is selected and there are no 1-1 mapping with project and account
      currentProjectContext != null && currentSingleRepoAndAccountState == null
    }

  fun switchProject() {
    cs.launch {
      connectionManager.closeConnection()
    }
  }
}