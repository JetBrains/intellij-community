// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.ENTER
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes.ERROR_ATTRIBUTES
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.layout.*
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.JBUI.emptyInsets
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubLoginPanel
import org.jetbrains.plugins.github.authentication.ui.setPasswordUi
import org.jetbrains.plugins.github.authentication.ui.setTokenUi
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants.TOP

internal class CloneDialogLoginPanel(private val account: GithubAccount?) :
  JBPanel<CloneDialogLoginPanel>(VerticalLayout(0)) {

  private val authenticationManager get() = GithubAuthenticationManager.getInstance()

  private val errorPanel = JPanel(VerticalLayout(10))
  private val loginPanel = GithubLoginPanel(GithubApiRequestExecutor.Factory.getInstance()) { name, server ->
    if (account == null) authenticationManager.isAccountUnique(name, server) else true
  }
  private val inlineCancelPanel = simplePanel()
  private val loginButton = JButton(message("button.login.mnemonic"))
  private val backLink = LinkLabel<Any?>(IdeBundle.message("button.back"), null).apply { verticalAlignment = TOP }

  private var loginIndicator: ProgressIndicator? = null

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

  fun setCancelHandler(listener: () -> Unit) =
    backLink.setListener(
      { _, _ ->
        cancelLogin()
        listener()
      },
      null
    )

  fun setTokenUi() {
    setupNewUi(false)
    loginPanel.setTokenUi()
  }

  fun setPasswordUi() {
    setupNewUi(false)
    loginPanel.setPasswordUi()
  }

  fun setOAuthUi() {
    setupNewUi(true)
    loginPanel.setOAuthUi()

    login()
  }

  fun setServer(path: String, editable: Boolean) = loginPanel.setServer(path, editable)

  private fun buildLayout() {
    add(JPanel(HorizontalLayout(0)).apply {
      add(loginPanel)
      add(inlineCancelPanel.apply { border = JBEmptyBorder(getRegularPanelInsets().apply { left = scale(6) }) })
    })
    add(errorPanel.apply { border = JBEmptyBorder(getRegularPanelInsets().apply { top = 0 }) })
  }

  private fun setupNewUi(isOAuth: Boolean) {
    loginButton.isVisible = !isOAuth
    backLink.text = if (isOAuth) IdeBundle.message("link.cancel") else IdeBundle.message("button.back")

    loginPanel.footer = { if (!isOAuth) buttonPanel() } // footer is used to put buttons in 2-nd column - align under text boxes
    if (isOAuth) inlineCancelPanel.addToCenter(backLink)
    inlineCancelPanel.isVisible = isOAuth

    errorPanel.removeAll()
  }

  private fun LayoutBuilder.buttonPanel() =
    row("") {
      cell {
        loginButton()
        backLink().withLargeLeftGap()
      }
    }

  fun cancelLogin() {
    loginIndicator?.cancel()
    loginIndicator = null
  }

  private fun login() {
    cancelLogin()

    val modalityState = ModalityState.stateForComponent(this)
    val indicator = EmptyProgressIndicator(modalityState)

    loginIndicator = indicator
    loginPanel.acquireLoginAndToken(indicator)
      .completionOnEdt(modalityState) {
        loginIndicator = null
        errorPanel.removeAll()
      }
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