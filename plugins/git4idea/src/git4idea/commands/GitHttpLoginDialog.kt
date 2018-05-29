// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBOptionButton
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.AuthData
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI.PanelFactory.grid
import com.intellij.util.ui.UI.PanelFactory.panel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import git4idea.remote.InteractiveGitHttpAuthDataProvider
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTextArea


class GitHttpLoginDialog @JvmOverloads constructor(project: Project,
                                                   url: String,
                                                   rememberPassword: Boolean = true,
                                                   username: String? = null,
                                                   editableUsername: Boolean = true) : DialogWrapper(project, true) {
  private val descriptionLabel = JTextArea().apply {
    font = UIUtil.getLabelFont()
    text = "Enter credentials for $url"
    lineWrap = true
    wrapStyleWord = true
    isEditable = false
    isFocusable = false
    isOpaque = false
    border = null
    margin = JBUI.emptyInsets()
  }
  private val usernameField = JBTextField(username).apply { isEditable = editableUsername }
  private val passwordField = JBPasswordField()
  private val rememberCheckbox: JBCheckBox = JBCheckBox("Remember", rememberPassword)
  private val additionalProvidersButton: JBOptionButton = JBOptionButton(null, null).apply { isVisible = false }

  var externalAuthData: AuthData? = null
    private set

  init {
    title = "Git Login"
    setOKButtonText("Log In")
    init()
  }

  override fun createCenterPanel(): BorderLayoutPanel = JBUI.Panels
    .simplePanel(0, UIUtil.DEFAULT_VGAP)
    .addToCenter(grid()
                   .add(panel(usernameField).withLabel("Username:"))
                   .add(panel(passwordField).withLabel("Password:"))
                   .add(panel(rememberCheckbox))
                   .createPanel())
    .addToTop(descriptionLabel)

  override fun doValidateAll(): List<ValidationInfo> {
    return listOfNotNull(if (usernameField.text.isBlank()) ValidationInfo("Username cannot be empty", usernameField) else null,
                         if (passwordField.password.isEmpty()) ValidationInfo("Password cannot be empty", passwordField) else null)
  }

  override fun createSouthAdditionalPanel(): Wrapper = Wrapper(additionalProvidersButton)
    .apply { border = JBUI.Borders.emptyRight(UIUtil.DEFAULT_HGAP) }

  override fun getPreferredFocusedComponent(): JComponent = if (usernameField.text.isBlank()) usernameField else passwordField

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

  val username: String get() = usernameField.text
  val password: String get() = String(passwordField.password!!)
  val rememberPassword: Boolean get() = rememberCheckbox.isSelected
}

