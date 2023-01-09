// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.util.bindDisabled
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.content.Content
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabToolWindowTabViewModel.NestedViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsPanelFactory
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

internal class GitLabToolWindowTabController(private val project: Project,
                                             scope: CoroutineScope,
                                             tabVm: GitLabToolWindowTabViewModel,
                                             private val content: Content) {

  init {
    scope.launch {
      tabVm.nestedViewModelState.collectScoped { scope, vm ->
        content.displayName = GitLabBundle.message("title.merge.requests")

        val component = when (vm) {
          is NestedViewModel.Selectors -> createSelectorsComponent(scope, vm)
          is NestedViewModel.MergeRequests -> createMergeRequestsComponent(project, scope, vm)
        }

        CollaborationToolsUIUtil.setComponentPreservingFocus(content, component)
      }
    }
  }

  private fun createSelectorsComponent(scope: CoroutineScope, vm: NestedViewModel.Selectors): JComponent {
    val accountsDetailsProvider = GitLabAccountsDetailsProvider(scope) {
      // TODO: separate loader
      service<GitLabAccountManager>().findCredentials(it)?.let(service<GitLabApiManager>()::getClient)
    }

    val selectorVm = vm.selectorVm
    val selectors = RepositoryAndAccountSelectorComponentFactory(selectorVm).create(
      scope = scope,
      repoNamer = { mapping ->
        val allProjects = vm.selectorVm.repositoriesState.value.map { it.repository }
        getProjectDisplayName(allProjects, mapping.repository)
      },
      detailsProvider = accountsDetailsProvider,
      accountsPopupActionsSupplier = { createPopupLoginActions(selectorVm, it) },
      submitActionText = GitLabBundle.message("view.merge.requests.button"),
      loginButtons = createLoginButtons(scope, selectorVm),
      errorPresenter = GitLabSelectorErrorStatusPresenter(project, scope, selectorVm.accountManager) {
        selectorVm.submitSelection()
      }
    )

    scope.launch {
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

  private fun createMergeRequestsComponent(project: Project, scope: CoroutineScope, vm: NestedViewModel.MergeRequests): JComponent =
    GitLabMergeRequestsPanelFactory().create(project, scope, vm.listVm)

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