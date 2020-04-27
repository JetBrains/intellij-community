// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.DEFAULT_HGAP
import com.intellij.util.ui.UIUtil.DEFAULT_VGAP
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository

private val LOG: Logger = logger<GitRewordAction>()

class GitRewordAction : GitCommitEditingAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    prohibitRebaseDuringRebase(e, GitBundle.getString("rebase.log.action.operation.reword.name"), true)
  }

  override fun actionPerformedAfterChecks(e: AnActionEvent) {
    val commit = getSelectedCommit(e)
    val project = e.project!!
    val repository = getRepository(e)
    val details = getOrLoadDetails(project, getLogData(e), commit)

    RewordDialog(project, getLogData(e), details, repository).show()
  }

  private fun getOrLoadDetails(project: Project, data: VcsLogData, commit: VcsShortCommitDetails): VcsCommitMetadata {
    return commit as? VcsCommitMetadata
           ?: getCommitDataFromCache(data, commit)
           ?: loadCommitData(project, data, commit)
           ?: throw ProcessCanceledException()
  }

  override fun getFailureTitle(): String = GitBundle.getString("rebase.log.reword.action.failure.title")

  private fun getCommitDataFromCache(data: VcsLogData, commit: VcsShortCommitDetails): VcsCommitMetadata? {
    val commitIndex = data.getCommitIndex(commit.id, commit.root)
    val commitData = data.commitDetailsGetter.getCommitDataIfAvailable(commitIndex)
    if (commitData != null) return commitData

    val message = data.index.dataGetter?.getFullMessage(commitIndex)
    if (message != null) return VcsCommitMetadataImpl(commit.id, commit.parents, commit.commitTime, commit.root, commit.subject,
                                                      commit.author, message, commit.committer, commit.authorTime)
    return null
  }

  private fun loadCommitData(project: Project, data: VcsLogData, commit: VcsShortCommitDetails): VcsCommitMetadata? {
    var commitData: VcsCommitMetadata? = null
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        try {
          commitData = VcsLogUtil.getDetails(data, commit.root, commit.id)
        }
        catch (e: VcsException) {
          val error = GitBundle.message("rebase.log.reword.action.loading.commit.message.failed.message", commit.id.asString())
          LOG.warn(error, e)
          val notification = VcsNotifier.STANDARD_NOTIFICATION.createNotification(
            "",
            error,
            NotificationType.ERROR,
            null
          )
          VcsNotifier.getInstance(project).notify(notification)
        }
      }, GitBundle.getString("rebase.log.reword.action.progress.indicator.loading.commit.message.title"), true, project)
    return commitData
  }

  private fun rewordInBackground(project: Project, commit: VcsCommitMetadata, repository: GitRepository, newMessage: String) {
    object : Task.Backgroundable(project, GitBundle.getString("rebase.log.reword.action.progress.indicator.title")) {
      override fun run(indicator: ProgressIndicator) {
        GitRewordOperation(repository, commit, newMessage).execute()
      }
    }.queue()
  }

  private inner class RewordDialog(val project: Project, val data: VcsLogData, val commit: VcsCommitMetadata, val repository: GitRepository)
    : DialogWrapper(project, true) {

    val originalHEAD = repository.info.currentRevision
    val commitEditor = createCommitEditor()

    init {
      Disposer.register(disposable, commitEditor)

      init()
      isModal = false
      title = GitBundle.getString("rebase.log.reword.dialog.title")
    }

    override fun createCenterPanel() =
      JBUI.Panels.simplePanel(DEFAULT_HGAP, DEFAULT_VGAP)
        .addToTop(JBLabel(GitBundle.message(
          "rebase.log.reword.dialog.description.label",
          commit.id.toShortString(),
          getShortPresentation(commit.author)
        )))
        .addToCenter(commitEditor)

    override fun getPreferredFocusedComponent() = commitEditor.editorField

    override fun getDimensionServiceKey() = "GitRewordDialog"

    private fun createCommitEditor(): CommitMessage {
      val editor = CommitMessage(project, false, false, true)
      editor.setText(commit.fullMessage)
      editor.editorField.setCaretPosition(0)
      editor.editorField.addSettingsProvider { editor ->
        // display at least several rows for one-line messages
        val MIN_ROWS = 3
        if ((editor as EditorImpl).visibleLineCount < MIN_ROWS) {
          verticalStretch = 1.5F
        }
      }
      return editor
    }

    override fun doValidate(): ValidationInfo? {
      if (repository.info.currentRevision != originalHEAD || Disposer.isDisposed(data)) {
        return ValidationInfo(GitBundle.getString("rebase.log.reword.dialog.failed.repository.changed.message"))
      }

      val branches = findContainingBranches(data, commit.root, commit.id)
      val protectedBranch = findProtectedRemoteBranch(repository, branches)
      if (protectedBranch != null) {
        return ValidationInfo(GitBundle.message(
          "rebase.log.reword.dialog.failed.pushed.to.protected.message",
          commitPushedToProtectedBranchError(protectedBranch)
        ))
      }

      return null
    }

    override fun doOKAction() {
      super.doOKAction()

      rewordInBackground(project, commit, repository, commitEditor.comment)
    }
  }
}