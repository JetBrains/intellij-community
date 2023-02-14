// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewToolwindowProjectContext
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesControllerImpl

class GitLabToolwindowProjectContext(
  project: Project,
  parentCs: CoroutineScope,
  val connection: GitLabProjectConnection
) : ReviewToolwindowProjectContext {

  private val cs = parentCs.childScope(Dispatchers.Main)

  override val projectName: @Nls String = connection.repo.repository.projectPath.name

  val filesController: GitLabMergeRequestsFilesController =
    GitLabMergeRequestsFilesControllerImpl(project, connection)

  val avatarIconProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(
    AsyncImageIconsProvider(cs, connection.imageLoader)
  )

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      connection.awaitClose()
      filesController.closeAllFiles()
    }
  }
}