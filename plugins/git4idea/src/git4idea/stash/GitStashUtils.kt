// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GitStashUtils")

package git4idea.stash

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitCommit
import git4idea.GitNotificationIdsHolder.Companion.STASH_LOCAL_CHANGES_DETECTED
import git4idea.GitNotificationIdsHolder.Companion.UNSTASH_FAILED
import git4idea.GitUtil
import git4idea.commands.*
import git4idea.config.GitConfigUtil
import git4idea.history.GitCommitRequirements
import git4idea.history.GitCommitRequirements.DiffInMergeCommits.DIFF_TO_PARENTS
import git4idea.history.GitCommitRequirements.DiffRenameLimit.NO_RENAMES
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.merge.GitConflictResolver
import git4idea.ui.StashInfo
import git4idea.util.GitUntrackedFilesHelper
import git4idea.util.LocalChangesWouldBeOverwrittenHelper
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.Charset

private val LOG: Logger = Logger.getInstance("#git4idea.stash.GitStashUtils")

/**
 * Unstash the given roots one by one, handling common error scenarios.
 *
 * If there's an error in one of the roots, stop and show the error.
 * If there's a conflict, show the merge dialog, and if the conflicts get resolved, continue with other roots.
 */
fun unstash(project: Project,
            rootAndRevisions: Map<VirtualFile, Hash?>,
            handlerProvider: (VirtualFile) -> GitLineHandler,
            conflictResolver: GitConflictResolver): Boolean {
  DvcsUtil.workingTreeChangeStarted(project, GitBundle.message("activity.name.unstash")).use {
    for ((root, hash) in rootAndRevisions) {
      val handler = handlerProvider(root)

      val conflictDetector = GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT_ON_UNSTASH)
      val untrackedFilesDetector = GitUntrackedFilesOverwrittenByOperationDetector(root)
      val localChangesDetector = GitLocalChangesWouldBeOverwrittenDetector(root, GitLocalChangesWouldBeOverwrittenDetector.Operation.MERGE)
      handler.addLineListener(conflictDetector)
      handler.addLineListener(untrackedFilesDetector)
      handler.addLineListener(localChangesDetector)

      val result = Git.getInstance().runCommand { handler }

      val changesInStash = hash?.run { loadChangesInStash(project, root, hash) }
      GitUtil.refreshVfs(root, changesInStash?.flatten())

      if (conflictDetector.hasHappened()) {
        val conflictsResolved = conflictResolver.merge()
        if (!conflictsResolved) return false
      }
      else if (untrackedFilesDetector.wasMessageDetected()) {
        GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, root, untrackedFilesDetector.relativeFilePaths,
                                                                  GitBundle.message("unstash.operation.name"), null)
        return false
      }
      else if (localChangesDetector.wasMessageDetected()) {
        LocalChangesWouldBeOverwrittenHelper.showErrorNotification(project, STASH_LOCAL_CHANGES_DETECTED, root,
                                                                   GitBundle.message("unstash.operation.name"),
                                                                   localChangesDetector.relativeFilePaths)
        return false
      }
      else if (!result.success()) {
        VcsNotifier.getInstance(project).notifyError(UNSTASH_FAILED, GitBundle.message("notification.title.unstash.failed"),
                                                     result.errorOutputAsHtmlString, true)
        return false
      }
    }
  }
  return true
}

private fun loadChangesInStash(project: Project, root: VirtualFile, hash: Hash): List<Collection<Change>>? {
  return try {
    val consumer = CollectConsumer<GitCommit>()
    GitLogUtil.readFullDetailsForHashes(project, root, listOf(hash.asString()),
                                        GitCommitRequirements(false, NO_RENAMES, DIFF_TO_PARENTS), consumer)
    val stashCommit = consumer.result.first()
    return (0 until stashCommit.parents.size).map { stashCommit.getChanges(it) }
  }
  catch (e: Exception) {
    LOG.warn("Couldn't load changes in root [$root] in stash resolved to [$hash]", e)
    null
  }
}

@Deprecated("use the simpler overloading method which returns a list")
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
fun loadStashStack(project: Project, root: VirtualFile, consumer: Consumer<StashInfo>) {
  for (stash in loadStashStack(project, root)) {
    consumer.consume(stash)
  }
}

@Throws(VcsException::class)
fun loadStashStack(project: Project, root: VirtualFile): List<StashInfo> {
  val charset = Charset.forName(GitConfigUtil.getLogEncoding(project, root))

  val h = GitLineHandler(project, root, GitCommand.STASH.readLockingCommand())
  h.setSilent(true)
  h.addParameters("list", "--pretty=format:%H:%gd:%s")
  h.charset = charset
  val output = Git.getInstance().runCommand(h)
  output.throwOnError()

  val result = mutableListOf<StashInfo>()
  for (line in output.output) {
    val parts = line.split(':', limit = 4)
    when {
      parts.size < 3 -> {
        logger<GitUtil>().error("Can't parse stash record: ${line}")
      }
      parts.size == 3 -> {
        result.add(StashInfo(root, HashImpl.build(parts[0]), parts[1], null, parts[2].trim()))
      }
      else -> {
        result.add(StashInfo(root, HashImpl.build(parts[0]), parts[1], parts[2].trim(), parts[3].trim()))
      }
    }
  }
  return result
}

fun createStashHandler(project: Project, root: VirtualFile, keepIndex: Boolean = false, message: String = ""): GitLineHandler {
  val handler = GitLineHandler(project, root, GitCommand.STASH)
  handler.addParameters("save")
  if (keepIndex) {
    handler.addParameters("--keep-index")
  }
  val msg = message.trim()
  if (msg.isNotEmpty()) {
    handler.addParameters(msg)
  }
  return handler
}
