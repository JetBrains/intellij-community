// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.dialog.DialogUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.RecordUniqueValidator
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import java.awt.Component
import java.util.regex.Pattern


class GithubShareDialog(private val project: Project,
                        existingRemotes: Set<String>,
                        private val accountInformationSupplier: (GithubAccount, Component) -> Pair<Boolean, Set<String>>)
  : DialogWrapper(project) {

  private val GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+")

  private val repositoryTextField = JBTextField(project.name)
  private val privateCheckBox = JBCheckBox(message("share.dialog.private"), false)

  @NlsSafe
  private val remoteName = if (existingRemotes.isEmpty()) "origin" else "github"
  private val remoteTextField = JBTextField(remoteName)
  private val descriptionTextArea = JBTextArea().apply { lineWrap = true }
  private val existingRepoValidator = RecordUniqueValidator(repositoryTextField,
                                                            message("share.error.repo.with.selected.name.exists"))
  private val existingRemoteValidator = RecordUniqueValidator(remoteTextField,
                                                              message("share.error.remote.with.selected.name.exists"))
    .apply { records = existingRemotes }
  private var accountInformationLoadingError: ValidationInfo? = null

  private val accounts = GHAccountsUtil.accounts

  private val accountsModel = CollectionComboBoxModel(
    accounts.toMutableList(),
    GHAccountsUtil.getDefaultAccount(project) ?: accounts.firstOrNull()
  )

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
      cell(repositoryTextField)
        .align(AlignX.FILL)
        .validationOnApply { validateRepository() }
        .resizableColumn()
      cell(privateCheckBox)
    }
    row(message("share.dialog.remote")) {
      cell(remoteTextField)
        .align(AlignX.FILL)
        .validationOnApply { validateRemote() }
    }
    row {
      label(message("share.dialog.description"))
        .align(AlignY.TOP)
      scrollCell(descriptionTextArea)
        .align(Align.FILL)
    }.layout(RowLayout.LABEL_ALIGNED).resizableRow()

    if (accountsModel.size != 1) {
      row(message("share.dialog.share.by")) {
        comboBox(accountsModel)
          .align(AlignX.FILL)
          .validationOnApply { if (accountsModel.selected == null) error(message("dialog.message.account.cannot.be.empty")) else null }
          .applyToComponent { addActionListener { switchAccount(getAccount()) } }
          .resizableColumn()

        if (accountsModel.size == 0) {
          cell(GHAccountsUtil.createAddAccountLink(project, accountsModel))
        }
      }
    }
  }.apply {
    preferredSize = JBUI.size(500, 250)
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
