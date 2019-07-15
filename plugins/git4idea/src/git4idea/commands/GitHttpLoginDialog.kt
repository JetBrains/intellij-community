// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import com.google.common.annotations.VisibleForTesting
import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import com.intellij.util.AuthData
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JRadioButton


class GitHttpLoginDialog @JvmOverloads constructor(project: Project,
                                                   url: String,
                                                   rememberPassword: Boolean = true,
                                                   username: String? = null,
                                                   editableUsername: Boolean = true,
                                                   private val showActionForCredHelper: Boolean = false) : DialogWrapper(project, true) {
  private val usernameField = JBTextField(username).apply { isEditable = editableUsername }
  private val passwordField = JBPasswordField()
  private val rememberCheckbox = JBCheckBox(CommonBundle.message("checkbox.remember.password"), rememberPassword)
  private val additionalProvidersButton = JBOptionButton(null, null).apply { isVisible = false }
  private var useCredentialHelper = false

  companion object {
    const val USE_CREDENTIAL_HELPER_CODE = NEXT_USER_EXIT_CODE
  }

  var externalAuthData: AuthData? = null
    private set

  init {
    title = "Log In to $url"
    setOKButtonText("Log In")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val credentialsPanel = panel {
      if (!showActionForCredHelper) {
        row("Enter credentials:") {}
      }
      row("Username:") { usernameField(CCFlags.growX) }
      row("Password:") { passwordField(CCFlags.growX) }
      row { rememberCheckbox() }
    }
    if (!showActionForCredHelper) {
      return credentialsPanel
    }

    val credRadioButton = JRadioButton("Enter credentials:", true).apply {
      addActionListener {
        useCredentialHelper = false
        UIUtil.setEnabled(credentialsPanel, true, true)
      }
    }
    val helperRadioButton = JRadioButton("Use credentials helper", false).apply {
      addActionListener {
        isOKActionEnabled = true
        useCredentialHelper = true
        UIUtil.setEnabled(credentialsPanel, false, true)
      }
    }
    return panel {
      buttonGroup {
        row {
          credRadioButton()
          row { credentialsPanel(CCFlags.growX) }
        }
        row { helperRadioButton() }
      }
    }
  }

  override fun doOKAction() {
    if (useCredentialHelper) {
      close(USE_CREDENTIAL_HELPER_CODE, false)
      return
    }
    super.doOKAction()
  }

  override fun doValidateAll(): List<ValidationInfo> {
    if (useCredentialHelper) {
      return emptyList()
    }
    return listOfNotNull(if (username.isBlank()) ValidationInfo("Username cannot be empty", usernameField) else null,
                         if (passwordField.password.isEmpty()) ValidationInfo("Password cannot be empty", passwordField) else null)
  }

  override fun createSouthAdditionalPanel(): Wrapper = Wrapper(additionalProvidersButton)
    .apply { border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP) }

  override fun getPreferredFocusedComponent(): JComponent = if (username.isBlank()) usernameField else passwordField

  fun setInteractiveDataProviders(providers: Map<String, InteractiveGitHttpAuthDataProvider>) {
    if (providers.isEmpty()) return

    val actions: List<AbstractAction> = providers.map {
      object : AbstractAction("Log In with ${it.key}...") {
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

