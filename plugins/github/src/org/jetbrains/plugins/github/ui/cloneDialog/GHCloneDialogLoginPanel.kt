// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.ENTER
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubLoginPanel
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import javax.swing.JButton
import javax.swing.JPanel

internal class GHCloneDialogLoginPanel(private val account: GithubAccount?) :
  JBPanel<GHCloneDialogLoginPanel>(VerticalLayout(0)) {

  private val authenticationManager get() = GithubAuthenticationManager.getInstance()

  private val errorPanel = JPanel(VerticalLayout(10))
  private val loginPanel = GithubLoginPanel(GithubApiRequestExecutor.Factory.getInstance()) { name, server ->
    if (account == null) authenticationManager.isAccountUnique(name, server) else true
  }
  private val switchLoginUiLink = loginPanel.createSwitchUiLink()
  private val loginButton = JButton(message("button.login.mnemonic"))
  private val cancelButton = JButton(CommonBundle.message("button.cancel.c"))

  init {
    buildLayout()

    if (account != null) {
      loginPanel.setCredentials(account.name, null, false)
      loginPanel.setServer(account.server.toUrl(), false)
    }

    cancelButton.isVisible = authenticationManager.hasAccounts()
    loginButton.addActionListener { login() }
    LoginAction().registerCustomShortcutSet(ENTER, loginPanel)
  }

  fun setCancelHandler(listener: () -> Unit) = cancelButton.addActionListener { listener() }

  private fun buildLayout() {
    loginPanel.footer = { buttonPanel() } // footer is used to put buttons in 2-nd column - align under text boxes

    add(simplePanel().apply {
      border = JBEmptyBorder(getRegularPanelInsets().apply { bottom = 0 })

      val title = JBLabel(message("login.to.github"), ComponentStyle.LARGE).apply { font = JBFont.label().biggerOn(5.0f) }
      addToLeft(title)
      addToRight(switchLoginUiLink)
    })
    add(loginPanel)
    add(errorPanel)
  }

  private fun LayoutBuilder.buttonPanel() =
    row("") {
      cell {
        loginButton()
        cancelButton()
      }
    }

  private fun login() {
    val modalityState = ModalityState.stateForComponent(this)

    switchLoginUiLink.isEnabled = false
    loginPanel.acquireLoginAndToken(EmptyProgressIndicator(modalityState))
      .completionOnEdt(modalityState) {
        switchLoginUiLink.isEnabled = true
        errorPanel.removeAll()
      }
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

  private inner class LoginAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.getData(CONTEXT_COMPONENT) != cancelButton
    }

    override fun actionPerformed(e: AnActionEvent) = login()
  }
}