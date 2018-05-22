// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import git4idea.DialogManager
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog
import org.jetbrains.plugins.github.util.GithubAccountsMigrationHelper
import org.jetbrains.plugins.github.util.GithubGitHelper
import javax.swing.Icon

abstract class LegacySingleAccountActionGroup(text: String?, description: String?, icon: Icon?) : DumbAwareAction(text, description, icon) {
  override fun update(e: AnActionEvent?) {
    if (e == null) return
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || project.isDefault) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val gitRepository = GithubGitHelper.findGitRepository(project, file)
    if (gitRepository == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (getAccountsForRemotes(project, gitRepository).isEmpty()
        && service<GithubAccountsMigrationHelper>().getOldServer()?.let { getRemote(it, gitRepository) } == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent?) {
    if (e == null) return
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || project.isDefault) return

    val gitRepository = GithubGitHelper.findGitRepository(project, file)
    if (gitRepository == null) return
    gitRepository.update()

    if (!service<GithubAccountsMigrationHelper>().migrate(project)) return
    val accounts = getAccountsForRemotes(project, gitRepository)
    // can happen if migration was cancelled
    if (accounts.isEmpty()) return
    val account = if (accounts.size == 1) accounts.first()
    else {
      val dialog = GithubChooseAccountDialog(project, null, accounts,
                                             "Default account is not configured for this project. Choose Github account:",
                                             true,
                                             !project.isDefault,
                                             "Choose Github Account",
                                             "Choose")
      DialogManager.show(dialog)
      if (!dialog.isOK) return
      val account = dialog.account
      if (dialog.setDefault) service<GithubAuthenticationManager>().setDefaultAccount(project, account)

      account
    }

    actionPerformed(project, file, gitRepository, account)
  }

  abstract fun actionPerformed(project: Project, file: VirtualFile?, gitRepository: GitRepository, account: GithubAccount)

  private fun getAccountsForRemotes(project: Project, repository: GitRepository): List<GithubAccount> {
    val authenticationManager = service<GithubAuthenticationManager>()
    val defaultAccount = authenticationManager.getDefaultAccount(project)
    return if (defaultAccount != null && getRemote(defaultAccount.server, repository) != null)
      listOf(defaultAccount)
    else {
      authenticationManager.getAccounts().filter { getRemote(it.server, repository) != null }
    }
  }

  protected abstract fun getRemote(server: GithubServerPath, repository: GitRepository): Pair<GitRemote, String>?
}