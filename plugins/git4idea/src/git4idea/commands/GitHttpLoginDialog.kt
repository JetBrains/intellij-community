// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI.PanelFactory.grid
import com.intellij.util.ui.UI.PanelFactory.panel
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JTextArea


class GitHttpLoginDialog @JvmOverloads constructor(project: Project,
                                                   url: String,
                                                   allowRememberPassword: Boolean = true,
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
  private val rememberCheckbox: JBCheckBox = JBCheckBox("Remember", allowRememberPassword).apply { isVisible = allowRememberPassword }

  init {
    title = "Git Login"
    setOKButtonText("Log In")
    init()
  }

  override fun createCenterPanel() = JBUI.Panels
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

  override fun getPreferredFocusedComponent(): JComponent = if (usernameField.text.isBlank()) usernameField else passwordField

  val username: String get() = usernameField.text
  val password: String get() = String(passwordField.password!!)
  val rememberPassword: Boolean get() = rememberCheckbox.isSelected
}

