/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.checkin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.SystemProperties
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.*
import com.intellij.vcs.log.VcsUser
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

internal class GitUserNameNotDefinedDialog(
  project: Project,
  private val myRootsWithUndefinedProps: Collection<VirtualFile>,
  private val myAllRootsAffectedByCommit: Collection<VirtualFile>,
  rootsWithDefinedProps: Map<VirtualFile, VcsUser>)
  : DialogWrapper(project, false) {

  private val myProposedValues: VcsUser?
  private val mySettings: GitVcsSettings

  private lateinit var myNameTextField: JTextField
  private lateinit var myEmailTextField: JTextField
  private lateinit var myGlobalCheckbox: JBCheckBox

  init {
    mySettings = GitVcsSettings.getInstance(project)

    myProposedValues = calcProposedValues(rootsWithDefinedProps)

    title = GitBundle.message("title.user.name.email.not.specified")
    setOKButtonText(GitBundle.message("button.set.name.and.commit"))

    init()
  }

  override fun doValidate(): ValidationInfo? {
    val message = GitBundle.message("validation.warning.set.name.email.for.git")
    if (isEmptyOrSpaces(userName)) {
      return ValidationInfo(message, myNameTextField)
    }
    val email = userEmail
    if (isEmptyOrSpaces(email)) {
      return ValidationInfo(message, myEmailTextField)
    }
    if (!email.contains("@")) {
      return ValidationInfo(GitBundle.message("validation.error.email.no.at"), myEmailTextField)
    }
    return null
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return myNameTextField
  }

  private fun calcProposedValues(rootsWithDefinedProps: Map<VirtualFile, VcsUser>): VcsUser? {
    if (rootsWithDefinedProps.isEmpty()) {
      return null
    }
    val iterator = rootsWithDefinedProps.entries.iterator()
    val firstValue = iterator.next().value
    while (iterator.hasNext()) {
      // nothing to propose if there are different values set in different repositories
      if (firstValue != iterator.next().value) {
        return null
      }
    }
    return firstValue
  }

  override fun createCenterPanel(): JComponent {

    val icon = JLabel(getWarningIcon(), SwingConstants.LEFT)
    val description = JLabel(getMessageText())

    myNameTextField = JTextField(20)
    val nameLabel = JBLabel(GitBundle.message("label.user.name") + " ")
    nameLabel.labelFor = myNameTextField

    myEmailTextField = JTextField(20)
    val emailLabel = JBLabel(GitBundle.message("label.user.email") + " ")
    emailLabel.labelFor = myEmailTextField

    if (myProposedValues != null) {
      myNameTextField.text = myProposedValues.name
      myEmailTextField.text = myProposedValues.email
    }
    else {
      myNameTextField.text = SystemProperties.getUserName()
    }

    myGlobalCheckbox = JBCheckBox(GitBundle.message("checkbox.set.config.property.globally"), mySettings.shouldSetUserNameGlobally())

    val rootPanel = JPanel(GridBagLayout())
    val g = GridBag()
      .setDefaultInsets(JBUI.insets(0, 0, DEFAULT_VGAP, DEFAULT_HGAP))
      .setDefaultAnchor(GridBagConstraints.LINE_START)
      .setDefaultFill(GridBagConstraints.HORIZONTAL)

    rootPanel.add(description, g.nextLine().next().coverLine(3).pady(DEFAULT_HGAP))
    rootPanel.add(icon, g.nextLine().next().coverColumn(3))
    rootPanel.add(nameLabel, g.next().fillCellNone().insets(JBUI.insets(0, 6, DEFAULT_VGAP, DEFAULT_HGAP)))
    rootPanel.add(myNameTextField, g.next())
    rootPanel.add(emailLabel, g.nextLine().next().next().fillCellNone().insets(JBUI.insets(0, 6, DEFAULT_VGAP, DEFAULT_HGAP)))
    rootPanel.add(myEmailTextField, g.next())
    rootPanel.add(myGlobalCheckbox, g.nextLine().next().next().coverLine(2))

    return rootPanel
  }

  override fun createNorthPanel(): JComponent? {
    return null
  }

  private fun getMessageText(): @NlsContexts.Label String {
    if (myAllRootsAffectedByCommit.size == myRootsWithUndefinedProps.size) {
      return ""
    }
    val sb = HtmlBuilder()
      .append(GitBundle.message("label.name.email.not.defined.in.n.roots", myRootsWithUndefinedProps.size))
    for (root in myRootsWithUndefinedProps) {
      sb.br().append(root.presentableUrl)
    }
    return sb.wrapWithHtmlBody().toString()
  }

  val userName: String
    get() = myNameTextField.text

  val userEmail: String
    get() = myEmailTextField.text

  val isGlobal: Boolean
    get() = myGlobalCheckbox.isSelected
}