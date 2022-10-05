// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI.Panels.simplePanel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import java.awt.Component
import javax.swing.Action
import javax.swing.JComponent

internal class GHOAuthLoginDialog(project: Project?, parent: Component?, isAccountUnique: UniqueLoginPredicate) :
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