// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubAccountCombobox
import org.jetbrains.plugins.github.i18n.GithubBundle
import javax.swing.JComponent
import javax.swing.JTextArea

class GithubCreateGistDialog(project: Project,
                             accounts: Set<GithubAccount>,
                             defaultAccount: GithubAccount?,
                             fileName: String?,
                             secret: Boolean,
                             openInBrowser: Boolean,
                             copyLink: Boolean) : DialogWrapper(project, true) {
  private val fileNameField: JBTextField? = if (fileName != null) JBTextField(fileName) else null
  private val descriptionField: JTextArea = JTextArea()
  private val secretCheckBox: JBCheckBox = JBCheckBox(GithubBundle.message("create.gist.dialog.secret"), secret)
  private val browserCheckBox: JBCheckBox = JBCheckBox(GithubBundle.message("create.gist.dialog.open.browser"), openInBrowser)
  private val copyLinkCheckBox: JBCheckBox = JBCheckBox(GithubBundle.message("create.gist.dialog.copy.url"), copyLink)
  private val accountSelector: GithubAccountCombobox = GithubAccountCombobox(accounts, defaultAccount, null)

  val fileName: String?
    get() = fileNameField?.text

  val description: String
    get() = descriptionField.text

  val isSecret: Boolean
    get() = secretCheckBox.isSelected

  val isOpenInBrowser: Boolean
    get() = browserCheckBox.isSelected

  val isCopyURL: Boolean
    get() = copyLinkCheckBox.isSelected

  val account: GithubAccount
    get() = accountSelector.selectedItem as GithubAccount

  init {
    title = GithubBundle.message("create.gist.dialog.title")
    init()
  }

  override fun createCenterPanel() = panel {
    fileNameField?.let {
      row(GithubBundle.message("create.gist.dialog.filename.field")) {
        it(pushX, growX)
      }
    }
    row(GithubBundle.message("create.gist.dialog.description.field")) {
      scrollPane(descriptionField)
    }
    row("") {
      cell {
        secretCheckBox()
        browserCheckBox()
        copyLinkCheckBox()
      }
    }
    if (accountSelector.isEnabled) {
      row(GithubBundle.message("create.gist.dialog.create.for.field")) {
        accountSelector(pushX, growX)
      }
    }
  }

  override fun getHelpId(): String? {
    return "github.create.gist.dialog"
  }

  override fun getDimensionServiceKey(): String? {
    return "Github.CreateGistDialog"
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return descriptionField
  }
}
