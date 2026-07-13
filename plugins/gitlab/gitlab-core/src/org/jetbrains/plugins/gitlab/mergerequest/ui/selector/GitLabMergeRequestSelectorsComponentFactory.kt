// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.selector

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.util.URIUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.ActionLink
import com.intellij.util.asSafely
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabAccountsDetailsProvider
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.GitLabSelectorErrorStatusPresenter
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.GitLabRepositoryAndAccountSelectorViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.toolwindow.model.LoginRequest
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent

@ApiStatus.Internal
object GitLabMergeRequestSelectorsComponentFactory {
  fun createSelectorsComponent(
    cs: CoroutineScope,
    project: Project,
    selectorVm: GitLabRepositoryAndAccountSelectorViewModel,
  ): JComponent {
    val defaultAccountHolder = project.service<GitLabProjectDefaultAccountHolder>()
    val apiManager = service<GitLabApiManager>()
    val accountsDetailsProvider = GitLabAccountsDetailsProvider(cs, apiManager, selectorVm.accountManager)

    val selectors = RepositoryAndAccountSelectorComponentFactory(selectorVm).create(
      scope = cs,
      defaultAccountHolder = defaultAccountHolder,
      repoNamer = { mapping ->
        val allProjects = selectorVm.repositoriesState.value.map { it.repository }
        getProjectDisplayName(allProjects, mapping.repository)
      },
      detailsProvider = accountsDetailsProvider,
      accountsPopupActionsSupplier = { createPopupLoginActions(selectorVm, it) },
      submitActionText = GitLabBundle.message("view.merge.requests.button"),
      loginButtons = createLoginButtons(cs, selectorVm),
      errorPresenter = GitLabSelectorErrorStatusPresenter(selectorVm.project, cs, selectorVm.accountManager, GitLabLoginSource.MR_TW) {
        selectorVm.submitSelection()
      }
    )

    cs.launch(Dispatchers.EDT) {
      selectorVm.loginRequestsFlow.collect { request ->
        val account = request.account
        if (account == null) {
          val (newAccount, credentials) = request.logIn(selectorVm.project, selectors, selectorVm.accountManager.accountsState.value)
                                            .asSafely<LoginResult.Success>() ?: return@collect
          selectorVm.authenticateAccount(newAccount, credentials, request.submit)
        }
        else {
          val (_, credentials) = request.reLogIn(selectorVm.project, selectors, account, selectorVm.accountManager.accountsState.value)
                                   .asSafely<LoginResult.Success>() ?: return@collect
          selectorVm.authenticateAccount(account, credentials, request.submit)
        }
      }
    }
    return selectors
  }

  private fun LoginRequest.logIn(
    project: Project,
    selectors: JComponent,
    accounts: Set<GitLabAccount>,
  ): LoginResult = when (type) {
    LoginRequest.Type.TOKEN -> GitLabLoginUtil.logInViaToken(
      project,
      selectors,
      repo.repository.serverPath,
      loginSource = GitLabLoginSource.MR_TW) { server, name ->
      GitLabLoginUtil.isAccountUnique(accounts, server, name)
    }
    LoginRequest.Type.OAUTH -> GitLabLoginUtil.logInViaOAuth(
      project,
      repo.repository.serverPath,
      loginSource = GitLabLoginSource.MR_TW) { server, name ->
      GitLabLoginUtil.isAccountUnique(accounts, server, name)
    }
  }

  private fun LoginRequest.reLogIn(
    project: Project,
    selectors: JComponent,
    account: GitLabAccount,
    accounts: Set<GitLabAccount>,
  ): LoginResult = when (type) {
    LoginRequest.Type.OAUTH -> GitLabLoginUtil.reLogInViaOAuth(
      project,
      account,
      loginSource = GitLabLoginSource.MR_TW) { server, name ->
      GitLabLoginUtil.isAccountUnique(accounts, server, name)
    }
    LoginRequest.Type.TOKEN -> GitLabLoginUtil.reLogInViaToken(
      project,
      selectors,
      account,
      loginSource = GitLabLoginSource.MR_TW) { server, name ->
      GitLabLoginUtil.isAccountUnique(accounts, server, name)
    }
  }

  private fun createLoginButtons(cs: CoroutineScope, vm: GitLabRepositoryAndAccountSelectorViewModel)
    : List<JButton> {
    return listOf(
      JButton(GitLabBundle.message("account.add.popup.text")).apply {
        isOpaque = false
        isDefault = true
        addActionListener {
          vm.requestOAuthLogin(false, true)
        }
        bindDisabledIn(cs, vm.busyState)
        bindVisibilityIn(cs, vm.oAuthLoginAvailableState)
      },
      ActionLink(CollaborationToolsBundle.message("accounts.action.add.account.with.token")).apply {
        addActionListener {
          vm.requestTokenLogin(false, true)
        }
      }.apply {
        autoHideOnDisable = false
        bindDisabledIn(cs, vm.busyState)
        bindVisibilityIn(cs, vm.tokenLoginAvailableState)
      }
    )
  }

  private fun createPopupLoginActions(vm: GitLabRepositoryAndAccountSelectorViewModel, mapping: GitLabProjectMapping?): List<Action> {
    if (mapping == null) return emptyList()
    return buildList {
      if (mapping.repository.serverPath == GitLabServerPath.DEFAULT_SERVER) {
        add(object : AbstractAction(GitLabBundle.message("account.add.popup.text")) {
          override fun actionPerformed(e: ActionEvent?) {
            vm.requestOAuthLogin(true, false)
          }
        })
      }
      add(object : AbstractAction(CollaborationToolsBundle.message("accounts.action.add.account.with.token")) {
        override fun actionPerformed(e: ActionEvent?) {
          vm.requestTokenLogin(true, false)
        }
      })
    }
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