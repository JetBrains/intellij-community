// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY
import com.intellij.openapi.util.Disposer
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
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.completionOnEdt
import org.jetbrains.plugins.github.util.errorOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTextArea

private fun JComponent.setPaddingCompensated(): JComponent =
  apply { putClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY, false) }

class GithubLoginDialog @JvmOverloads constructor(executorFactory: GithubApiRequestExecutor.Factory,
                                                  project: Project?,
                                                  parent: Component? = null,
                                                  isAccountUnique: UniqueLoginPredicate = { _, _ -> true },
                                                  @Nls(capitalization = Nls.Capitalization.Title) title: String =
                                                    GithubBundle.message("login.to.github"),
                                                  @Nls(capitalization = Nls.Capitalization.Sentence) private val message: String? = null)
  : DialogWrapper(project, parent, false, IdeModalityType.PROJECT) {

  private val githubLoginPanel = GithubLoginPanel(executorFactory, isAccountUnique)
  private val switchLoginUiLink = githubLoginPanel.createSwitchUiLink()

  internal lateinit var login: String
  internal lateinit var token: String

  init {
    this.title = title
    setOKButtonText(GitBundle.message("login.dialog.button.login"))
    init()
  }

  @JvmOverloads
  fun withServer(path: String, editable: Boolean = true): GithubLoginDialog {
    githubLoginPanel.setServer(path, editable)
    return this
  }

  @JvmOverloads
  fun withCredentials(login: String? = null, password: String? = null, editableLogin: Boolean = true): GithubLoginDialog {
    githubLoginPanel.setCredentials(login, password, editableLogin)
    return this
  }

  @JvmOverloads
  fun withToken(token: String? = null): GithubLoginDialog {
    githubLoginPanel.setToken(token)
    return this
  }

  fun withError(exception: Throwable): GithubLoginDialog {
    githubLoginPanel.setError(exception)
    startTrackingValidation()
    return this
  }

  fun getServer(): GithubServerPath = githubLoginPanel.getServer()

  fun getLogin(): String = login

  fun getToken(): String = token

  override fun doOKAction() {
    val modalityState = ModalityState.stateForComponent(githubLoginPanel)
    val emptyProgressIndicator = EmptyProgressIndicator(modalityState)
    Disposer.register(disposable, Disposable { emptyProgressIndicator.cancel() })

    switchLoginUiLink.isEnabled = false
    githubLoginPanel.acquireLoginAndToken(emptyProgressIndicator)
      .completionOnEdt(modalityState) { switchLoginUiLink.isEnabled = true }
      .successOnEdt(modalityState) { (login, token) ->
        this.login = login
        this.token = token
        close(OK_EXIT_CODE, true)
      }
      .errorOnEdt(modalityState) {
        if (!GithubAsyncUtil.isCancellation(it)) startTrackingValidation()
      }
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

  override fun createSouthAdditionalPanel() = simplePanel()
    .addToCenter(LinkLabel.create(GithubBundle.message("login.sign.up"), Runnable { BrowserUtil.browse("https://github.com") }))
    .addToRight(JBLabel(AllIcons.Ide.External_link_arrow))

  override fun createCenterPanel(): JComponent =
    simplePanel()
      .addToTop(
        simplePanel().apply {
          border = JBEmptyBorder(getRegularPanelInsets().apply { bottom = 0 })

          addToRight(switchLoginUiLink)
        }
      )
      .addToCenter(githubLoginPanel)
      .setPaddingCompensated()

  override fun getPreferredFocusedComponent(): JComponent = githubLoginPanel.getPreferredFocus()

  override fun doValidateAll() = githubLoginPanel.doValidateAll()
}
