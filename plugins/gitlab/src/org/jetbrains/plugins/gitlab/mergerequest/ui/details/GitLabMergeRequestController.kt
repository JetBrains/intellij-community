// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabProjectDetailsLoader
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabFilesController
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel.LoadingState
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsPanelFactory
import org.jetbrains.plugins.gitlab.providers.GitLabImageLoader

internal class GitLabMergeRequestController(
  project: Project,
  private val scope: CoroutineScope,
  private val connection: GitLabProjectConnection,
  private val listVm: GitLabMergeRequestsListViewModel,
  private val wrapper: Wrapper
) {
  private val filesController = GitLabFilesController(project, connection.repo.repository)

  private val mergeRequestsList by lazy {
    GitLabMergeRequestsPanelFactory().create(project, scope, listVm).also { panel ->
      DataManager.registerDataProvider(panel) { key ->
        when {
          GitLabMergeRequestsActionKeys.MERGE_REQUEST_CONTROLLER.`is`(key) -> this
          GitLabMergeRequestsActionKeys.FILES_CONTROLLER.`is`(key) -> filesController
          else -> null
        }
      }
    }
  }

  init {
    wrapper.setContent(mergeRequestsList)
    wrapper.repaint()

    scope.launch {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          invokeAndWaitIfNeeded(ModalityState.defaultModalityState()) {
            filesController.closeAllFiles()
          }
        }
      }
    }
  }

  fun openMergeRequest(mergeRequestId: GitLabMergeRequestId) {
    val reviewDetailsVm = GitLabMergeRequestDetailsLoadingViewModelImpl(scope, connection, mergeRequestId).apply {
      requestLoad()
    }

    wrapper.bindContent(scope, reviewDetailsVm.mergeRequestLoadingFlow.map { loadingState ->
      when (loadingState) {
        LoadingState.Loading -> LoadingLabel()
        is LoadingState.Error -> SimpleHtmlPane(loadingState.exception.localizedMessage)
        is LoadingState.Result -> {
          val projectDetailsLoader = GitLabProjectDetailsLoader(connection)
          val avatarIconsProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(
            AsyncImageIconsProvider(scope, GitLabImageLoader(connection.apiClient, connection.repo.repository.serverPath))
          )

          GitLabMergeRequestDetailsComponentFactory.create(
            scope,
            loadingState.detailsVm,
            projectDetailsLoader,
            avatarIconsProvider,
            ::openMergeRequestsList,
            openTimeLineAction = { mergeRequestId, focus -> filesController.openTimeline(mergeRequestId, focus) }
          )
        }
      }
    })
  }

  fun openMergeRequestsList() {
    wrapper.setContent(mergeRequestsList)
    wrapper.repaint()
  }
}