// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.vcs.branch.BranchPresentation
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.ui.StashInfo
import git4idea.validators.validateName
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

internal class GitUnstashAsDialog(private val project: Project, private val stashInfo: StashInfo) : DialogWrapper(project) {
  private lateinit var branchTextField: JBTextField
  private lateinit var popStashCheckbox: JBCheckBox
  private lateinit var keepIndexCheckbox: JBCheckBox

  var popStash: Boolean = false
    private set
  var keepIndex: Boolean = false
    private set
  var branch: String = ""
    private set

  init {
    title = GitBundle.message("stash.unstash.changes.in.root.dialog.title", stashInfo.root.presentableName)
    init()

    updateOkButtonText()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(GitBundle.message("stash.unstash.changes.current.branch.label")) {
        label(CurrentBranchComponent.getCurrentBranch(project, stashInfo.root)?.let { BranchPresentation.getPresentableText(it) } ?: "")
      }
      row(GitBundle.message("unstash.branch.label")) {
        branchTextField = textField()
          .bindText(::branch)
          .validationOnInput {
            if (it.text.isBlank()) return@validationOnInput null
            val repository = GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(stashInfo.root)
                             ?: return@validationOnInput null
            validateName(listOf(repository), it.text)
          }
          .applyToComponent {
            document.addDocumentListener(object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                onBranchChanged()
              }
            })
          }
          .focused()
          .component
      }
      row {
        popStashCheckbox = checkBox(GitBundle.message("unstash.pop.stash"))
          .applyToComponent {
            toolTipText = GitBundle.message("unstash.pop.stash.tooltip")
            addActionListener { onPopStashChanged() }
          }
          .bindSelected(::popStash)
          .component
      }
      row {
        keepIndexCheckbox = checkBox(GitBundle.message("unstash.reinstate.index"))
          .applyToComponent { toolTipText = GitBundle.message("unstash.reinstate.index.tooltip") }
          .bindSelected(::popStash)
          .component
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
