// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import git4idea.i18n.GitBundle
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import java.awt.Component
import javax.swing.JComponent

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