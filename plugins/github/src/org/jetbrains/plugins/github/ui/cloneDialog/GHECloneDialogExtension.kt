// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.isGHAccount
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.GithubUtil
import javax.swing.JComponent

private val GithubAccount.isGHEAccount: Boolean get() = !isGHAccount

class GHECloneDialogExtension : BaseCloneDialogExtension() {
  override fun getName(): String = GithubUtil.ENTERPRISE_SERVICE_DISPLAY_NAME

  override fun getAccounts(): Collection<GithubAccount> = GithubAuthenticationManager.getInstance().getAccounts().filter { it.isGHEAccount }

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    GHECloneDialogExtensionComponent(project, modalityState)
}

private class GHECloneDialogExtensionComponent(project: Project, modalityState: ModalityState) : GHCloneDialogExtensionComponentBase(
  project,
  modalityState,
  GithubAuthenticationManager.getInstance()
) {

  override fun isAccountHandled(account: GithubAccount): Boolean = account.isGHEAccount

  override fun createLoginPanel(account: GithubAccount?, cancelHandler: () -> Unit): JComponent =
    GHECloneDialogLoginPanel(account).apply {
      Disposer.register(this@GHECloneDialogExtensionComponent, this)

      loginPanel.isCancelVisible = getAccounts().isNotEmpty()
      loginPanel.setCancelHandler(cancelHandler)
    }

  override fun createAccountMenuLoginActions(account: GithubAccount?): Collection<AccountMenuItem.Action> =
    listOf(createLoginAction(account))

  private fun createLoginAction(account: GithubAccount?): AccountMenuItem.Action {
    val isExistingAccount = account != null
    return AccountMenuItem.Action(
      message("login.to.github.enterprise.action"),
      { switchToLogin(account) },
      showSeparatorAbove = !isExistingAccount
    )
  }
}

private class GHECloneDialogLoginPanel(account: GithubAccount?) : BorderLayoutPanel(), Disposable {
  private val titlePanel =
    simplePanel().apply {
      val title = JBLabel(message("login.to.github.enterprise"), ComponentStyle.LARGE).apply { font = JBFont.label().biggerOn(5.0f) }
      addToLeft(title)
    }
  val loginPanel = CloneDialogLoginPanel(account).apply {
    Disposer.register(this@GHECloneDialogLoginPanel, this)

    if (account == null) setServer("", true)
    setTokenUi()
  }

  init {
    addToTop(titlePanel.apply { border = JBEmptyBorder(getRegularPanelInsets().apply { bottom = 0 }) })
    addToCenter(loginPanel)
  }

  override fun dispose() = loginPanel.cancelLogin()
}