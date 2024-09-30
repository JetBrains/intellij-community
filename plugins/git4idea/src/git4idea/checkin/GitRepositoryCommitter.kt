// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.notification.NotificationAction
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.CommitExceptionWithActions
import com.intellij.vcs.commit.isAmendCommitMode
import com.intellij.vcs.commit.isCleanupCommitMessage
import com.intellij.vcs.log.VcsUser
import git4idea.checkin.GitCheckinEnvironment.Companion.COMMIT_DATE_FORMAT
import git4idea.checkin.GitCheckinEnvironment.Companion.runWithMessageFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineEventDetector
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import java.io.File
import java.util.*

data class GitCommitOptions(
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

internal class GitRepositoryCommitter(val repository: GitRepository, private val commitOptions: GitCommitOptions) {
  val project: Project get() = repository.project
  val root: VirtualFile get() = repository.root

  @Throws(VcsException::class)
  fun commitStaged(commitMessage: String) {
    runWithMessageFile(project, root, commitMessage) { messageFile -> commitStaged(messageFile) }
  }

  @Throws(VcsException::class)
  fun commitStaged(messageFile: File) {
    val gpgProblemDetector = GitGpgProblemDetector()
    val emptyCommitProblemDetector = GitEmptyCommitProblemDetector()
    val handler = GitLineHandler(project, root, GitCommand.COMMIT)
    handler.setStdoutSuppressed(false)
    handler.addLineListener(gpgProblemDetector)
    handler.addLineListener(emptyCommitProblemDetector)

    handler.setCommitMessage(messageFile)
    handler.setCommitOptions(commitOptions)
    handler.endOptions()

    val command = Git.getInstance().runCommand(handler)

    try {
      command.throwOnError()
    }
    catch (e: VcsException) {
      if (gpgProblemDetector.isDetected) {
        throw GitGpgCommitException(e)
      }
      if (emptyCommitProblemDetector.isDetected) {
        throw VcsException(GitBundle.message("git.commit.nothing.to.commit.error.message"))
      }
      throw e
    }
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

private class GitGpgProblemDetector : GitLineEventDetector {
  override var isDetected = false
    private set

  override fun onLineAvailable(line: String, outputType: Key<*>) {
    if (outputType === ProcessOutputTypes.STDERR && line.contains(PATTERN)) {
      isDetected = true
    }
  }

  companion object {
    private const val PATTERN = "gpg failed to sign the data"
  }
}

private class GitEmptyCommitProblemDetector : GitLineEventDetector {
  override var isDetected = false
    private set

  override fun onLineAvailable(line: String, outputType: Key<*>) {
    if (outputType === ProcessOutputTypes.STDOUT && PATTERNS.any { line.startsWith(it, ignoreCase = true) }) {
      isDetected = true
    }
  }

  companion object {
    private val PATTERNS = listOf(
      "No changes",
      "no changes added to commit",
      "nothing added to commit",
      "nothing to commit"
    )
  }
}

private class GitGpgCommitException(cause: VcsException) : VcsException(cause), CommitExceptionWithActions {
  override val actions: List<NotificationAction>
    get() = listOf(NotificationAction.createSimple(GitBundle.message("gpg.error.see.documentation.link.text")) {
      HelpManager.getInstance().invokeHelp(GitBundle.message("gpg.jb.manual.link"))
    })
}
