/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UI.PanelFactory.grid
import com.intellij.util.ui.UI.PanelFactory.panel
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubAccountCombobox
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JTextArea

class GithubCreateGistDialog(project: Project,
                             accounts: Set<GithubAccount>,
                             defaultAccount: GithubAccount?,
                             fileName: String?,
                             secret: Boolean,
                             openInBrowser: Boolean,
                             copyLink: Boolean) : DialogWrapper(project, true) {
  private val myFileNameField: JBTextField? = if (fileName != null) JBTextField(fileName) else null
  private val myDescriptionField: JTextArea = JTextArea()
  private val mySecretCheckBox: JBCheckBox = JBCheckBox("Secret", secret)
  private val myOpenInBrowserCheckBox: JBCheckBox = JBCheckBox("Open in browser", openInBrowser)
  private val myCopyLinkCheckBox: JBCheckBox = JBCheckBox("Copy URL", copyLink)
  private val myAccountSelector: GithubAccountCombobox = GithubAccountCombobox(accounts, defaultAccount, null)

  val fileName: String?
    get() = myFileNameField?.text

  val description: String
    get() = myDescriptionField.text

  val isSecret: Boolean
    get() = mySecretCheckBox.isSelected

  val isOpenInBrowser: Boolean
    get() = myOpenInBrowserCheckBox.isSelected

  val isCopyURL: Boolean
    get() = myCopyLinkCheckBox.isSelected

  val account: GithubAccount
    get() = myAccountSelector.selectedItem as GithubAccount

  init {

    title = "Create Gist"
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val checkBoxes = JBBox.createHorizontalBox()
    checkBoxes.add(mySecretCheckBox)
    checkBoxes.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)))
    checkBoxes.add(myOpenInBrowserCheckBox)
    checkBoxes.add(Box.createRigidArea(JBUI.size(UIUtil.DEFAULT_HGAP, 0)))
    checkBoxes.add(myCopyLinkCheckBox)

    val descriptionPane = JBScrollPane(myDescriptionField)
    descriptionPane.preferredSize = JBDimension(270, 55)
    descriptionPane.minimumSize = JBDimension(270, 55)

    val grid = grid().resize()
    if (myFileNameField != null) grid.add(panel(myFileNameField).withLabel("Filename:"))
    grid.add(panel(descriptionPane).withLabel("Description:").anchorLabelOn(UI.Anchor.Top).resizeY(true)).add(panel(checkBoxes))
    if (myAccountSelector.isEnabled) grid.add(panel(myAccountSelector).withLabel("Create for:").resizeX(false))
    return grid.createPanel()
  }

  override fun getHelpId(): String? {
    return "github.create.gist.dialog"
  }

  override fun getDimensionServiceKey(): String? {
    return "Github.CreateGistDialog"
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return myDescriptionField
  }
}
