// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.util.bindDisabled
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.AuthorizationType
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.ui.GHAccountsDetailsProvider
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent

class GHRepositoryAndAccountSelectorComponentFactory internal constructor(private val project: Project,
                                                                          private val vm: GHRepositoryAndAccountSelectorViewModel,
                                                                          private val accountManager: GHAccountManager) {

  fun create(scope: CoroutineScope): JComponent {
    val accountDetailsProvider = GHAccountsDetailsProvider(scope, accountManager)

    return RepositoryAndAccountSelectorComponentFactory(vm)
      .create(scope = scope,
              repoNamer = { mapping ->
                val allRepositories = vm.repositoriesState.value.map { it.repository }
                GHUIUtil.getRepositoryDisplayName(allRepositories, mapping.repository, true)
              },
              detailsProvider = accountDetailsProvider,
              accountsPopupActionsSupplier = { createPopupLoginActions(it) },
              credsMissingText = GithubBundle.message("account.token.missing"),
              submitActionText = GithubBundle.message("pull.request.view.list"),
              loginButtons = createLoginButtons(scope))
  }

  private fun createLoginButtons(scope: CoroutineScope): List<JButton> {
    return listOf(
      JButton(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")).apply {
        isDefault = true
        isOpaque = false

        addActionListener {
          if (loginToGithub(false, AuthorizationType.OAUTH)) {
            vm.submitSelection()
          }
        }

        bindVisibility(scope, vm.githubLoginAvailableState)
        bindDisabled(scope, vm.busyState)
      },

      ActionLink(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
        if (loginToGithub(false, AuthorizationType.TOKEN)) {
          vm.submitSelection()
        }
      }.apply {

        autoHideOnDisable = false
        bindVisibility(scope, vm.githubLoginAvailableState)
        bindDisabled(scope, vm.busyState)
      },
      JButton(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")).apply {
        isDefault = true
        isOpaque = false

        addActionListener {
          val repo = vm.repoSelectionState.value ?: return@addActionListener
          if (loginToGhe(false, repo)) {
            vm.submitSelection()
          }
        }

        bindVisibility(scope, vm.gheLoginAvailableState)
        bindDisabled(scope, vm.busyState)
      }
    )
  }

  private fun createPopupLoginActions(repo: GHGitRepositoryMapping?): List<AbstractAction> {
    val isDotComServer = repo?.repository?.serverPath?.isGithubDotCom ?: false
    return if (isDotComServer)
      listOf(object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGithub(true, AuthorizationType.OAUTH)
        }
      }, object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGithub(true, AuthorizationType.TOKEN)
        }
      })
    else listOf(
      object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGhe(true, repo!!)
        }
      })
  }

  private fun loginToGithub(forceNew: Boolean, authType: AuthorizationType): Boolean {
    val account = vm.accountSelectionState.value
    if (account == null || forceNew) {
      return GHAccountsUtil.requestNewAccount(GithubServerPath.DEFAULT_SERVER,
                                              null,
                                              project,
                                              authType = authType
      )?.account?.also {
        vm.accountSelectionState.value = it
      } != null
    }
    else if (vm.missingCredentialsState.value == true) {
      return GHAccountsUtil.requestReLogin(account, project, authType = authType) != null
    }
    return false
  }

  private fun loginToGhe(forceNew: Boolean, repo: GHGitRepositoryMapping): Boolean {
    val server = repo.repository.serverPath
    val account = vm.accountSelectionState.value
    if (account == null || forceNew) {
      return GHAccountsUtil.requestNewAccount(server, login = null, project = project)?.also {
        vm.accountSelectionState.value = it.account
      } != null
    }
    else if (vm.missingCredentialsState.value == true) {
      return GHAccountsUtil.requestReLogin(account, project, authType = AuthorizationType.TOKEN) != null
    }
    return false
  }
}

