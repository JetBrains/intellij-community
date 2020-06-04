// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubLoginPanel
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.event.ActionListener
import javax.swing.JPanel

internal class GHCloneDialogLoginPanel(private val account: GithubAccount?) : BorderLayoutPanel() {
  private val authenticationManager get() = GithubAuthenticationManager.getInstance()

  private val errorPanel = JPanel(VerticalLayout(10))
  private val loginPanel = GithubLoginPanel(
    GithubApiRequestExecutor.Factory.getInstance(),
    { name, server -> if (account == null) authenticationManager.isAccountUnique(name, server) else true },
    false
  )

  init {
    addToTop(loginPanel)
    addToCenter(errorPanel)

    if (account != null) {
      loginPanel.setCredentials(account.name, null, false)
      loginPanel.setServer(account.server.toUrl(), false)
    }
    loginPanel.setLoginListener(ActionListener { login() })
    loginPanel.setLoginButtonVisible(true)
    loginPanel.setCancelButtonVisible(authenticationManager.hasAccounts())
  }

  fun setCancelHandler(listener: () -> Unit) = loginPanel.setCancelListener(ActionListener { listener() })

  private fun login() {
    val modalityState = ModalityState.stateForComponent(this)

    loginPanel.acquireLoginAndToken(EmptyProgressIndicator(modalityState))
      .completionOnEdt(modalityState) { errorPanel.removeAll() }
      .errorOnEdt(modalityState) {
        for (validationInfo in loginPanel.doValidateAll()) {
          val component = SimpleColoredComponent()
          component.append(validationInfo.message, SimpleTextAttributes.ERROR_ATTRIBUTES)
          errorPanel.add(component)
          errorPanel.revalidate()
        }
        errorPanel.repaint()
      }
      .successOnEdt(modalityState) { (login, token) ->
        if (account != null) {
          authenticationManager.updateAccountToken(account, token)
        }
        else {
          authenticationManager.registerAccount(login, loginPanel.getServer().host, token)
        }
      }
  }
}