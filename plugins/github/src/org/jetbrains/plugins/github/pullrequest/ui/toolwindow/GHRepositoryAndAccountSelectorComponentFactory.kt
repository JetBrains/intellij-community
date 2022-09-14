// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.ui.GHAccountsDetailsLoader
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent

class GHRepositoryAndAccountSelectorComponentFactory internal constructor(private val project: Project,
                                                                          private val vm: GHRepositoryAndAccountSelectorViewModel,
                                                                          private val authManager: GithubAuthenticationManager) {

  fun create(scope: CoroutineScope): JComponent {
    val indicatorsProvider = ProgressIndicatorsProvider().also {
      Disposer.register(scope.nestedDisposable(), it)
    }
    val accountDetailsLoader = GHAccountsDetailsLoader(indicatorsProvider) {
      GithubApiRequestExecutorManager.getInstance().getExecutor(it)
    }

    return RepositoryAndAccountSelectorComponentFactory(vm)
      .create(scope = scope,
              repoNamer = { mapping ->
                val allRepositories = vm.repositoriesState.value.map { it.repository }
                GHUIUtil.getRepositoryDisplayName(allRepositories, mapping.repository, true)
              },
              detailsLoader = accountDetailsLoader,
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
          if (loginToGithub(true)) {
            vm.submitSelection()
          }
        }

        bindVisibility(scope, vm.githubLoginAvailableState)
      },

      ActionLink(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
        if (loginToGithub(false)) {
          vm.submitSelection()
        }
      }.apply {
        bindVisibility(scope, vm.githubLoginAvailableState)
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
      }
    )
  }

  private fun createPopupLoginActions(repo: GHGitRepositoryMapping?): List<AbstractAction> {
    val isDotComServer = repo?.repository?.serverPath?.isGithubDotCom ?: false
    return if (isDotComServer)
      listOf(object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGithub(true)
        }
      }, object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGithub(true, false)
        }
      })
    else listOf(
      object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGhe(true, repo!!)
        }
      })
  }

  private fun loginToGithub(forceNew: Boolean, withOAuth: Boolean = true): Boolean {
    val account = vm.accountSelectionState.value
    if (account == null || forceNew) {
      return authManager.requestNewAccountForDefaultServer(project, !withOAuth)?.also {
        vm.accountSelectionState.value = it
      } != null
    }
    else if (vm.missingCredentialsState.value) {
      return authManager.requestReLogin(account, project)
    }
    return false
  }

  private fun loginToGhe(forceNew: Boolean, repo: GHGitRepositoryMapping): Boolean {
    val server = repo.repository.serverPath
    val account = vm.accountSelectionState.value
    if (account == null || forceNew) {
      return authManager.requestNewAccountForServer(server, project)?.also {
        vm.accountSelectionState.value = it
      } != null
    }
    else if (vm.missingCredentialsState.value) {
      return authManager.requestReLogin(account, project)
    }
    return false
  }
}

