// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import com.intellij.util.AuthData
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent


class GitHttpLoginDialog @JvmOverloads constructor(project: Project,
                                                   url: @NlsSafe String,
                                                   rememberPassword: Boolean = true,
                                                   username: @NlsSafe String? = null,
                                                   editableUsername: Boolean = true,
                                                   private val showActionForCredHelper: Boolean = false) : DialogWrapper(project, true) {
  private val usernameField = JBTextField(username, 20).apply { isEditable = editableUsername }
  private val passwordField = JBPasswordField()
  private val rememberCheckbox = JBCheckBox(UIBundle.message("auth.remember.cb"), rememberPassword)
  private val additionalProvidersButton = JBOptionButton(null, null).apply { isVisible = false }
  private var useCredentialHelper = false
  private lateinit var dialogPanel: DialogPanel

  companion object {
    const val USE_CREDENTIAL_HELPER_CODE = NEXT_USER_EXIT_CODE
  }

  var externalAuthData: AuthData? = null
    private set

  init {
    title = GitBundle.message("login.dialog.label.login.to.url", url)
    setOKButtonText(GitBundle.message("login.dialog.button.login"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    if (!showActionForCredHelper) {
      dialogPanel = panel {
        row(GitBundle.message("login.dialog.prompt.enter.credentials")) {}
        buildCredentialsPanel()
      }
    }
    else {
      dialogPanel = panel {
        buttonGroup(::useCredentialHelper) {
          row {
            radioButton(GitBundle.message("login.dialog.select.login.way.credentials"), false)
            row {
              buildCredentialsPanel()
            }
          }
          row {
            radioButton(GitBundle.message("login.dialog.select.login.way.use.helper"), true).also {
              it.component.addActionListener {
                isOKActionEnabled = true
              }
            }
          }
        }
      }
    }
    return dialogPanel
  }

  private fun RowBuilder.buildCredentialsPanel() {
    row(GitBundle.message("login.dialog.username.label")) { usernameField(growX) }
    row(GitBundle.message("login.dialog.password.label")) { passwordField(growX) }
    row { rememberCheckbox() }
  }

  override fun doOKAction() {
    dialogPanel.apply()
    if (useCredentialHelper) {
      close(USE_CREDENTIAL_HELPER_CODE, false)
      return
    }
    super.doOKAction()
  }

  override fun doValidateAll(): List<ValidationInfo> {
    dialogPanel.apply()
    if (useCredentialHelper) {
      return emptyList()
    }
    return listOfNotNull(
      if (username.isBlank())
        ValidationInfo(GitBundle.message("login.dialog.error.username.cant.be.empty"), usernameField)
      else null,
      if (passwordField.password.isEmpty())
        ValidationInfo(GitBundle.message("login.dialog.error.password.cant.be.empty"), passwordField)
      else null
    )
  }

  override fun createSouthAdditionalPanel(): Wrapper = Wrapper(additionalProvidersButton)
    .apply { border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP) }

  override fun getPreferredFocusedComponent(): JComponent = if (username.isBlank()) usernameField else passwordField

  fun setInteractiveDataProviders(providers: Map<String, InteractiveGitHttpAuthDataProvider>) {
    if (providers.isEmpty()) return

    val actions: List<AbstractAction> = providers.map {
      object : AbstractAction(GitBundle.message("login.dialog.login.with.selected.provider", it.key)) {
        override fun actionPerformed(e: ActionEvent?) {
          val authData = it.value.getAuthData(this@GitHttpLoginDialog.rootPane)
          if (authData != null) {
            if (authData.password != null) {
              externalAuthData = authData
              this@GitHttpLoginDialog.close(0, true)
            }
            else {
              usernameField.text = authData.login
            }
          }
        }
      }
    }
    additionalProvidersButton.action = actions.first()
    if (actions.size > 1) {
      additionalProvidersButton.options = actions.subList(1, actions.size).toTypedArray()
    }
    additionalProvidersButton.isVisible = true
  }

  @set:VisibleForTesting
  var username: String
    get() = usernameField.text
    internal set(value) {
      usernameField.text = value
    }

  @set:VisibleForTesting
  var password: String
    get() = String(passwordField.password!!)
    internal set(value) {
      passwordField.text = value
    }

  @set:VisibleForTesting
  var rememberPassword: Boolean
    get() = rememberCheckbox.isSelected
    internal set(value) {
      rememberCheckbox.isSelected = value
    }
}

@Suppress("HardCodedStringLiteral")
class TestGitHttpLoginDialogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let {
      val gitHttpLoginDialog = GitHttpLoginDialog(it, "http://google.com", showActionForCredHelper = true)
      gitHttpLoginDialog.show()
      if (gitHttpLoginDialog.exitCode == GitHttpLoginDialog.USE_CREDENTIAL_HELPER_CODE) {
        Messages.showMessageDialog(e.project,"Credential selected", "Git login test", null)
      }
      else {
        Messages.showMessageDialog(e.project, "Regular login", "Git login test", null)
      }
    }
  }
}
