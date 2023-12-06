// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingActionBase.Companion.findContainingBranches
import org.jetbrains.annotations.Nls
import javax.swing.BorderFactory
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

  override fun createCenterPanel(): BorderLayoutPanel {
    return JBUI.Panels.simplePanel(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP)
      .addToTop(
        JBUI.Panels.simplePanel()
          .addToCenter(JBLabel(dialogLabel))
          .addToRight(createToolbar())
      )
      .addToCenter(commitEditor)
  }

  private fun createToolbar(): JComponent {
    val actionGroup = ActionManager.getInstance().getAction("Git.Reword.ToolbarActions") as ActionGroup
    val toolbar = ActionManager.getInstance().createActionToolbar("GitNewCommitMessageActionDialog", actionGroup, true)
    toolbar.setReservePlaceAutoPopupIcon(false)
    toolbar.getComponent().setBorder(BorderFactory.createEmptyBorder())
    toolbar.setTargetComponent(commitEditor)
    return toolbar.getComponent()
  }

  override fun getPreferredFocusedComponent() = commitEditor.editorField

  override fun getDimensionServiceKey() = "Git.Rebase.Log.Action.NewCommitMessage.Dialog"

  private fun createCommitEditor(): CommitMessage {
    val editor = object : CommitMessage(commitEditingData.project, false, false, true) {
      override fun getData(dataId: String): Any? {
        if (VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION.`is`(dataId)) return commitEditingData.selection
        if (VcsLogInternalDataKeys.LOG_DATA.`is`(dataId)) return commitEditingData.logData
        return super.getData(dataId)
      }
    }
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