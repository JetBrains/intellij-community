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
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

internal class GithubLoginDialog @JvmOverloads constructor(
  executorFactory: GithubApiRequestExecutor.Factory,
  project: Project?,
  parent: Component? = null,
  isAccountUnique: UniqueLoginPredicate = { _, _ -> true },
  @Nls(capitalization = Nls.Capitalization.Title) title: String = GithubBundle.message("login.to.github"),
  @Nls(capitalization = Nls.Capitalization.Sentence) private val message: String? = null
) : BaseLoginDialog(project, parent, executorFactory, isAccountUnique) {

  private val switchLoginUiLink = loginPanel.createSwitchUiLink()

  init {
    this.title = title
    setOKButtonText(GitBundle.message("login.dialog.button.login"))
    init()
  }

  @JvmOverloads
  fun withServer(path: String, editable: Boolean = true): GithubLoginDialog = apply { setServer(path, editable) }

  @JvmOverloads
  fun withCredentials(login: String? = null, password: String? = null, editableLogin: Boolean = true): GithubLoginDialog =
    apply { loginPanel.setCredentials(login, password, editableLogin) }

  @JvmOverloads
  fun withToken(token: String? = null): GithubLoginDialog = apply { loginPanel.setToken(token) }

  fun withError(exception: Throwable): GithubLoginDialog =
    apply {
      loginPanel.setError(exception)
      startTrackingValidation()
    }

  override fun startGettingToken() {
    switchLoginUiLink.isEnabled = false
  }

  override fun finishGettingToken() {
    switchLoginUiLink.isEnabled = true
  }

  override fun createNorthPanel(): JComponent? {
    return message?.let {
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
