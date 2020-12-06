// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import git4idea.i18n.GitBundle
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import java.awt.Component
import javax.swing.JComponent

class AddGHAccountWithTokenAction : BaseAddAccountWithTokenAction() {
  override val defaultServer: String get() = GithubServerPath.DEFAULT_HOST
}

class AddGHEAccountAction : BaseAddAccountWithTokenAction() {
  override val defaultServer: String get() = ""
}

abstract class BaseAddAccountWithTokenAction : DumbAwareAction() {
  abstract val defaultServer: String

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(GHAccountsHost.KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val accountsHost = e.getData(GHAccountsHost.KEY)!!
    val dialog = newAddAccountDialog(e.project, e.getData(CONTEXT_COMPONENT), accountsHost::isAccountUnique)

    dialog.setServer(defaultServer, defaultServer != GithubServerPath.DEFAULT_HOST)
    if (dialog.showAndGet()) {
      accountsHost.addAccount(dialog.server, dialog.login, dialog.token)
    }
  }
}

private fun newAddAccountDialog(project: Project?, parent: Component?, isAccountUnique: UniqueLoginPredicate): BaseLoginDialog =
  GHTokenLoginDialog(project, parent, isAccountUnique).apply {
    title = message("dialog.title.add.github.account")
    setLoginButtonText(message("button.add.account"))
  }

internal class GHTokenLoginDialog(project: Project?, parent: Component?, isAccountUnique: UniqueLoginPredicate) :
  BaseLoginDialog(project, parent, GithubApiRequestExecutor.Factory.getInstance(), isAccountUnique) {

  init {
    title = message("login.to.github")
    setLoginButtonText(GitBundle.message("login.dialog.button.login"))
    loginPanel.setTokenUi()

    init()
  }

  internal fun setLoginButtonText(@NlsContexts.Button text: String) = setOKButtonText(text)

  override fun createCenterPanel(): JComponent = loginPanel.setPaddingCompensated()
}