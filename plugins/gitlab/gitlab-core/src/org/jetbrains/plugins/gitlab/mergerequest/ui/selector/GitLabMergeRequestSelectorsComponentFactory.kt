// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.selector

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.asSafely
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabSelectorErrorStatusPresenter
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabRepositoryAndAccountSelectorViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

@ApiStatus.Internal
object GitLabMergeRequestSelectorsComponentFactory {
  fun createSelectorsComponent(cs: CoroutineScope, selectorVm: GitLabRepositoryAndAccountSelectorViewModel): JComponent {

    val accountsDetailsProvider = GitLabAccountsDetailsProvider(cs, selectorVm.accountManager) { account ->
      // TODO: separate loader
      selectorVm.accountManager.findCredentials(account)?.let { token ->
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
      errorPresenter = GitLabSelectorErrorStatusPresenter(selectorVm.project, cs, selectorVm.accountManager) {
        selectorVm.submitSelection()
      }
    )

    cs.launch(Dispatchers.EDT) {
      selectorVm.loginRequestsFlow.collect { req ->
        val account = req.account
        if (account == null) {
          val (newAccount, token) = GitLabLoginUtil.logInViaToken(selectorVm.project, selectors, req.repo.repository.serverPath) { server, name ->
            GitLabLoginUtil.isAccountUnique(req.accounts, server, name)
          }.asSafely<LoginResult.Success>() ?: return@collect
          req.login(newAccount, token)
        }
        else {
          val loginResult = GitLabLoginUtil.updateToken(selectorVm.project, selectors, account) { server, name ->
            GitLabLoginUtil.isAccountUnique(req.accounts, server, name)
          }.asSafely<LoginResult.Success>() ?: return@collect
          req.login(account, loginResult.token)
        }
      }
    }

    return selectors
  }

  private fun createLoginButtons(cs: CoroutineScope, vm: GitLabRepositoryAndAccountSelectorViewModel)
    : List<JButton> {
    return listOf(
      JButton(CollaborationToolsBundle.message("login.button")).apply {
        isDefault = true
        isOpaque = false

        addActionListener {
          vm.requestTokenLogin(false, true)
        }

        bindDisabledIn(cs, vm.busyState)
        bindVisibilityIn(cs, vm.tokenLoginAvailableState)
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