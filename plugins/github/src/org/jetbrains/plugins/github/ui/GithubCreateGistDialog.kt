// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GHAccountsComboBoxModel
import org.jetbrains.plugins.github.authentication.ui.GHAccountsComboBoxModel.Companion.accountSelector
import org.jetbrains.plugins.github.authentication.ui.GHAccountsHost
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import javax.swing.JComponent
import javax.swing.JTextArea

class GithubCreateGistDialog(
  project: Project,
  accounts: Set<GithubAccount>,
  defaultAccount: GithubAccount?,
  @NlsSafe fileName: String?,
  secret: Boolean,
  openInBrowser: Boolean,
  copyLink: Boolean
) : DialogWrapper(project, true),
    DataProvider {

  private val fileNameField = if (fileName != null) JBTextField(fileName) else null
  private val descriptionField = JTextArea()
  private val secretCheckBox = JBCheckBox(message("create.gist.dialog.secret"), secret)
  private val browserCheckBox = JBCheckBox(message("create.gist.dialog.open.browser"), openInBrowser)
  private val copyLinkCheckBox = JBCheckBox(message("create.gist.dialog.copy.url"), copyLink)

  private val accountsModel = GHAccountsComboBoxModel(accounts, defaultAccount ?: accounts.firstOrNull())

  val fileName: String? get() = fileNameField?.text
  val description: String get() = descriptionField.text
  val isSecret: Boolean get() = secretCheckBox.isSelected
  val isOpenInBrowser: Boolean get() = browserCheckBox.isSelected
  val isCopyURL: Boolean get() = copyLinkCheckBox.isSelected
  val account: GithubAccount? get() = accountsModel.selected

  init {
    title = message("create.gist.dialog.title")
    init()
  }

  override fun createCenterPanel() = panel {
    fileNameField?.let {
      row(message("create.gist.dialog.filename.field")) {
        it(pushX, growX)
      }
    }
    row(message("create.gist.dialog.description.field")) {
      scrollPane(descriptionField)
    }
    row("") {
      cell {
        secretCheckBox()
        browserCheckBox()
        copyLinkCheckBox()
      }
    }
    if (accountsModel.size != 1) {
      row(message("create.gist.dialog.create.for.field")) {
        accountSelector(accountsModel)
      }
    }
  }

  override fun getHelpId(): String = "github.create.gist.dialog"
  override fun getDimensionServiceKey(): String = "Github.CreateGistDialog"
  override fun getPreferredFocusedComponent(): JComponent = descriptionField

  override fun getData(dataId: String): Any? =
    if (GHAccountsHost.KEY.`is`(dataId)) accountsModel
    else null
}
