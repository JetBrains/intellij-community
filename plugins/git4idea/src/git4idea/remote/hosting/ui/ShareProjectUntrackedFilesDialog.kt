// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

internal class ShareProjectUntrackedFilesDialog(
  private val myProject: Project,
  untrackedFiles: List<VirtualFile>
) : SelectFilesDialog(myProject, untrackedFiles, null, null, true, false), UiDataProvider {
  private var myCommitMessagePanel: CommitMessage? = null

  val commitMessage: String
    get() = myCommitMessagePanel!!.comment

  init {
    title = GitBundle.message("share.untracked.files.dialog.title")
    setOKButtonText(CommonBundle.getAddButtonText())
    setCancelButtonText(CommonBundle.getCancelButtonText())
    init()
  }

  override fun createNorthPanel(): JComponent? {
    return null
  }

  override fun createCenterPanel(): JComponent {
    val tree = super.createCenterPanel()

    val commitMessage = CommitMessage(myProject)
    Disposer.register(disposable, commitMessage)
    commitMessage.setCommitMessage("Initial commit")
    myCommitMessagePanel = commitMessage

    val splitter = Splitter(true)
    splitter.setHonorComponentsMinimumSize(true)
    splitter.firstComponent = tree
    splitter.secondComponent = myCommitMessagePanel
    splitter.proportion = 0.7f

    return splitter
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[VcsDataKeys.COMMIT_MESSAGE_CONTROL] = myCommitMessagePanel
  }

  override fun getDimensionServiceKey(): String = "Git.ShareProject.UntrackedFilesDialog"
}