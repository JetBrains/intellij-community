// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import com.intellij.util.ui.dialog.DialogUtils
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubAccountCombobox
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.RecordUniqueValidator
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.chain
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank
import org.jetbrains.plugins.github.ui.util.Validator
import java.awt.Component
import java.util.regex.Pattern
import javax.swing.JTextArea


class GithubShareDialog(project: Project,
                        accounts: Set<GithubAccount>,
                        defaultAccount: GithubAccount?,
                        existingRemotes: Set<String>,
                        private val accountInformationSupplier: (GithubAccount, Component) -> Pair<Boolean, Set<String>>)
  : DialogWrapper(project) {

  private val GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+")

  private val repositoryTextField = JBTextField(project.name)
  private val privateCheckBox = JBCheckBox(GithubBundle.message("share.dialog.private"), false)
  private val remoteTextField = JBTextField(if (existingRemotes.isEmpty()) "origin" else "github")
  private val descriptionTextArea = JTextArea()
  private val accountSelector = GithubAccountCombobox(accounts, defaultAccount) { switchAccount(it) }
  private val existingRepoValidator = RecordUniqueValidator(repositoryTextField,
                                                            GithubBundle.message("share.error.repo.with.selected.name.exists"))
  private val existingRemoteValidator = RecordUniqueValidator(remoteTextField,
                                                              GithubBundle.message("share.error.remote.with.selected.name.exists"))
    .apply { records = existingRemotes }
  private var accountInformationLoadingError: ValidationInfo? = null

  init {
    title = GithubBundle.message("share.on.github")
    setOKButtonText(GithubBundle.message("share.button"))
    init()
    DialogUtils.invokeLaterAfterDialogShown(this) { switchAccount(accountSelector.selectedItem as GithubAccount) }
  }

  private fun switchAccount(account: GithubAccount) {
    try {
      accountInformationLoadingError = null
      accountInformationSupplier(account, window).let {
        privateCheckBox.isEnabled = it.first
        if (!it.first) privateCheckBox.toolTipText = GithubBundle.message("share.error.private.repos.not.supported")
        else privateCheckBox.toolTipText = null
        existingRepoValidator.records = it.second
      }
    }
    catch (e: Exception) {
      val errorText = GithubBundle.message("share.dialog.account.info.load.error.prefix", account) +
                      if (e is ProcessCanceledException) GithubBundle.message("share.dialog.account.info.load.process.canceled")
                      else e.message
      accountInformationLoadingError = ValidationInfo(errorText)
      privateCheckBox.isEnabled = false
      privateCheckBox.toolTipText = null
      existingRepoValidator.records = emptySet()
      startTrackingValidation()
    }
  }

  override fun createCenterPanel() = panel {
    row(GithubBundle.message("share.dialog.repo.name")) {
      cell {
        repositoryTextField(growX, pushX)
        privateCheckBox()
      }
    }
    row(GithubBundle.message("share.dialog.remote")) {
      remoteTextField(growX, pushX)
    }
    row(GithubBundle.message("share.dialog.description")) {
      scrollPane(descriptionTextArea)
    }
    if (accountSelector.isEnabled) {
      row(GithubBundle.message("share.dialog.share.by")) {
        accountSelector(growX, pushX)
      }
    }
  }

  override fun doValidateAll(): List<ValidationInfo> {
    val repositoryNamePatternMatchValidator: Validator = {
      if (!GITHUB_REPO_PATTERN.matcher(repositoryTextField.text).matches()) ValidationInfo(
        GithubBundle.message("share.validation.invalid.repo.name"),
        repositoryTextField)
      else null
    }

    return listOf({ accountInformationLoadingError },
                  chain({ notBlank(repositoryTextField, GithubBundle.message("share.validation.no.repo.name")) },
                        repositoryNamePatternMatchValidator,
                        existingRepoValidator),
                  chain({ notBlank(remoteTextField, GithubBundle.message("share.validation.no.remote.name")) },
                        existingRemoteValidator)
    ).mapNotNull { it() }
  }

  override fun getHelpId(): String = "github.share"
  override fun getDimensionServiceKey(): String = "Github.ShareDialog"
  override fun getPreferredFocusedComponent(): JBTextField = repositoryTextField

  fun getRepositoryName(): String = repositoryTextField.text
  fun getRemoteName(): String = remoteTextField.text
  fun isPrivate(): Boolean = privateCheckBox.isSelected
  fun getDescription(): String = descriptionTextArea.text
  fun getAccount(): GithubAccount = accountSelector.selectedItem as GithubAccount

  @TestOnly
  fun testSetRepositoryName(name: String) {
    repositoryTextField.text = name
  }
}
