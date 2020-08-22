// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Sentence
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

internal class GHPasswordTokenLoginDialog(
  project: Project?,
  parent: Component?,
  isAccountUnique: UniqueLoginPredicate,
  @Nls(capitalization = Sentence) private val message: String?
) : BaseLoginDialog(project, parent, GithubApiRequestExecutor.Factory.getInstance(), isAccountUnique) {

  private val switchLoginUiLink = loginPanel.createSwitchUiLink()

  init {
    title = GithubBundle.message("login.to.github")
    setOKButtonText(GitBundle.message("login.dialog.button.login"))
    init()
  }

  fun setPassword(password: String?) = loginPanel.setPassword(password)

  override fun startGettingToken() {
    switchLoginUiLink.isEnabled = false
  }

  override fun finishGettingToken() {
    switchLoginUiLink.isEnabled = true
  }

  override fun createNorthPanel(): JComponent? =
    message?.let {
      JTextArea().apply {
        font = UIUtil.getLabelFont()
        text = it
        isEditable = false
        isFocusable = false
        isOpaque = false
        border = JBUI.Borders.emptyBottom(UIUtil.DEFAULT_VGAP * 2)
        margin = JBUI.emptyInsets()
      }
    }

  override fun createSouthAdditionalPanel(): JPanel = createSignUpLink()

  override fun createCenterPanel(): JComponent =
    simplePanel()
      .addToTop(
        simplePanel().apply {
          border = JBEmptyBorder(getRegularPanelInsets().apply { bottom = 0 })

          addToRight(switchLoginUiLink)
        }
      )
      .addToCenter(loginPanel)
      .setPaddingCompensated()

  companion object {
    fun createSignUpLink(): JPanel = simplePanel()
      .addToCenter(LinkLabel.create(GithubBundle.message("login.sign.up")) { BrowserUtil.browse("https://github.com") })
      .addToRight(JBLabel(AllIcons.Ide.External_link_arrow))
  }
}
