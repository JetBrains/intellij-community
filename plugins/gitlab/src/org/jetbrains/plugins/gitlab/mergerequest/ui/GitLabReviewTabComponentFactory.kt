// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindDisabled
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabMergeRequestsListLoader
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestDetailsComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersHistoryModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersViewModelImpl
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsPersistentFiltersHistory
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsPanelFactory
import org.jetbrains.plugins.gitlab.providers.GitLabImageLoader
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

internal class GitLabReviewTabComponentFactory(private val project: Project) {
  private val projectsManager = project.service<GitLabProjectsManager>()
  private val accountManager = service<GitLabAccountManager>()
  private val connectionManager = project.service<GitLabProjectConnectionManager>()

  fun createComponent(
    cs: CoroutineScope,
    connection: GitLabProjectConnection,
    reviewTab: GitLabReviewTab
  ): JComponent {
    return when (reviewTab) {
      GitLabReviewTab.ReviewList -> createReviewListComponent(cs, connection)
      is GitLabReviewTab.ReviewSelected -> createReviewDetailsComponent(cs, connection, reviewTab)
    }
  }

  private fun createReviewDetailsComponent(
    cs: CoroutineScope,
    connection: GitLabProjectConnection,
    reviewTab: GitLabReviewTab.ReviewSelected
  ): JComponent {
    val reviewDetailsVm = GitLabMergeRequestDetailsLoadingViewModelImpl(cs,
                                                                        connection.currentUser,
                                                                        connection.apiClient,
                                                                        connection.projectData,
                                                                        reviewTab.reviewId).apply {
      requestLoad()
    }

    return GitLabMergeRequestDetailsComponentFactory.createDetailsComponent(project, cs, connection, reviewDetailsVm)
  }

  private fun createReviewListComponent(cs: CoroutineScope, connection: GitLabProjectConnection): JComponent {
    val avatarIconsProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(
      AsyncImageIconsProvider(cs, GitLabImageLoader(connection.apiClient, connection.repo.repository.serverPath))
    )

    val filterVm: GitLabMergeRequestsFiltersViewModel = GitLabMergeRequestsFiltersViewModelImpl(
      cs,
      currentUser = connection.currentUser,
      historyModel = GitLabMergeRequestsFiltersHistoryModel(GitLabMergeRequestsPersistentFiltersHistory()),
      avatarIconsProvider = avatarIconsProvider,
      projectData = connection.projectData
    )

    val listVm: GitLabMergeRequestsListViewModel = GitLabMergeRequestsListViewModelImpl(
      cs,
      filterVm = filterVm,
      repository = connection.repo.repository.projectPath.name,
      account = connection.account,
      avatarIconsProvider = avatarIconsProvider,
      accountManager = accountManager,
      tokenRefreshFlow = connection.tokenRefreshFlow,
      loaderSupplier = { filtersValue ->
        GitLabMergeRequestsListLoader(connection.apiClient, connection.repo.repository, filtersValue.toSearchQuery())
      }
    )

    return GitLabMergeRequestsPanelFactory().create(project, cs, listVm)
  }

  fun createEmptyContent(cs: CoroutineScope): JComponent {
    return createSelectorsComponent(cs)
  }

  private fun createSelectorsComponent(cs: CoroutineScope): JComponent {
    // TODO: move vm creation to another place
    val selectorVm = GitLabRepositoryAndAccountSelectorViewModel(cs, projectsManager, accountManager, onSelected = { mapping, account ->
      withContext(cs.coroutineContext) {
        connectionManager.openConnection(mapping, account)
      }
    })

    val accountsDetailsProvider = GitLabAccountsDetailsProvider(cs) {
      // TODO: separate loader
      service<GitLabAccountManager>().findCredentials(it)?.let(service<GitLabApiManager>()::getClient)
    }

    val selectors = RepositoryAndAccountSelectorComponentFactory(selectorVm).create(
      scope = cs,
      repoNamer = { mapping ->
        val allProjects = selectorVm.repositoriesState.value.map { it.repository }
        getProjectDisplayName(allProjects, mapping.repository)
      },
      detailsProvider = accountsDetailsProvider,
      accountsPopupActionsSupplier = { createPopupLoginActions(selectorVm, it) },
      submitActionText = GitLabBundle.message("view.merge.requests.button"),
      loginButtons = createLoginButtons(cs, selectorVm),
      errorPresenter = GitLabSelectorErrorStatusPresenter(project, cs, selectorVm.accountManager) {
        selectorVm.submitSelection()
      }
    )

    cs.launch {
      selectorVm.loginRequestsFlow.collect { req ->
        val account = req.account
        if (account == null) {
          val (newAccount, token) = GitLabLoginUtil.logInViaToken(project, selectors, req.repo.repository.serverPath) { server, name ->
            req.accounts.none { it.server == server || it.name == name }
          } ?: return@collect
          req.login(newAccount, token)
        }
        else {
          val token = GitLabLoginUtil.updateToken(project, selectors, account) { server, name ->
            req.accounts.none { it.server == server || it.name == name }
          } ?: return@collect
          req.login(account, token)
        }
      }
    }

    return JPanel(BorderLayout()).apply {
      add(selectors, BorderLayout.NORTH)
    }
  }

  private fun createLoginButtons(scope: CoroutineScope, vm: GitLabRepositoryAndAccountSelectorViewModel)
    : List<JButton> {
    return listOf(
      JButton(CollaborationToolsBundle.message("login.button")).apply {
        isDefault = true
        isOpaque = false

        addActionListener {
          vm.requestTokenLogin(false, true)
        }

        bindDisabled(scope, vm.busyState)
        bindVisibility(scope, vm.tokenLoginAvailableState)
      }
    )
  }

  private fun createPopupLoginActions(vm: GitLabRepositoryAndAccountSelectorViewModel, mapping: GitLabProjectMapping?): List<Action> {
    if (mapping == null) return emptyList()
    return listOf(object : AbstractAction(CollaborationToolsBundle.message("login.button")) {
      override fun actionPerformed(e: ActionEvent?) {
        vm.requestTokenLogin(true, false)
      }
    })
  }

  private fun getProjectDisplayName(allProjects: List<GitLabProjectCoordinates>, project: GitLabProjectCoordinates): @NlsSafe String {
    val showServer = needToShowServer(allProjects)
    val builder = StringBuilder()
    if (showServer) builder.append(URIUtil.toStringWithoutScheme(project.serverPath.toURI())).append("/")
    builder.append(project.projectPath.owner).append("/")
    builder.append(project.projectPath.name)
    return builder.toString()
  }

  private fun needToShowServer(projects: List<GitLabProjectCoordinates>): Boolean {
    if (projects.size <= 1) return false
    val firstServer = projects.first().serverPath
    return projects.any { it.serverPath != firstServer }
  }
}
