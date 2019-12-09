// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.DialogManager
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper
import javax.swing.Icon

/**
 * If it is not possible to automatically determine suitable account, [GithubChooseAccountDialog] dialog will be shown.
 */
abstract class AbstractAuthenticatingGithubUrlGroupingAction(text: String?, description: String?, icon: Icon?)
  : AbstractGithubUrlGroupingAction(text, description, icon) {

  override fun actionPerformed(e: AnActionEvent, project: Project, repository: GitRepository, remote: GitRemote, remoteUrl: String) {
    if (!service<GithubAccountsMigrationHelper>().migrate(project)) return
    val account = getAccount(project, remoteUrl) ?: return
    actionPerformed(e, project, repository, remote, remoteUrl, account)
  }

  private fun getAccount(project: Project, remoteUrl: String): GithubAccount? {
    val authenticationManager = service<GithubAuthenticationManager>()
    val accounts = authenticationManager.getAccounts().filter { it.server.matches(remoteUrl) }
    //only possible when remote is on github.com
    if (accounts.isEmpty()) {
      if (!GithubServerPath.DEFAULT_SERVER.matches(remoteUrl))
        throw IllegalArgumentException("Remote $remoteUrl does not match ${GithubServerPath.DEFAULT_SERVER}")
      return authenticationManager.requestNewAccountForServer(GithubServerPath.DEFAULT_SERVER, project)
    }

    return accounts.singleOrNull()
           ?: accounts.find { it == authenticationManager.getDefaultAccount(project) }
           ?: chooseAccount(project, authenticationManager, remoteUrl, accounts)
  }

  private fun chooseAccount(project: Project, authenticationManager: GithubAuthenticationManager,
                            remoteUrl: String, accounts: List<GithubAccount>): GithubAccount? {
    val dialog = GithubChooseAccountDialog(project,
                                           null,
                                           accounts,
                                           "Choose GitHub account for: $remoteUrl",
                                           false,
                                           true)
    DialogManager.show(dialog)
    if (!dialog.isOK) return null
    val account = dialog.account
    if (dialog.setDefault) authenticationManager.setDefaultAccount(project, account)
    return account
  }

  protected abstract fun actionPerformed(e: AnActionEvent,
                                         project: Project,
                                         repository: GitRepository,
                                         remote: GitRemote,
                                         remoteUrl: String,
                                         account: GithubAccount)
}