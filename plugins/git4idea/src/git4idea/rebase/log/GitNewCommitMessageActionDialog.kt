// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.asSafely
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.data.VcsLogData
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.rebase.GitSingleCommitEditingAction
import git4idea.rebase.log.GitCommitEditingActionBase.Companion.findContainingBranches
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import javax.swing.JComponent


internal class GitNewCommitMessageActionDialog(
  private val project: Project,
  private val originMessage: String,
  private val selectedChanges: List<Change>?,
  private val validateCommitEditable: () -> ValidationInfo?,
  @Nls title: String,
  @Nls private val dialogLabel: String,
) : DialogWrapper(project, true) {
  private val commitEditor = createCommitEditor()
  private var onOk: (String) -> Unit = {}
  private var repositoryValidationResult: ValidationInfo? = null

  constructor(
    commitEditingData: GitCommitEditingActionBase.MultipleCommitEditingData,
    originMessage: String,
    @Nls title: String,
    @Nls dialogLabel: String,
  ) : this(commitEditingData.project,
           originMessage,
           commitEditingData.selectedChanges,
           {
             validateCommitsEditable(
               commitEditingData.logData,
               commitEditingData.repository,
               commitEditingData.selection.commits.map { it.hash },
               commitEditingData.repository.info.currentRevision
             )
           },
           title,
           dialogLabel)

  init {
    Disposer.register(disposable, commitEditor)

    init()
    isModal = false
    this.title = title

    commitEditor.editorField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        updateOkButtonState()
      }
    })
    updateOkButtonState()
  }

  private fun updateOkButtonState() {
    if (repositoryValidationResult != null) {
      return
    }
    val isMessageEmpty = commitEditor.comment.isBlank()
    isOKActionEnabled = !isMessageEmpty
  }

  fun show(onOk: (newMessage: String) -> Unit) {
    this.onOk = onOk
    show()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      commitMessageWithLabelAndToolbar(commitEditor, dialogLabel)
    }
  }

  override fun getPreferredFocusedComponent() = commitEditor.editorField

  override fun getDimensionServiceKey() = "Git.Rebase.Log.Action.NewCommitMessage.Dialog"

  private fun createCommitEditor() =
    CommitMessage(project, false, false, true).apply {
      text = originMessage
      editorField.setCaretPosition(0)

      if (selectedChanges != null) {
        setChangesSupplier { selectedChanges }
      }
    }

  override fun doValidate(): ValidationInfo? {
    repositoryValidationResult = validateCommitEditable()
    return repositoryValidationResult
  }

  override fun doOKAction() {
    super.doOKAction()

    onOk(commitEditor.comment)
  }

  override fun dispose() {
    if (shouldUpdateCommitHistory()) {
      VcsConfiguration.getInstance(project).saveCommitMessage(commitEditor.comment)
    }

    super.dispose()
  }

  private fun shouldUpdateCommitHistory(): Boolean {
    return commitEditor.comment != originMessage
  }

  companion object {
    fun validateCommitsEditable(
      logData: VcsLogData,
      repository: GitRepository,
      commits: List<Hash>,
      originalHEAD: String?,
    ): ValidationInfo? {
      if (repository.info.currentRevision != originalHEAD || logData.isDisposed) {
        return ValidationInfo(
          GitBundle.message("rebase.log.reword.dialog.failed.repository.changed.message", commits.size)
        )
      }
      val lastCommitHash = commits.last()
      val branches = findContainingBranches(logData, repository.root, lastCommitHash)
      val protectedBranch = findProtectedRemoteBranch(repository, branches)
      if (protectedBranch != null) {
        return ValidationInfo(
          GitBundle.message("rebase.log.reword.dialog.failed.pushed.to.protected.message", commits.size, lastCommitHash, protectedBranch)
        )
      }
      return null
    }
  }
}

internal fun Panel.commitMessageWithLabelAndToolbar(commitMessage: CommitMessage, label: @NlsContexts.Label String) {
  row {
    label(label).also { it.component.labelFor = commitMessage.editorField }
      .resizableColumn()
      .align(AlignX.FILL)
    cell(commitMessage.createToolbar(true))
  }
  row {
    cell(commitMessage)
      .align(Align.FILL)
      .applyToComponent {
        minimumSize = JBUI.size(300, 60)
      }
  }.resizableRow()
}

private val GitCommitEditingActionBase.MultipleCommitEditingData.selectedChanges: List<Change>?
  get() = asSafely<GitSingleCommitEditingAction.SingleCommitEditingData>()?.selectedChanges?.takeIf { it.isNotEmpty() }

