// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.DialogManager
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubUrlUtil
import javax.swing.Icon

/**
 * Visible and enabled if there's at least one possible github remote url ([GithubGitHelper]).
 * If there's only one url - it will be used for action, otherwise child actions will be created for each url.
 *
 * If it is not possible to automatically determine suitable account, [GithubChooseAccountDialog] dialog will be shown.
 */
abstract class AbstractGithubUrlGroupingAction(text: String?, description: String?, icon: Icon?)
  : ActionGroup(text, description, icon), DumbAware {

  final override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  protected open fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val project = e.getData(CommonDataKeys.PROJECT)
    if (project == null || project.isDefault) return false

    return service<GithubGitHelper>().havePossibleRemotes(project)
  }

  final override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.getData(CommonDataKeys.PROJECT) ?: return AnAction.EMPTY_ARRAY

    val coordinates = service<GithubGitHelper>().getPossibleRemoteUrlCoordinates(project)

    return if (coordinates.size > 1) {
      coordinates.map {
        object : DumbAwareAction(GithubUrlUtil.removeProtocolPrefix(it.url)) {
          override fun actionPerformed(e: AnActionEvent) {
            actionPerformed(e, project, it.repository, it.remote, it.url)
          }
        }
      }.toTypedArray()
    }
    else AnAction.EMPTY_ARRAY
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return

    val coordinates = service<GithubGitHelper>().getPossibleRemoteUrlCoordinates(project)
    coordinates.singleOrNull()?.let { actionPerformed(e, project, it.repository, it.remote, it.url) }
  }

  final override fun canBePerformed(context: DataContext): Boolean {
    val project = context.getData(CommonDataKeys.PROJECT) ?: return false

    val coordinates = service<GithubGitHelper>().getPossibleRemoteUrlCoordinates(project)
    return coordinates.size == 1
  }

  final override fun isPopup(): Boolean = true
  final override fun disableIfNoVisibleChildren(): Boolean = false

  private fun actionPerformed(e: AnActionEvent, project: Project, repository: GitRepository, remote: GitRemote, remoteUrl: String) {
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