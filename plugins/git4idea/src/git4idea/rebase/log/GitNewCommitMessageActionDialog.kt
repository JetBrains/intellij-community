// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingActionBase.Companion.findContainingBranches
import org.jetbrains.annotations.Nls

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
    val commits = commitEditingData.selectedCommitList
    if (repository.info.currentRevision != originalHEAD || Disposer.isDisposed(logData)) {
      return ValidationInfo(
        GitBundle.message("rebase.log.reword.dialog.failed.repository.changed.message", commits.size)
      )
    }
    val lastCommitHash = commits.last().id
    val branches = findContainingBranches(logData, repository.root, lastCommitHash)
    val protectedBranch = findProtectedRemoteBranch(repository, branches)
    if (protectedBranch != null) {
      return ValidationInfo(
        GitBundle.message("rebase.log.reword.dialog.failed.pushed.to.protected.message", commits.size, lastCommitHash, protectedBranch)
      )
    }
    return null
  }

  override fun createCenterPanel() =
    JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
      .addToTop(JBLabel(dialogLabel))
      .addToCenter(commitEditor)

  override fun getPreferredFocusedComponent() = commitEditor.editorField

  override fun getDimensionServiceKey() = "Git.Rebase.Log.Action.NewCommitMessage.Dialog"

  private fun createCommitEditor(): CommitMessage {
    val editor = CommitMessage(commitEditingData.project, false, false, true)
    editor.text = originMessage
    editor.editorField.setCaretPosition(0)
    editor.editorField.addSettingsProvider { editorEx ->
      // display at least several rows for one-line messages
      val MIN_ROWS = 3
      if ((editorEx as EditorImpl).visibleLineCount < MIN_ROWS) {
        verticalStretch = 1.5F
      }
    }
    return editor
  }

  override fun doValidate(): ValidationInfo? {
    return validate(commitEditingData, originalHEAD)
  }

  override fun doOKAction() {
    super.doOKAction()

    onOk(commitEditor.comment)
  }
}