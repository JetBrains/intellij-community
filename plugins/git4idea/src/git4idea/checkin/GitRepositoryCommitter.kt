// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.isAmendCommitMode
import com.intellij.vcs.commit.isCleanupCommitMessage
import com.intellij.vcs.log.VcsUser
import git4idea.checkin.GitCheckinEnvironment.COMMIT_DATE_FORMAT
import git4idea.checkin.GitCheckinEnvironment.runWithMessageFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import java.io.File
import java.util.*

internal data class GitCommitOptions(
  val isAmend: Boolean = false,
  val isSignOff: Boolean = false,
  val isSkipHooks: Boolean = false,
  val commitAuthor: VcsUser? = null,
  val commitAuthorDate: Date? = null,
  val isCleanupCommitMessage: Boolean = false
) {
  constructor(context: CommitContext) : this(
    context.isAmendCommitMode,
    context.isSignOffCommit,
    context.isSkipHooks,
    context.commitAuthor,
    context.commitAuthorDate,
    context.isCleanupCommitMessage
  )
}

internal class GitRepositoryCommitter(val repository: GitRepository, val commitOptions: GitCommitOptions) {
  val project: Project get() = repository.project
  val root: VirtualFile get() = repository.root

  @Throws(VcsException::class)
  fun commitStaged(commitMessage: String) =
    runWithMessageFile(project, root, commitMessage) { messageFile -> commitStaged(messageFile) }

  @Throws(VcsException::class)
  fun commitStaged(messageFile: File) {
    val handler = GitLineHandler(project, root, GitCommand.COMMIT)
    handler.setStdoutSuppressed(false)

    handler.setCommitMessage(messageFile)
    handler.setCommitOptions(commitOptions)
    handler.endOptions()

    Git.getInstance().runCommand(handler).throwOnError()
  }
}

private fun GitLineHandler.setCommitOptions(options: GitCommitOptions) {
  if (options.isAmend) addParameters("--amend")
  if (options.isSignOff) addParameters("--signoff")
  if (options.isSkipHooks) addParameters("--no-verify")
  if (options.isCleanupCommitMessage) addParameters("--cleanup=strip")

  options.commitAuthor?.let { addParameters("--author=$it") }
  options.commitAuthorDate?.let { addParameters("--date", COMMIT_DATE_FORMAT.format(it)) }
}

private fun GitLineHandler.setCommitMessage(messageFile: File) {
  addParameters("-F")
  addAbsoluteFile(messageFile)
}
