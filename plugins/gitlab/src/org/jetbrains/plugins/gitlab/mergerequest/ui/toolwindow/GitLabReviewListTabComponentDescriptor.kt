// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.toolwindow.ReviewListTabComponentDescriptor
import com.intellij.ide.DataManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabLazyProject
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
  parentCs: CoroutineScope,
  accountManager: GitLabAccountManager,
  ctx: GitLabProjectUIContext
) : ReviewListTabComponentDescriptor {
  private val cs = parentCs.childScope(Dispatchers.Default)

  private val avatarIconsProvider: IconsProvider<GitLabUserDTO> = ctx.avatarIconProvider
  private val projectData: GitLabLazyProject = ctx.projectData

  private val accountVm = GitLabAccountViewModelImpl(project, cs, ctx.account, accountManager)
  private val filterVm: GitLabMergeRequestsFiltersViewModel = GitLabMergeRequestsFiltersViewModelImpl(
    cs,
    currentUser = ctx.currentUser,
    historyModel = GitLabMergeRequestsFiltersHistoryModel(project.service<GitLabMergeRequestsPersistentFiltersHistory>()),
    avatarIconsProvider = avatarIconsProvider,
    projectData = projectData
  )

  override val viewModel: GitLabMergeRequestsListViewModel = GitLabMergeRequestsListViewModelImpl(
    cs,
    filterVm = filterVm,
    repository = ctx.projectName,
    avatarIconsProvider = avatarIconsProvider,
    tokenRefreshFlow = ctx.tokenRefreshFlow,
    loaderSupplier = { filtersValue -> projectData.mergeRequests.getListLoader(filtersValue.toSearchQuery()) }
  )

  override val component: JComponent = GitLabMergeRequestsPanelFactory()
    .create(cs.childScope(Dispatchers.Main), accountVm, viewModel).also { panel ->
      DataManager.registerDataProvider(panel) { dataId ->
        when {
          GitLabMergeRequestsActionKeys.FILES_CONTROLLER.`is`(dataId) -> ctx.filesController
          else -> null
        }
      }
    }
}