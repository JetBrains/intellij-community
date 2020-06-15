// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI.Panels.simplePanel
import git4idea.i18n.GitBundle
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.isOAuthEnabled
import org.jetbrains.plugins.github.authentication.ui.GithubLoginDialog.Companion.createSignUpLink
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import java.awt.Component
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class AddGHAccountAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(GHAccountsPanel.KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val accountsPanel = e.getData(GHAccountsPanel.KEY)!!
    val dialog = createDialog(e.project, accountsPanel)
    dialog.setServer(GithubServerPath.DEFAULT_HOST, false)

    if (dialog.showAndGet()) {
      accountsPanel.addAccount(dialog.server, dialog.login, dialog.token)
    }
  }

  private fun createDialog(project: Project?, accountsPanel: GHAccountsPanel): BaseLoginDialog =
    if (isOAuthEnabled()) OAuthLoginDialog(project, accountsPanel, accountsPanel::isAccountUnique)
    else PasswordLoginDialog(project, accountsPanel, accountsPanel::isAccountUnique)
}

private class PasswordLoginDialog(project: Project?, parent: Component?, isAccountUnique: UniqueLoginPredicate) :
  BaseLoginDialog(project, parent, GithubApiRequestExecutor.Factory.getInstance(), isAccountUnique) {

  init {
    title = message("login.to.github")
    setOKButtonText(GitBundle.message("login.dialog.button.login"))
    init()
  }

  override fun createCenterPanel(): JComponent = loginPanel.setPaddingCompensated()

  override fun createSouthAdditionalPanel(): JPanel = createSignUpLink()
}

private class OAuthLoginDialog(project: Project?, parent: Component?, isAccountUnique: UniqueLoginPredicate) :
  BaseLoginDialog(project, parent, GithubApiRequestExecutor.Factory.getInstance(), isAccountUnique) {

  init {
    title = message("login.to.github")
    loginPanel.setOAuthUi()
    init()
  }

  override fun createActions(): Array<Action> = arrayOf(cancelAction)

  override fun show() {
    doOKAction()
    super.show()
  }

  override fun createCenterPanel(): JComponent =
    simplePanel(loginPanel)
      .withPreferredWidth(200)
      .setPaddingCompensated()
}