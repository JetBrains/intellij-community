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
package git4idea.rebase

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
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
import com.intellij.vcs.log.impl.VcsLogUtil
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.repo.GitRepository

class GitRewordAction : GitCommitEditingAction() {
  val LOG = logger<GitRewordAction>()

  override fun update(e: AnActionEvent) {
    super.update(e)
    prohibitRebaseDuringRebase(e, "reword", true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    super.actionPerformed(e)

    val commit = getSelectedCommit(e)
    val project = e.project!!
    val repository = getRepository(e)
    val details = getOrLoadDetails(project, getLogData(e), commit)

    val dialog = RewordDialog(project, details)
    if (dialog.showAndGet()) {
      rewordInBackground(project, details, repository, dialog.getMessage())
    }
  }

  private fun getOrLoadDetails(project: Project, data: VcsLogData, commit: VcsShortCommitDetails): VcsCommitMetadata {
    return commit as? VcsCommitMetadata
           ?: getCommitDataFromCache(data, commit)
           ?: loadCommitData(project, data, commit)
           ?: throw ProcessCanceledException()
  }

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
    ProgressManager.getInstance().runProcessWithProgressSynchronously({
      try {
        commitData = VcsLogUtil.getDetails(data, commit.root, commit.id)
      }
      catch(e: VcsException) {
        val error = "Couldn't load changes of " + commit.id.asString()
        LOG.warn(error, e)
        val notification = VcsNotifier.STANDARD_NOTIFICATION.createNotification("", error, NotificationType.ERROR, null)
        VcsNotifier.getInstance(project).notify(notification)
      }
    }, "Loading Commit Message", true, project)
    return commitData
  }

  private fun rewordInBackground(project: Project, commit: VcsCommitMetadata, repository: GitRepository, newMessage: String) {
    object : Task.Backgroundable(project, "Rewording") {
      override fun run(indicator: ProgressIndicator) {
        GitRewordOperation(repository, commit, newMessage).execute()
      }
    }.queue()
  }

  override fun getFailureTitle() = "Couldn't Reword Commit"

  private class RewordDialog(val project: Project, val commit: VcsCommitMetadata) : DialogWrapper(project, true) {

    val commitEditor = createCommitEditor()

    init {
      init()
      title = "Reword Commit"
    }

    override fun createCenterPanel() =
      JBUI.Panels.simplePanel(DEFAULT_HGAP, DEFAULT_VGAP).
        addToTop(JBLabel("Edit message for commit ${commit.id.toShortString()} by ${getShortPresentation(commit.author)}")).
        addToCenter(commitEditor)

    override fun getPreferredFocusedComponent() = commitEditor.editorField

    override fun getDimensionServiceKey() = "GitRewordDialog"

    fun getMessage() = commitEditor.comment

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
  }
}