// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.cloneDialog

import com.intellij.application.subscribe
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager.Companion.ACCOUNT_REMOVED_TOPIC
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager.Companion.ACCOUNT_TOKEN_CHANGED_TOPIC
import org.jetbrains.plugins.github.authentication.accounts.isGHAccount
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.util.CachingGithubUserAvatarLoader
import org.jetbrains.plugins.github.util.GithubImageResizer
import org.jetbrains.plugins.github.util.GithubUtil
import javax.swing.JComponent

private val GithubAccount.isGHEAccount: Boolean get() = !isGHAccount

private fun getGHEAccounts(): Collection<GithubAccount> =
  GithubAuthenticationManager.getInstance().getAccounts().filter { it.isGHEAccount }

class GHECloneDialogExtension : BaseCloneDialogExtension() {
  override fun getName(): String = GithubUtil.ENTERPRISE_SERVICE_DISPLAY_NAME

  override fun getAccounts(): Collection<GithubAccount> = getGHEAccounts()

  override fun createMainComponent(project: Project, modalityState: ModalityState): VcsCloneDialogExtensionComponent =
    object : BaseCloneDialogExtensionComponent(
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

      override fun getAccounts(): Collection<GithubAccount> = getGHEAccounts()

      override fun accountRemoved(removedAccount: GithubAccount) {
        if (removedAccount.isGHEAccount) super.accountRemoved(removedAccount)
      }

      override fun tokenChanged(account: GithubAccount) {
        if (account.isGHEAccount) super.tokenChanged(account)
      }

      override fun createLoginPanel(account: GithubAccount?, cancelHandler: () -> Unit): JComponent =
        GHECloneDialogLoginPanel(account).apply {
          loginPanel.isCancelVisible = getAccounts().isNotEmpty()
          loginPanel.setCancelHandler(cancelHandler)
        }
    }
}

private class GHECloneDialogLoginPanel(account: GithubAccount?) : BorderLayoutPanel() {
  private val titlePanel =
    simplePanel().apply {
      val title = JBLabel(message("login.to.github.enterprise"), ComponentStyle.LARGE).apply { font = JBFont.label().biggerOn(5.0f) }
      addToLeft(title)
    }
  val loginPanel = CloneDialogLoginPanel(account).apply {
    if (account == null) setServer("", true)
    setTokenUi()
  }

  init {
    addToTop(titlePanel.apply { border = JBEmptyBorder(getRegularPanelInsets().apply { bottom = 0 }) })
    addToCenter(loginPanel)
  }
}