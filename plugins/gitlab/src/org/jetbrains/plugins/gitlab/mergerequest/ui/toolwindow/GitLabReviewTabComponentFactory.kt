// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.toolwindow.ReviewTabsComponentFactory
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.util.URIUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.UIUtil
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabReviewTabViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestDetailsComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsPanelFactory
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

internal class GitLabReviewTabComponentFactory(
  private val project: Project,
  private val toolwindowViewModel: GitLabToolWindowViewModel,
) : ReviewTabsComponentFactory<GitLabReviewTabViewModel, GitLabToolWindowProjectViewModel> {

  override fun createReviewListComponent(
    cs: CoroutineScope,
    projectVm: GitLabToolWindowProjectViewModel
  ): JComponent {
    GitLabStatistics.logMrListOpened(project)
    return GitLabMergeRequestsPanelFactory()
      .create(cs, projectVm.accountVm, projectVm.listVm).also { panel ->
        DataManager.registerDataProvider(panel) { dataId ->
          when {
            GitLabMergeRequestsActionKeys.FILES_CONTROLLER.`is`(dataId) -> projectVm.filesController
            else -> null
          }
        }
      }
  }

  override fun createTabComponent(cs: CoroutineScope,
                                  projectVm: GitLabToolWindowProjectViewModel,
                                  tabVm: GitLabReviewTabViewModel): JComponent {
    return when (tabVm) {
      is GitLabReviewTabViewModel.Details -> {
        GitLabStatistics.logMrDetailsOpened(project)
        createReviewDetailsComponent(cs, projectVm, tabVm.detailsVm).also {
          tabVm.detailsVm.apply {
            requestLoad()
            refreshData()
          }
        }
      }
    }
  }

  override fun createEmptyTabContent(cs: CoroutineScope): JComponent {
    GitLabStatistics.logMrTwLoginOpened(project)
    return createSelectorsComponent(cs)
  }

  private fun createReviewDetailsComponent(
    cs: CoroutineScope,
    projectVm: GitLabToolWindowProjectViewModel,
    reviewDetailsVm: GitLabMergeRequestDetailsLoadingViewModel
  ): JComponent {
    val avatarIconsProvider = projectVm.avatarIconProvider
    return GitLabMergeRequestDetailsComponentFactory.createDetailsComponent(
      project, cs, reviewDetailsVm, projectVm.accountVm, avatarIconsProvider
    ).also {
      DataManager.registerDataProvider(it) { dataId ->
        when {
          GitLabMergeRequestsActionKeys.FILES_CONTROLLER.`is`(dataId) -> projectVm.filesController
          else -> null
        }
      }
    }
  }

  private fun createSelectorsComponent(cs: CoroutineScope): JComponent {
    val selectorVm = toolwindowViewModel.selectorVm

    val accountsDetailsProvider = GitLabAccountsDetailsProvider(cs) { account ->
      // TODO: separate loader
      service<GitLabAccountManager>().findCredentials(account)?.let { token ->
        service<GitLabApiManager>().getClient(account.server, token)
      }
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
            GitLabLoginUtil.isAccountUnique(req.accounts, server, name)
          } ?: return@collect
          req.login(newAccount, token)
        }
        else {
          val token = GitLabLoginUtil.updateToken(project, selectors, account) { server, name ->
            GitLabLoginUtil.isAccountUnique(req.accounts, server, name)
          } ?: return@collect
          req.login(account, token)
        }
      }
    }

    return JPanel(BorderLayout()).apply {
      background = UIUtil.getListBackground()
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

        bindDisabledIn(scope, vm.busyState)
        bindVisibilityIn(scope, vm.tokenLoginAvailableState)
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
