// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.application.subscribe
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager.Companion.ACCOUNT_REMOVED_TOPIC
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager.Companion.ACCOUNT_TOKEN_CHANGED_TOPIC
import org.jetbrains.plugins.github.authentication.accounts.isGHAccount
import org.jetbrains.plugins.github.authentication.isOAuthEnabled
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUtil
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

private fun getGHAccounts(): Collection<GithubAccount> =
  GithubAuthenticationManager.getInstance().getAccounts().filter { it.isGHAccount }

class GHCloneDialogExtension : BaseCloneDialogExtension() {
  override fun getName() = GithubUtil.SERVICE_DISPLAY_NAME

  override fun getAccounts(): Collection<GithubAccount> = getGHAccounts()

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    GHCloneDialogExtensionComponent(project)
}

private class GHCloneDialogExtensionComponent(project: Project) : BaseCloneDialogExtensionComponent(
  project,
  GithubAuthenticationManager.getInstance(),
  GithubApiRequestExecutorManager.getInstance(),
  GithubAccountInformationProvider.getInstance(),
  CachingGithubUserAvatarLoader.getInstance(),
  GithubImageResizer.getInstance()
) {

  init {
    ACCOUNT_REMOVED_TOPIC.subscribe(this, this)
    ACCOUNT_TOKEN_CHANGED_TOPIC.subscribe(this, this)

    setup()
  }

  override fun getAccounts(): Collection<GithubAccount> = getGHAccounts()

  override fun accountRemoved(removedAccount: GithubAccount) {
    if (removedAccount.isGHAccount) super.accountRemoved(removedAccount)
  }

  override fun tokenChanged(account: GithubAccount) {
    if (account.isGHAccount) super.tokenChanged(account)
  }

  override fun createLoginPanel(account: GithubAccount?, cancelHandler: () -> Unit): JComponent =
    GHCloneDialogLoginPanel(account).apply {
      Disposer.register(this@GHCloneDialogExtensionComponent, this)

      val chooseLoginUiHandler = { setChooseLoginUi() }
      loginPanel.setCancelHandler(if (getAccounts().isEmpty()) chooseLoginUiHandler else cancelHandler)
    }

  override fun createAccountMenuLoginActions(account: GithubAccount?): Collection<AccountMenuItem.Action> =
    listOf(createLoginAction(account), createLoginWithTokenAction(account))

  private fun createLoginAction(account: GithubAccount?): AccountMenuItem.Action {
    val isExistingAccount = account != null
    return AccountMenuItem.Action(
      message("login.via.github.action"),
      {
        switchToLogin(account)
        getLoginPanel()?.setPrimaryLoginUi()
      },
      showSeparatorAbove = !isExistingAccount
    )
  }

  private fun createLoginWithTokenAction(account: GithubAccount?): AccountMenuItem.Action =
    AccountMenuItem.Action(
      message("login.with.token.action"),
      {
        switchToLogin(account)
        getLoginPanel()?.setTokenUi()
      }
    )

  private fun getLoginPanel(): GHCloneDialogLoginPanel? = content as? GHCloneDialogLoginPanel
}

private class GHCloneDialogLoginPanel(account: GithubAccount?) :
  JBPanel<GHCloneDialogLoginPanel>(VerticalLayout(0)),
  Disposable {

  private val titlePanel =
    simplePanel().apply {
      val title = JBLabel(message("login.to.github"), ComponentStyle.LARGE).apply { font = JBFont.label().biggerOn(5.0f) }
      addToLeft(title)
    }
  private val contentPanel = Wrapper()

  private val chooseLoginUiPanel: JPanel =
    JPanel(HorizontalLayout(0)).apply {
      border = JBEmptyBorder(getRegularPanelInsets())

      val loginViaGHButton = JButton(message("login.via.github.action")).apply { addActionListener { setPrimaryLoginUi() } }
      val useTokenLink = LinkLabel.create(message("link.label.use.token")) { setTokenUi() }

      add(loginViaGHButton)
      add(JBLabel(message("label.login.option.separator")).apply { border = empty(0, 6, 0, 4) })
      add(useTokenLink)
    }
  val loginPanel = CloneDialogLoginPanel(account).apply { setServer(GithubServerPath.DEFAULT_HOST, false) }

  init {
    add(titlePanel.apply { border = JBEmptyBorder(getRegularPanelInsets().apply { bottom = 0 }) })
    add(contentPanel)

    setChooseLoginUi()
  }

  fun setChooseLoginUi() = setContent(chooseLoginUiPanel)

  fun setPrimaryLoginUi() = if (isOAuthEnabled()) setOAuthUi() else setPasswordUi()

  fun setTokenUi() {
    setContent(loginPanel)
    loginPanel.setTokenUi() // after `loginPanel` is set as content to ensure correct focus behavior
  }

  fun setPasswordUi() {
    setContent(loginPanel)
    loginPanel.setPasswordUi() // after `loginPanel` is set as content to ensure correct focus behavior
  }

  fun setOAuthUi() {
    setContent(loginPanel)
    loginPanel.setOAuthUi()
  }

  private fun setContent(content: JComponent) {
    contentPanel.setContent(content)

    revalidate()
    repaint()
  }

  override fun dispose() = loginPanel.cancelLogin()
}