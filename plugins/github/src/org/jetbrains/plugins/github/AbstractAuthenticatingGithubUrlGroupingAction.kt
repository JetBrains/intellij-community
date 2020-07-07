// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.DialogManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper
import java.util.function.Supplier
import javax.swing.Icon

/**
 * If it is not possible to automatically determine suitable account, [GithubChooseAccountDialog] dialog will be shown.
 */
abstract class AbstractAuthenticatingGithubUrlGroupingAction(dynamicText: Supplier<String?>,
                                                             dynamicDescription: Supplier<String?>,
                                                             icon: Icon?)
  : AbstractGithubUrlGroupingAction(dynamicText, dynamicDescription, icon) {

  override fun actionPerformed(e: AnActionEvent, project: Project, repository: GHGitRepositoryMapping) {
    if (!service<GithubAccountsMigrationHelper>().migrate(project)) return
    val account = getAccount(project, repository) ?: return
    actionPerformed(e, project, repository, account)
  }

  private fun getAccount(project: Project, repository: GHGitRepositoryMapping): GithubAccount? {
    val authenticationManager = service<GithubAuthenticationManager>()
    val accounts = authenticationManager.getAccounts().filter { it.server == repository.repository.serverPath }
    if (accounts.isEmpty()) {
      return authenticationManager.requestNewAccountForServer(repository.repository.serverPath, project)
    }

    return accounts.singleOrNull()
           ?: accounts.find { it == authenticationManager.getDefaultAccount(project) }
           ?: chooseAccount(project, authenticationManager, repository.repository.serverPath, accounts)
  }

  private fun chooseAccount(project: Project, authenticationManager: GithubAuthenticationManager,
                            server: GithubServerPath, accounts: List<GithubAccount>): GithubAccount? {
    val dialog = GithubChooseAccountDialog(project,
                                           null,
                                           accounts,
                                           GithubBundle.message("account.choose.for", server),
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
                                         repository: GHGitRepositoryMapping,
                                         account: GithubAccount)
}