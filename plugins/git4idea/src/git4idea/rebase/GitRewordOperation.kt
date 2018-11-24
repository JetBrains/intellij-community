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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitRebaseParams
import git4idea.commands.Git
import git4idea.config.GitConfigUtil
import git4idea.history.GitLogUtil
import git4idea.rebase.GitRebaseEntry.Action.pick
import git4idea.rebase.GitRebaseEntry.Action.reword
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.reset.GitResetMode

class GitRewordOperation(private val repository: GitRepository,
                         private val commit: VcsCommitMetadata,
                         private val newMessage: String) {
  init {
    repository.update()
  }

  private val LOG = logger<GitRewordOperation>()
  private val project = repository.project
  private val notifier = VcsNotifier.getInstance(project)

  private val initialHeadPosition = repository.currentRevision!!
  private var headAfterReword: String? = null
  private var rewordedCommit: Hash? = null

  fun execute() {
    val rebaseEditor = GitAutomaticRebaseEditor(project, commit.root,
                                                entriesEditor = { list -> injectRewordAction(list) },
                                                plainTextEditor = { editorText -> supplyNewMessage(editorText) })

    val params = GitRebaseParams.editCommits(commit.parents.first().asString(), rebaseEditor, true)
    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val spec = GitRebaseSpec.forNewRebase(project, params, listOf(repository), indicator)
    RewordProcess(spec).rebase()
    headAfterReword = repository.currentRevision
    rewordedCommit = findNewHashOfRewordedCommit(headAfterReword!!)
  }

  internal fun undo() {
    val possibility = checkUndoPossibility(project)
    val errorTitle = "Can't Undo Reword"
    when (possibility) {
      is UndoPossibility.HeadMoved -> notifier.notifyError(errorTitle, "Repository has already been changed")
      is UndoPossibility.PushedToProtectedBranch ->
        notifier.notifyError(errorTitle, "Commit has already been pushed to ${possibility.branch}")
      is UndoPossibility.Error -> notifier.notifyError(errorTitle, "")
      else -> doUndo()
    }
  }

  private fun doUndo() {
    val res = Git.getInstance().reset(repository, GitResetMode.KEEP, initialHeadPosition)
    repository.update()
    if (!res.success()) {
      notifier.notifyError("Undo Reword Failed", res.errorOutputAsHtmlString)
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
      LOG.error("Unexpected editor content. Charset: ${GitConfigUtil.getCommitEncoding(project, commit.root)}",
                Attachment("actual.txt", editorText), Attachment("expected.txt", commit.fullMessage))
      throw IllegalStateException("Unexpected editor content")
    }
  }

  private fun findNewHashOfRewordedCommit(newHead: String): Hash? {
    val newCommitsRange = "${commit.parents.first().asString()}..$newHead"
    val newCommits = GitLogUtil.collectMetadata(project, repository.root, newCommitsRange).commits
    if (newCommits.isEmpty()) {
      LOG.error("Couldn't find commits after reword in range $newCommitsRange")
      return null
    }
    val newCommit = newCommits.last()
    if (!StringUtil.equalsIgnoreWhitespaces(newCommit.fullMessage, newMessage)) {
      LOG.error("Couldn't find the reworded commit. Expected message: \n[$newMessage]\nActual message: \n[${newCommit.fullMessage}]")
      return null
    }
    return newCommit.id
  }

  private fun checkUndoPossibility(project: Project): UndoPossibility {
    repository.update()
    if (repository.currentRevision != headAfterReword) {
      return UndoPossibility.HeadMoved
    }

    if (rewordedCommit == null) {
      LOG.error("Couldn't find the reworded commit")
      return UndoPossibility.Error
    }
    val containingBranches = GitBranchUtil.getBranches(project, repository.root, false, true, rewordedCommit!!.asString())
    val protectedBranch = findProtectedRemoteBranch(repository, containingBranches)
    if (protectedBranch != null) return UndoPossibility.PushedToProtectedBranch(protectedBranch)
    return UndoPossibility.Possible
  }

  private sealed class UndoPossibility {
    object Possible : UndoPossibility()
    object HeadMoved : UndoPossibility()
    class PushedToProtectedBranch(val branch: String) : UndoPossibility()
    object Error : UndoPossibility()
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

      val connection = project.messageBus.connect()
      notification.whenExpired { connection.disconnect() }
      connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
        ApplicationManager.getApplication().executeOnPooledThread {
          if (checkUndoPossibility(project) !is UndoPossibility.Possible) notification.expire()
        }
      })

      notifier.notify(notification)
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