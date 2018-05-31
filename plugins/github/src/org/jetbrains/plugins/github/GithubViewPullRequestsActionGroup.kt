// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.DialogManager
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog
import org.jetbrains.plugins.github.pullrequest.GithubPullRequestsToolWindowManager
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubUrlUtil

class GithubViewPullRequestsActionGroup : ActionGroup("View Pull Requests", null, AllIcons.Vcs.Vendors.Github), DumbAware {
  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    if (project == null || project.isDefault) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = GithubGitHelper
      .findGitRepository(project)
      ?.let(service<GithubGitHelper>()::hasAccessibleRemotes)
      ?: false
  }


  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.getData(CommonDataKeys.PROJECT) ?: return AnAction.EMPTY_ARRAY
    val repository = GithubGitHelper.findGitRepository(project) ?: return AnAction.EMPTY_ARRAY
    val urls = getAccessibleRemoteUrls(repository)
    return if (urls.size > 1) urls.map { GithubViewPullRequestsAction(project, repository, it) }.toTypedArray() else AnAction.EMPTY_ARRAY
  }

  override fun isPopup(): Boolean = true

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val repository = GithubGitHelper.findGitRepository(project) ?: return
    getAccessibleRemoteUrls(repository).firstOrNull()?.let { url -> showPullRequestsTab(project, repository, url) }
  }

  override fun canBePerformed(context: DataContext): Boolean {
    val project = context.getData(CommonDataKeys.PROJECT) ?: return false
    val repository = GithubGitHelper.findGitRepository(project) ?: return false
    return getAccessibleRemoteUrls(repository).size == 1
  }

  private fun getAccessibleRemoteUrls(repository: GitRepository): List<String> {
    return repository.let { service<GithubGitHelper>().getAccessibleRemoteUrls(it) }
  }

  override fun disableIfNoVisibleChildren(): Boolean = false

  companion object {
    private class GithubViewPullRequestsAction(private val project: Project,
                                               private val repository: GitRepository,
                                               private val remoteUrl: String)
      : DumbAwareAction(GithubUrlUtil.removeProtocolPrefix(remoteUrl)) {
      override fun actionPerformed(e: AnActionEvent) {
        showPullRequestsTab(project, repository, remoteUrl)
      }
    }

    private fun showPullRequestsTab(project: Project, repository: GitRepository, remoteUrl: String) {
      val account = getAccount(project, remoteUrl) ?: return
      project.service<GithubPullRequestsToolWindowManager>().showPullRequestsTab(repository, remoteUrl, account)
    }

    private fun getAccount(project: Project, remoteUrl: String): GithubAccount? {
      if (!service<GithubAccountsMigrationHelper>().migrate(project)) return null
      val authenticationManager = service<GithubAuthenticationManager>()
      val accounts = authenticationManager.getAccounts().filter { it.server.matches(remoteUrl) }
      assert(accounts.isNotEmpty())

      return accounts.singleOrNull()
             ?: accounts.find { it == authenticationManager.getDefaultAccount(project) }
             ?: chooseAccount(project, accounts)
    }

    private fun chooseAccount(project: Project, accounts: List<GithubAccount>): GithubAccount? {
      val dialog = GithubChooseAccountDialog(project,
                                             null,
                                             accounts,
                                             null,
                                             false,
                                             true)
      DialogManager.show(dialog)
      if (!dialog.isOK) return null
      val account = dialog.account
      if (dialog.setDefault) service<GithubAuthenticationManager>().setDefaultAccount(project, account)
      return account
    }
  }
}