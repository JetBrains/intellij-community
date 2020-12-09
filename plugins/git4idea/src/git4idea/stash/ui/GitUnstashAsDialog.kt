// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.ui.StashInfo
import git4idea.validators.validateName
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

internal class GitUnstashAsDialog(private val project: Project, val stashInfo: StashInfo) : DialogWrapper(project) {
  private val branchTextField = JBTextField()
  private val popStashCheckbox = JBCheckBox(GitBundle.message("unstash.pop.stash")).apply {
    toolTipText = GitBundle.message("unstash.pop.stash.tooltip")
  }
  private val keepIndexCheckbox = JBCheckBox(GitBundle.message("unstash.reinstate.index")).apply {
    toolTipText = GitBundle.message("unstash.reinstate.index.tooltip")
  }

  var popStash: Boolean = false
    private set
  var keepIndex: Boolean = false
    private set
  var branch: String = ""
    private set

  init {
    title = GitBundle.message("stash.unstash.changes.in.root.dialog.title", stashInfo.root.presentableName)
    init()

    branchTextField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        onBranchChanged()
      }
    })
    popStashCheckbox.addActionListener { onPopStashChanged() }
    updateOkButtonText()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(GitBundle.message("stash.unstash.changes.current.branch.label")) {
        label(CurrentBranchComponent.getCurrentBranch(project, stashInfo.root)?.let { CurrentBranchComponent.getPresentableText(it) } ?: "")
      }
      row(GitBundle.message("unstash.branch.label")) {
        branchTextField().withBinding(JBTextField::getText, JBTextField::setText,
                                      PropertyBinding({ branch }, { value -> branch = value})
        ).withValidationOnInput {
          if (it.text.isBlank()) return@withValidationOnInput null
          val repository = GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(stashInfo.root)
                           ?: return@withValidationOnInput null
          validateName(listOf(repository), it.text)
        }.focused()
      }
      row {
        popStashCheckbox().withBinding(
          JBCheckBox::isSelected, JBCheckBox::setSelected,
          PropertyBinding({ popStash }, { value -> popStash = value }),
        )
      }
      row {
        keepIndexCheckbox().withBinding(
          JBCheckBox::isSelected, JBCheckBox::setSelected,
          PropertyBinding({ keepIndex }, { value -> keepIndex = value }),
        )
      }
    }
  }

  private fun onBranchChanged() {
    updateEnabled()
    updateOkButtonText()
  }

  private fun onPopStashChanged() {
    updateOkButtonText()
  }

  private fun updateEnabled() {
    val hasBranch = branchTextField.text.isNotBlank()
    popStashCheckbox.isEnabled = !hasBranch
    keepIndexCheckbox.isEnabled = !hasBranch
  }

  private fun updateOkButtonText() {
    val buttonText = when {
      branchTextField.text.isNotBlank() -> {
        GitBundle.message("unstash.button.branch")
      }
      popStashCheckbox.isSelected -> GitBundle.message("unstash.button.pop")
      else -> GitBundle.message("unstash.button.apply")
    }
    setOKButtonText(buttonText)
  }
}