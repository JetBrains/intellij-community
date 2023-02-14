// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsPanelActionsController
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import javax.swing.JComponent

internal class GitLabAccountsPanelActionsController(private val project: Project, private val model: GitLabAccountsListModel)
  : AccountsPanelActionsController<GitLabAccount> {
  override val isAddActionWithPopup: Boolean = false

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    // ignoring the point since we know there will be a simple dialog for now
    val (account, token) = GitLabLoginUtil.logInViaToken(project, parentComponent, uniqueAccountPredicate = ::isAccountUnique) ?: return
    model.add(account, token)
  }

  override fun editAccount(parentComponent: JComponent, account: GitLabAccount) {
    val token = GitLabLoginUtil.updateToken(project, parentComponent, account, ::isAccountUnique) ?: return
    model.update(account, token)
  }

  private fun isAccountUnique(serverPath: GitLabServerPath, username: String) =
    model.accounts.none { it.server == serverPath || it.name == username }
}