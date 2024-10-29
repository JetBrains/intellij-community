// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingActionBase.Companion.findContainingBranches
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class GitNewCommitMessageActionDialog<T : GitCommitEditingActionBase.MultipleCommitEditingData>(
  private val commitEditingData: T,
  private val originMessage: String,
  @Nls title: String,
  @Nls private val dialogLabel: String
) : DialogWrapper(commitEditingData.project, true) {
  private val originalHEAD = commitEditingData.repository.info.currentRevision
  private val commitEditor = createCommitEditor()
  private var onOk: (String) -> Unit = {}

  init {
    Disposer.register(disposable, commitEditor)

    init()
    isModal = false
    this.title = title
  }

  fun show(onOk: (newMessage: String) -> Unit) {
    this.onOk = onOk
    show()
  }

  private fun validate(commitEditingData: T, originalHEAD: String?): ValidationInfo? {
    val logData = commitEditingData.logData
    val repository = commitEditingData.repository
    val commits = commitEditingData.selection.commits
    if (repository.info.currentRevision != originalHEAD || Disposer.isDisposed(logData)) {
      return ValidationInfo(
        GitBundle.message("rebase.log.reword.dialog.failed.repository.changed.message", commits.size)
      )
    }
    val lastCommitHash = commits.last().hash
    val branches = findContainingBranches(logData, repository.root, lastCommitHash)
    val protectedBranch = findProtectedRemoteBranch(repository, branches)
    if (protectedBranch != null) {
      return ValidationInfo(
        GitBundle.message("rebase.log.reword.dialog.failed.pushed.to.protected.message", commits.size, lastCommitHash, protectedBranch)
      )
    }
    return null
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      commitMessageWithLabelAndToolbar(commitEditor, dialogLabel)
    }.also {
      // Temporary workaround for IDEA-302779
      it.minimumSize = JBUI.size(400, 120)
    }
  }

  override fun getPreferredFocusedComponent() = commitEditor.editorField

  override fun getDimensionServiceKey() = "Git.Rebase.Log.Action.NewCommitMessage.Dialog"

  private fun createCommitEditor(): CommitMessage {
    val editor = object : CommitMessage(commitEditingData.project, false, false, true) {
      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION] = commitEditingData.selection
        sink[VcsLogInternalDataKeys.LOG_DATA] = commitEditingData.logData
      }
    }
    editor.text = originMessage
    editor.editorField.setCaretPosition(0)
    return editor
  }

  override fun doValidate(): ValidationInfo? {
    return validate(commitEditingData, originalHEAD)
  }

  override fun doOKAction() {
    super.doOKAction()

    onOk(commitEditor.comment)
  }

  override fun dispose() {
    if (shouldUpdateCommitHistory()) {
      VcsConfiguration.getInstance(commitEditingData.project).saveCommitMessage(commitEditor.comment)
    }

    super.dispose()
  }

  private fun shouldUpdateCommitHistory(): Boolean {
    return commitEditor.comment != originMessage
  }

}

internal fun Panel.commitMessageWithLabelAndToolbar(commitMessage: CommitMessage, label: @NlsContexts.Label String) {
  row {
    label(label).also { it.component.labelFor = commitMessage.editorField }
      .resizableColumn()
      .align(Align.FILL)
    cell(commitMessage.createToolbar(true))
  }
  row {
    cell(commitMessage)
      .resizableColumn()
      .align(Align.FILL)
  }.resizableRow()
}