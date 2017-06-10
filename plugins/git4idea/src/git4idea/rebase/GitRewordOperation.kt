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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.branch.GitRebaseParams
import git4idea.commands.Git
import git4idea.rebase.GitRebaseEntry.Action.pick
import git4idea.rebase.GitRebaseEntry.Action.reword
import git4idea.repo.GitRepository
import git4idea.reset.GitResetMode

class GitRewordOperation(private val repository: GitRepository,
                         private val commit: VcsCommitMetadata,
                         private val newMessage: String) {
  init {
    repository.update()
  }
  private val project = repository.project
  private val initialHeadPosition = repository.currentRevision!!

  fun execute() {
    val rebaseEditor = GitAutomaticRebaseEditor(project, commit.root,
                                                entriesEditor = { list -> injectRewordAction(list) },
                                                plainTextEditor = { editorText -> supplyNewMessage(editorText) })

    val params = GitRebaseParams.editCommits(commit.parents.first().asString(), rebaseEditor, true)
    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val spec = GitRebaseSpec.forNewRebase(project, params, listOf(repository), indicator)
    RewordProcess(spec).rebase()
  }

  internal fun undo() {
    val res = Git.getInstance().reset(repository, GitResetMode.KEEP, initialHeadPosition)
    if (!res.success()) {
      VcsNotifier.getInstance(project).notifyError("Undo Reword Failed", res.errorOutputAsHtmlString)
    }
  }

  private fun injectRewordAction(list: List<GitRebaseEntry>): List<GitRebaseEntry> {
    return list.map({ entry ->
      if (entry.action == pick && commit.id.asString().startsWith(entry.commit))
        GitRebaseEntry(reword, entry.commit, entry.subject)
      else entry
    })
  }

  private fun supplyNewMessage(editorText: String): String {
    if (editorText.startsWith(commit.fullMessage)) { // there are comments after the proposed message
      return newMessage
    }
    else {
      throw IllegalStateException("Unexpected editor content: $editorText")
    }
  }

  private inner class RewordProcess(spec: GitRebaseSpec) : GitRebaseProcess(project, spec, null) {
    override fun notifySuccess(successful: MutableMap<GitRepository, GitSuccessfulRebase>,
                               skippedCommits: MultiMap<GitRepository, GitRebaseUtils.CommitInfo>) {
      val notification = STANDARD_NOTIFICATION.createNotification("Reworded Successfully", "", NotificationType.INFORMATION, null)
      notification.addAction(object : NotificationAction("Undo") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notification.expire()
          undoInBackground()
        }
      })
      VcsNotifier.getInstance(project).notify(notification)
    }

    override fun shouldRefreshOnSuccess(successType: GitSuccessfulRebase.SuccessType) = false

    private fun undoInBackground() {
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Undoing Reword") {
        override fun run(indicator: ProgressIndicator) {
          undo()
        }
      })
    }
  }
}