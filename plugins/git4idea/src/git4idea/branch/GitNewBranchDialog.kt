/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.layout.*
import git4idea.branch.GitBranchOperationType.CHECKOUT
import git4idea.branch.GitBranchOperationType.CREATE
import git4idea.repo.GitRepository
import git4idea.validators.checkRefName
import git4idea.validators.conflictsWithLocalBranch
import git4idea.validators.conflictsWithRemoteBranch
import java.awt.event.KeyEvent
import javax.swing.JCheckBox
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

data class GitNewBranchOptions(val name: String,
                               @get:JvmName("shouldCheckout") val checkout: Boolean = true,
                               @get:JvmName("shouldReset") val reset: Boolean = false)


enum class GitBranchOperationType(val text: String, val description: String = "") {
  CREATE("Create", "Create new branches in other repositories."),
  CHECKOUT("Checkout", "Checkout existing branches, and create new branches in other repositories."),
  RENAME("Rename")
}

internal class GitNewBranchDialog @JvmOverloads constructor(project: Project,
                                                            private val repositories: Collection<GitRepository>,
                                                            dialogTitle: String,
                                                            initialName: String?,
                                                            private val showCheckOutOption: Boolean = true,
                                                            private val showResetOption: Boolean = false,
                                                            private val localConflictsAllowed: Boolean = false,
                                                            private val operation: GitBranchOperationType = if (showCheckOutOption) CREATE else CHECKOUT)
  : DialogWrapper(project, true) {

  private var checkout = true
  private var reset = false
  private var branchName = initialName.orEmpty()
  private var overwriteCheckbox: JCheckBox? = null

  init {
    title = dialogTitle
    setOKButtonText(operation.text)
    init()
  }

  fun showAndGetOptions() = if (showAndGet()) GitNewBranchOptions(branchName.trim(), checkout, reset) else null

  override fun createCenterPanel() = panel {
    row {
      label("New branch name:")
    }
    row {
      textField(::branchName, { branchName = it }).focused().withValidationOnApply(
        validateBranchName()).apply { startTrackingValidationIfNeeded() }
    }
    row {
      if (showCheckOutOption) {
        checkBox("Checkout branch", ::checkout).component.apply {
          mnemonic = KeyEvent.VK_C
        }
      }
      if (showResetOption) {
        overwriteCheckbox = checkBox("Overwrite existing branch", ::reset).component.apply {
          mnemonic = KeyEvent.VK_R
          isEnabled = false
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
      else if (localBranchConflict.warning && localConflictsAllowed) warning("${localBranchConflict.message}.<br/>${operation.description}")
      else error(localBranchConflict.message + if (showResetOption) ".<br/>Change the name or overwrite existing branch" else "")
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
