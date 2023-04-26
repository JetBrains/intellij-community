// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectContext
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLazyProject
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridge
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesControllerImpl

class GitLabProjectUIContext
private constructor(cs: CoroutineScope, project: Project, connection: GitLabProjectConnection)
  : ReviewToolwindowProjectContext {

  val connectionId: String = connection.id
  override val projectName: @Nls String = connection.repo.repository.projectPath.name

  val currentUser: GitLabUserDTO = connection.currentUser
  val projectData: GitLabLazyProject = connection.projectData

  private val diffBridgeStore = Caffeine.newBuilder()
    .weakValues()
    .build<GitLabMergeRequestId.Simple, GitLabMergeRequestDiffBridge>()

  val filesController: GitLabMergeRequestsFilesController = GitLabMergeRequestsFilesControllerImpl(project, connection)

  val avatarIconProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(AsyncImageIconsProvider(cs, connection.imageLoader))

  // should not be here
  val account: GitLabAccount = connection.account
  val tokenRefreshFlow: Flow<Unit> = connection.tokenRefreshFlow

  fun getDiffBridge(mr: GitLabMergeRequestId): GitLabMergeRequestDiffBridge =
    diffBridgeStore.get(GitLabMergeRequestId.Simple(mr)) {
      GitLabMergeRequestDiffBridge()
    }

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      finally {
        filesController.closeAllFiles()
      }
    }
  }

  companion object {
    internal fun CoroutineScope.GitLabProjectUIContext(project: Project, connection: GitLabProjectConnection) =
      GitLabProjectUIContext(this, project, connection)
  }
}