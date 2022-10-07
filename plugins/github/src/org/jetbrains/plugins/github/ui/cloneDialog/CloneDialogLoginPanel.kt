// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts.ENTER
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes.ERROR_ATTRIBUTES
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubLoginPanel
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class CloneDialogLoginPanel(private val account: GithubAccount?) :
  JBPanel<CloneDialogLoginPanel>(VerticalLayout(0)),
  Disposable {

  private val accountManager get() = service<GHAccountManager>()

  private val errorPanel = JPanel(VerticalLayout(10))
  private val loginPanel = GithubLoginPanel(GithubApiRequestExecutor.Factory.getInstance()) { name, server ->
    if (account == null) accountManager.accountsState.value.none {
      it.name == name && it.server.equals(server, true)
    }
    else true
  }
  private val inlineCancelPanel = simplePanel()
  private val loginButton = JButton(message("button.login.mnemonic"))
  private val backLink = LinkLabel<Any?>(IdeBundle.message("button.back"), null).apply {
    verticalAlignment = SwingConstants.CENTER
  }

  private var errors = emptyList<ValidationInfo>()
  private var loginIndicator: ProgressIndicator? = null

  var isCancelVisible: Boolean
    get() = backLink.isVisible
    set(value) {
      backLink.isVisible = value
    }

  init {
    buildLayout()

    if (account != null) {
      loginPanel.setServer(account.server.toUrl(), false)
      loginPanel.setLogin(account.name, false)
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

  fun setOAuthUi() {
    setupNewUi(true)
    loginPanel.setOAuthUi()

    login()
  }

  fun setServer(path: String, editable: Boolean) = loginPanel.setServer(path, editable)

  override fun dispose() {
    cancelLogin()
  }

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

    clearErrors()
  }

  private fun Panel.buttonPanel() =
    row("") {
      cell(loginButton)
      cell(backLink)
    }

  fun cancelLogin() {
    loginIndicator?.cancel()
    loginIndicator = null
  }

  private fun login() {
    cancelLogin()

    loginPanel.setError(null)
    clearErrors()
    if (!doValidate()) return

    val modalityState = ModalityState.stateForComponent(this)
    val indicator = EmptyProgressIndicator(modalityState)

    loginIndicator = indicator
    loginPanel.acquireLoginAndToken(indicator)
      .completionOnEdt(modalityState) {
        loginIndicator = null
        clearErrors()
      }
      .errorOnEdt(modalityState) { doValidate() }
      .successOnEdt(modalityState) { (login, token) ->
        val acc = account ?: GHAccountManager.createAccount(login, loginPanel.getServer())
        accountManager.updateAccount(acc, token)
      }
  }

  private fun doValidate(): Boolean {
    errors = loginPanel.doValidateAll()
    setErrors(errors)

    val toFocus = errors.firstOrNull()?.component
    if (toFocus?.isVisible == true) IdeFocusManager.getGlobalInstance().requestFocus(toFocus, true)

    return errors.isEmpty()
  }

  private fun clearErrors() {
    for (component in errors.mapNotNull { it.component }) {
      ComponentValidator.getInstance(component).ifPresent { it.updateInfo(null) }
    }
    errorPanel.removeAll()
    errors = emptyList()
  }

  private fun setErrors(errors: Collection<ValidationInfo>) {
    for (error in errors) {
      val component = error.component

      if (component != null) {
        ComponentValidator.getInstance(component)
          .orElseGet { ComponentValidator(this).installOn(component) }
          .updateInfo(error)
      }
      else {
        errorPanel.add(toErrorComponent(error))
      }
    }

    errorPanel.revalidate()
    errorPanel.repaint()
  }

  private fun toErrorComponent(info: ValidationInfo): JComponent =
    SimpleColoredComponent().apply {
      myBorder = empty()
      ipad = JBInsets.emptyInsets()

      append(info.message, ERROR_ATTRIBUTES)
    }

  private inner class LoginAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = e.getData(CONTEXT_COMPONENT) != backLink
    }

    override fun actionPerformed(e: AnActionEvent) = login()
  }
}