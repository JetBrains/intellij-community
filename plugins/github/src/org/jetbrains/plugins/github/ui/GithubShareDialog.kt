// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.util.ui.dialog.DialogUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GHAccountsComboBoxModel
import org.jetbrains.plugins.github.authentication.ui.GHAccountsComboBoxModel.Companion.accountSelector
import org.jetbrains.plugins.github.authentication.ui.GHAccountsHost
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.RecordUniqueValidator
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import java.awt.Component
import java.util.regex.Pattern
import javax.swing.JTextArea


class GithubShareDialog(project: Project,
                        accounts: Set<GithubAccount>,
                        defaultAccount: GithubAccount?,
                        existingRemotes: Set<String>,
                        private val accountInformationSupplier: (GithubAccount, Component) -> Pair<Boolean, Set<String>>)
  : DialogWrapper(project), DataProvider {

  private val GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+")

  private val repositoryTextField = JBTextField(project.name)
  private val privateCheckBox = JBCheckBox(message("share.dialog.private"), false)

  @NlsSafe
  private val remoteName = if (existingRemotes.isEmpty()) "origin" else "github"
  private val remoteTextField = JBTextField(remoteName)
  private val descriptionTextArea = JTextArea()
  private val existingRepoValidator = RecordUniqueValidator(repositoryTextField,
                                                            message("share.error.repo.with.selected.name.exists"))
  private val existingRemoteValidator = RecordUniqueValidator(remoteTextField,
                                                              message("share.error.remote.with.selected.name.exists"))
    .apply { records = existingRemotes }
  private var accountInformationLoadingError: ValidationInfo? = null

  private val accountsModel = GHAccountsComboBoxModel(accounts, defaultAccount ?: accounts.firstOrNull())

  init {
    title = message("share.on.github")
    setOKButtonText(message("share.button"))
    init()
    DialogUtils.invokeLaterAfterDialogShown(this) { switchAccount(getAccount()) }
  }

  private fun switchAccount(account: GithubAccount?) {
    if (account == null) return

    try {
      accountInformationLoadingError = null
      accountInformationSupplier(account, window).let {
        privateCheckBox.isEnabled = it.first
        if (!it.first) privateCheckBox.toolTipText = message("share.error.private.repos.not.supported")
        else privateCheckBox.toolTipText = null
        existingRepoValidator.records = it.second
      }
    }
    catch (e: Exception) {
      val errorText = message("share.dialog.account.info.load.error.prefix", account) +
                      if (e is ProcessCanceledException) message("share.dialog.account.info.load.process.canceled")
                      else e.message
      accountInformationLoadingError = ValidationInfo(errorText)
      privateCheckBox.isEnabled = false
      privateCheckBox.toolTipText = null
      existingRepoValidator.records = emptySet()
      startTrackingValidation()
    }
  }

  override fun createCenterPanel() = panel {
    row(message("share.dialog.repo.name")) {
      cell {
        repositoryTextField(growX, pushX).withValidationOnApply { validateRepository() }
        privateCheckBox()
      }
    }
    row(message("share.dialog.remote")) {
      remoteTextField(growX, pushX).withValidationOnApply { validateRemote() }
    }
    row(message("share.dialog.description")) {
      scrollPane(descriptionTextArea)
    }
    if (accountsModel.size != 1) {
      row(message("share.dialog.share.by")) {
        accountSelector(accountsModel) { switchAccount(getAccount()) }
      }
    }
  }

  override fun doValidateAll(): List<ValidationInfo> {
    val uiErrors = super.doValidateAll()
    val loadingError = accountInformationLoadingError

    return if (loadingError != null) uiErrors + loadingError else uiErrors
  }

  private fun validateRepository(): ValidationInfo? =
    notBlank(repositoryTextField, message("share.validation.no.repo.name"))
    ?: validateRepositoryName()
    ?: existingRepoValidator()

  private fun validateRepositoryName(): ValidationInfo? =
    if (GITHUB_REPO_PATTERN.matcher(repositoryTextField.text).matches()) null
    else ValidationInfo(message("share.validation.invalid.repo.name"), repositoryTextField)

  private fun validateRemote(): ValidationInfo? =
    notBlank(remoteTextField, message("share.validation.no.remote.name"))
    ?: existingRemoteValidator()

  override fun getHelpId(): String = "github.share"
  override fun getDimensionServiceKey(): String = "Github.ShareDialog"
  override fun getPreferredFocusedComponent(): JBTextField = repositoryTextField

  override fun getData(dataId: String): Any? =
    if (GHAccountsHost.KEY.`is`(dataId)) accountsModel
    else null

  @NlsSafe
  fun getRepositoryName(): String = repositoryTextField.text

  @NlsSafe
  fun getRemoteName(): String = remoteTextField.text
  fun isPrivate(): Boolean = privateCheckBox.isSelected

  @NlsSafe
  fun getDescription(): String = descriptionTextArea.text
  fun getAccount(): GithubAccount? = accountsModel.selected

  @TestOnly
  fun testSetRepositoryName(name: String) {
    repositoryTextField.text = name
  }
}
