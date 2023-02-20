// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.toolwindow.ReviewListTabComponentDescriptor
import com.intellij.collaboration.ui.toolwindow.ReviewTabsComponentFactory
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.util.URIUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.UIUtil
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffBridgeRepository
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestDetailsComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModelImpl
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

internal class GitLabReviewTabComponentFactory(
  private val project: Project,
  private val toolwindowViewModel: GitLabToolwindowViewModel,
) : ReviewTabsComponentFactory<GitLabReviewTab, GitLabToolwindowProjectContext> {
  override fun createReviewListComponentDescriptor(
    cs: CoroutineScope,
    projectContext: GitLabToolwindowProjectContext
  ): ReviewListTabComponentDescriptor {
    return GitLabReviewListTabComponentDescriptor(project, cs, toolwindowViewModel.accountManager, projectContext)
  }

  override fun createTabComponent(cs: CoroutineScope,
                                  projectContext: GitLabToolwindowProjectContext,
                                  reviewTabType: GitLabReviewTab): JComponent {
    return when (reviewTabType) {
      is GitLabReviewTab.ReviewSelected -> {
        createReviewDetailsComponent(cs, projectContext, reviewTabType.reviewId)
      }
    }
  }

  override fun createEmptyTabContent(cs: CoroutineScope): JComponent {
    return createSelectorsComponent(cs)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun createReviewDetailsComponent(
    cs: CoroutineScope,
    projectContext: GitLabToolwindowProjectContext,
    reviewId: GitLabMergeRequestId
  ): JComponent {
    val conn = projectContext.connection
    val reviewDetailsVm = GitLabMergeRequestDetailsLoadingViewModelImpl(project, cs, conn.currentUser, conn.projectData, reviewId).apply {
      requestLoad()
    }

    val detailsVmFlow = reviewDetailsVm.mergeRequestLoadingFlow.mapLatest {
      (it as? GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Result)?.detailsVm
    }.filterNotNull()

    cs.launch(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
      detailsVmFlow.flatMapLatest {
        it.detailsInfoVm.showTimelineRequests
      }.collect {
        projectContext.filesController.openTimeline(reviewId, true)
      }
    }

    cs.launch(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
      val diffBridge = project.service<GitLabMergeRequestDiffBridgeRepository>().get(conn, reviewId)
      detailsVmFlow.flatMapLatest {
        it.changesVm.userChangesSelection
      }.collectLatest {
        diffBridge.setChanges(it)
      }
    }

    cs.launch(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
      detailsVmFlow.flatMapLatest {
        it.changesVm.showDiffRequests
      }.collect {
        projectContext.filesController.openDiff(reviewId, true)
      }
    }


    val avatarIconsProvider = projectContext.avatarIconProvider
    return GitLabMergeRequestDetailsComponentFactory.createDetailsComponent(project, cs, reviewDetailsVm, avatarIconsProvider).also {
      DataManager.registerDataProvider(it) { dataId ->
        when {
          GitLabMergeRequestsActionKeys.FILES_CONTROLLER.`is`(dataId) -> projectContext.filesController
          else -> null
        }
      }
    }
  }

  private fun createSelectorsComponent(cs: CoroutineScope): JComponent {
    // TODO: move vm creation to another place
    val selectorVm = GitLabRepositoryAndAccountSelectorViewModel(
      cs, toolwindowViewModel.projectsManager, toolwindowViewModel.accountManager,
      onSelected = { mapping, account ->
        withContext(cs.coroutineContext) {
          toolwindowViewModel.connectionManager.openConnection(mapping, account)
        }
      }
    )

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
