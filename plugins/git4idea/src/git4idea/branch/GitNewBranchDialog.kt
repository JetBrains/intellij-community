// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.layout.*
import com.intellij.util.ui.UIUtil.BR
import git4idea.branch.GitBranchOperationType.CHECKOUT
import git4idea.branch.GitBranchOperationType.CREATE
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.validators.checkRefName
import git4idea.validators.conflictsWithLocalBranch
import git4idea.validators.conflictsWithRemoteBranch
import org.jetbrains.annotations.Nls
import java.awt.event.KeyEvent
import javax.swing.JCheckBox
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

data class GitNewBranchOptions(val name: String,
                               @get:JvmName("shouldCheckout") val checkout: Boolean = true,
                               @get:JvmName("shouldReset") val reset: Boolean = false,
                               @get:JvmName("shouldSetTracking") val setTracking: Boolean = false)


enum class GitBranchOperationType(@Nls val text: String, @Nls val description: String = "") {
  CREATE(GitBundle.message("new.branch.dialog.operation.create.name"),
         GitBundle.message("new.branch.dialog.operation.create.description")),
  CHECKOUT(GitBundle.message("new.branch.dialog.operation.checkout.name"),
           GitBundle.message("new.branch.dialog.operation.checkout.description")),
  RENAME(GitBundle.message("new.branch.dialog.operation.rename.name"))
}

internal class GitNewBranchDialog @JvmOverloads constructor(project: Project,
                                                            private val repositories: Collection<GitRepository>,
                                                            dialogTitle: @NlsContexts.DialogTitle String,
                                                            initialName: String?,
                                                            private val showCheckOutOption: Boolean = true,
                                                            private val showResetOption: Boolean = false,
                                                            private val showSetTrackingOption: Boolean = false,
                                                            private val localConflictsAllowed: Boolean = false,
                                                            private val operation: GitBranchOperationType = if (showCheckOutOption) CREATE else CHECKOUT)
  : DialogWrapper(project, true) {

  private var checkout = true
  private var reset = false
  private var tracking = showSetTrackingOption
  private var branchName = initialName.orEmpty()
  private var overwriteCheckbox: JCheckBox? = null
  private var setTrackingCheckbox: JCheckBox? = null

  init {
    title = dialogTitle
    setOKButtonText(operation.text)
    init()
  }

  fun showAndGetOptions() = if (showAndGet()) GitNewBranchOptions(branchName.trim(), checkout, reset, tracking) else null

  override fun createCenterPanel() = panel {
    row {
      label(GitBundle.message("new.branch.dialog.branch.name"))
    }
    row {
      textField(::branchName, { branchName = it }).focused().withValidationOnApply(
        validateBranchName()).apply { startTrackingValidationIfNeeded() }
    }
    row {
      if (showCheckOutOption) {
        checkBox(GitBundle.message("new.branch.dialog.checkout.branch.checkbox"), ::checkout).component.apply {
          mnemonic = KeyEvent.VK_C
        }
      }
      if (showResetOption) {
        overwriteCheckbox = checkBox(GitBundle.message("new.branch.dialog.overwrite.existing.branch.checkbox"), ::reset).component.apply {
          mnemonic = KeyEvent.VK_R
          isEnabled = false
        }
      }
      if (showSetTrackingOption) {
        setTrackingCheckbox = checkBox(GitBundle.message("new.branch.dialog.set.tracking.branch.checkbox"), ::tracking).component.apply {
          mnemonic = KeyEvent.VK_T
        }
      }
    }
  }

  private fun validateBranchName(): ValidationInfoBuilder.(JTextField) -> ValidationInfo? = {
    val errorInfo = checkRefName(it.text) ?: conflictsWithRemoteBranch(repositories, it.text)
    if (errorInfo != null) error(errorInfo.message)
    else {
      val localBranchConflict = conflictsWithLocalBranch(repositories, it.text)
      overwriteCheckbox?.isEnabled = localBranchConflict != null

      if (localBranchConflict == null || overwriteCheckbox?.isSelected == true) null // no conflicts or ask to reset
      else if (localBranchConflict.warning && localConflictsAllowed) warning("${localBranchConflict.message}.$BR${operation.description}")
      else error(localBranchConflict.message +
                 if (showResetOption) ".$BR" + GitBundle.message("new.branch.dialog.overwrite.existing.branch.warning") else "")
    }
  }

  private fun CellBuilder<JTextField>.startTrackingValidationIfNeeded() {
    if (branchName.isEmpty()) {
      component.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          startTrackingValidation()
          component.document.removeDocumentListener(this)
        }
      })
    }
    else {
      startTrackingValidation()
    }
  }
}
