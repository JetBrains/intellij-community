// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewListTabComponentDescriptor
import com.intellij.ide.DataManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectUIContext
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersHistoryModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsPersistentFiltersHistory
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsPanelFactory
import javax.swing.JComponent

internal class GitLabReviewListTabComponentDescriptor(
  project: Project,
  cs: CoroutineScope,
  accountManager: GitLabAccountManager,
  ctx: GitLabProjectUIContext
) : ReviewListTabComponentDescriptor {

  override val viewModel: GitLabMergeRequestsListViewModel

  override val component: JComponent

  init {
    val avatarIconsProvider: IconsProvider<GitLabUserDTO> = ctx.avatarIconProvider

    val projectData = ctx.projectData
    val filterVm: GitLabMergeRequestsFiltersViewModel = GitLabMergeRequestsFiltersViewModelImpl(
      cs,
      currentUser = ctx.currentUser,
      historyModel = GitLabMergeRequestsFiltersHistoryModel(project.service<GitLabMergeRequestsPersistentFiltersHistory>()),
      avatarIconsProvider = avatarIconsProvider,
      projectData = projectData
    )

    viewModel = GitLabMergeRequestsListViewModelImpl(
      cs,
      filterVm = filterVm,
      repository = ctx.projectName,
      account = ctx.account,
      avatarIconsProvider = avatarIconsProvider,
      accountManager = accountManager,
      tokenRefreshFlow = ctx.tokenRefreshFlow,
      loaderSupplier = { filtersValue -> projectData.mergeRequests.getListLoader(filtersValue.toSearchQuery()) }
    )

    component = GitLabMergeRequestsPanelFactory().create(project, cs, viewModel).also {
      DataManager.registerDataProvider(it) { dataId ->
        when {
          GitLabMergeRequestsActionKeys.FILES_CONTROLLER.`is`(dataId) -> ctx.filesController
          else -> null
        }
      }
    }
  }
}