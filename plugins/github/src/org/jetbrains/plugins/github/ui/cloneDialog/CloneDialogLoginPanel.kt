// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.ENTER
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes.ERROR_ATTRIBUTES
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.emptyInsets
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubLoginPanel
import org.jetbrains.plugins.github.authentication.ui.setTokenUi
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class CloneDialogLoginPanel(private val account: GithubAccount?) :
  JBPanel<CloneDialogLoginPanel>(VerticalLayout(0)) {

  private val authenticationManager get() = GithubAuthenticationManager.getInstance()

  private val errorPanel = JPanel(VerticalLayout(10))
  private val loginPanel = GithubLoginPanel(GithubApiRequestExecutor.Factory.getInstance()) { name, server ->
    if (account == null) authenticationManager.isAccountUnique(name, server) else true
  }
  private val loginButton = JButton(message("button.login.mnemonic"))
  private val backLink = LinkLabel<Any?>(IdeBundle.message("button.back"), null)

  var isCancelVisible: Boolean
    get() = backLink.isVisible
    set(value) {
      backLink.isVisible = value
    }

  init {
    buildLayout()

    if (account != null) {
      loginPanel.setCredentials(account.name, null, false)
      loginPanel.setServer(account.server.toUrl(), false)
    }

    loginButton.addActionListener { login() }
    LoginAction().registerCustomShortcutSet(ENTER, loginPanel)
  }

  fun setCancelHandler(listener: () -> Unit) = backLink.setListener({ _, _ -> listener() }, null)

  fun createSwitchUiLink(): LinkLabel<*> = loginPanel.createSwitchUiLink()
  fun setTokenUi() = loginPanel.setTokenUi()
  fun setServer(path: String, editable: Boolean) = loginPanel.setServer(path, editable)

  private fun buildLayout() {
    loginPanel.footer = { buttonPanel() } // footer is used to put buttons in 2-nd column - align under text boxes

    add(loginPanel)
    add(errorPanel.apply { border = JBEmptyBorder(getRegularPanelInsets().apply { top = 0 }) })
  }

  private fun LayoutBuilder.buttonPanel() =
    row("") {
      cell {
        loginButton()
        backLink().withLargeLeftGap()
      }
    }

  private fun login() {
    val modalityState = ModalityState.stateForComponent(this)

    loginPanel.acquireLoginAndToken(EmptyProgressIndicator(modalityState))
      .completionOnEdt(modalityState) { errorPanel.removeAll() }
      .errorOnEdt(modalityState) {
        loginPanel.doValidateAll().forEach { errorPanel.add(toErrorComponent(it)) }

        errorPanel.revalidate()
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

  private fun toErrorComponent(info: ValidationInfo): JComponent =
    SimpleColoredComponent().apply {
      myBorder = empty()
      ipad = emptyInsets()

      append(info.message, ERROR_ATTRIBUTES)
    }

  private inner class LoginAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.getData(CONTEXT_COMPONENT) != backLink
    }

    override fun actionPerformed(e: AnActionEvent) = login()
  }
}