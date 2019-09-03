// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.util.GithubAsyncUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTextArea

class GithubLoginDialog @JvmOverloads constructor(executorFactory: GithubApiRequestExecutor.Factory,
                                                  project: Project?,
                                                  parent: Component? = null,
                                                  isAccountUnique: (name: String, server: GithubServerPath) -> Boolean = { _, _ -> true },
                                                  title: String = "Log In to GitHub",
                                                  private val message: String? = null)
  : DialogWrapper(project, parent, false, IdeModalityType.PROJECT) {
  private var githubLoginPanel = GithubLoginPanel(executorFactory, isAccountUnique, project).apply {
    putClientProperty("isVisualPaddingCompensatedOnComponentLevel", false)
  }

  internal lateinit var login: String
  internal lateinit var token: String

  init {
    this.title = title
    setOKButtonText("Log In")
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
    val emptyProgressIndicator = EmptyProgressIndicator(ModalityState.stateForComponent(githubLoginPanel))
    Disposer.register(disposable, Disposable { emptyProgressIndicator.cancel() })
    githubLoginPanel.acquireLoginAndToken(emptyProgressIndicator).handleOnEdt { loginToken, throwable ->
      if (throwable != null && !GithubAsyncUtil.isCancellation(throwable)) startTrackingValidation()
      if (loginToken != null) {
        login = loginToken.first
        token = loginToken.second
        close(OK_EXIT_CODE, true)
      }
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

  override fun createSouthAdditionalPanel() = JBUI.Panels.simplePanel()
    .addToCenter(LinkLabel.create("Sign up for GitHub", Runnable { BrowserUtil.browse("https://github.com") }))
    .addToRight(JBLabel(AllIcons.Ide.External_link_arrow))

  override fun createCenterPanel(): Wrapper = githubLoginPanel

  override fun getPreferredFocusedComponent(): JComponent = githubLoginPanel.getPreferredFocus()

  override fun doValidateAll() = githubLoginPanel.doValidateAll()
}
