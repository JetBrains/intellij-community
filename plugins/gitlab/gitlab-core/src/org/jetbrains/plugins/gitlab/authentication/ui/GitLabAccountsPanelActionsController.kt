// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.AccountsPanelActionsController
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil.logInViaOAuth
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil.logInViaOAuthToCustomServer
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginUtil.logInViaToken
import org.jetbrains.plugins.gitlab.authentication.LoginResult
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent

internal class GitLabAccountsPanelActionsController(
  private val project: Project,
  private val model: GitLabAccountsListModel,
) : AccountsPanelActionsController<GitLabAccount> {
  override val isAddActionWithPopup: Boolean = true

  @RequiresEdt
  @Suppress("SplitModeApiUsage")
  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    val group = DefaultActionGroup().apply {
      add(createOAuthLoginAction(project))
      add(createOAuthEnterpriseLoginAction(project, parentComponent))
      add(createTokenLoginAction(project, parentComponent))
    }
    val actualPoint = point ?: RelativePoint.getCenterOf(parentComponent)
    JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, DataManager.getInstance().getDataContext(parentComponent),
                              JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
      .show(actualPoint)
  }

  @RequiresEdt
  override fun editAccount(parentComponent: JComponent, account: GitLabAccount) {
    GitLabLoginUtil.reLogIn(
      project,
      parentComponent,
      account,
      null,
      GitLabLoginSource.SETTINGS,
      ::isAccountUnique,
    ).asSafely<LoginResult.Success>()?.also {
      model.update(it.account, it.credentials)
    }
  }

  private fun createTokenLoginAction(project: Project, parentComponent: JComponent?) =
    DumbAwareAction.create(CollaborationToolsBundle.message("accounts.action.add.account.with.token")) {
      logInViaToken(
        project,
        parentComponent,
        loginSource = GitLabLoginSource.SETTINGS,
        uniqueAccountPredicate = ::isAccountUnique
      ).asSafely<LoginResult.Success>()?.also {
        model.add(it.account, it.credentials)
      }
    }

  private fun createOAuthLoginAction(project: Project) =
    DumbAwareAction.create(GitLabBundle.message("account.add.popup.text")) {
      logInViaOAuth(
        project,
        loginSource = GitLabLoginSource.SETTINGS,
        uniqueAccountPredicate = ::isAccountUnique
      ).asSafely<LoginResult.Success>()?.also {
        model.add(it.account, it.credentials)
      }
    }

  private fun createOAuthEnterpriseLoginAction(project: Project, parentComponent: JComponent?) =
    DumbAwareAction.create(GitLabBundle.message("account.add.custom.server.popup.text")) {
      logInViaOAuthToCustomServer(
        project,
        parentComponent,
        loginSource = GitLabLoginSource.SETTINGS,
        uniqueAccountPredicate = ::isAccountUnique
      ).asSafely<LoginResult.Success>()?.also {
        model.add(it.account, it.credentials)
      }
    }

  private fun isAccountUnique(serverPath: GitLabServerPath, username: String) =
    GitLabLoginUtil.isAccountUnique(model.accounts, serverPath, username)
}