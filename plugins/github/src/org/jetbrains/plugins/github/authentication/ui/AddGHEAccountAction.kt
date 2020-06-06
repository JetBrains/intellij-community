// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import java.awt.Component
import javax.swing.JComponent

class AddGHEAccountAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(GHAccountsPanel.KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val accountsPanel = e.getData(GHAccountsPanel.KEY)!!
    val dialog = AddGHEAccountDialog(e.project, accountsPanel, accountsPanel::isAccountUnique)

    if (dialog.showAndGet()) {
      accountsPanel.addAccount(dialog.server, dialog.login, dialog.token)
    }
  }
}

private class AddGHEAccountDialog(project: Project?, parent: Component?, isAccountUnique: UniqueLoginPredicate) :
  BaseLoginDialog(project, parent, GithubApiRequestExecutor.Factory.getInstance(), isAccountUnique) {

  init {
    title = message("dialog.title.add.github.account")
    setOKButtonText(message("button.add.account"))

    setServer("", true)
    loginPanel.setTokenUi()

    init()
  }

  override fun createCenterPanel(): JComponent = loginPanel
}