// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GitStashUtils")

package git4idea.stash

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitCommit
import git4idea.GitNotificationIdsHolder
import git4idea.GitNotificationIdsHolder.Companion.STASH_LOCAL_CHANGES_DETECTED
import git4idea.GitNotificationIdsHolder.Companion.UNSTASH_FAILED
import git4idea.GitUtil
import git4idea.changes.GitChangeUtils
import git4idea.changes.GitCommittedChangeList
import git4idea.commands.*
import git4idea.config.GitConfigUtil
import git4idea.history.GitCommitRequirements
import git4idea.history.GitCommitRequirements.DiffInMergeCommits.DIFF_TO_PARENTS
import git4idea.history.GitCommitRequirements.DiffRenameLimit.NO_RENAMES
import git4idea.history.GitLogParser
import git4idea.history.GitLogParser.GitLogOption
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.merge.GitConflictResolver
import git4idea.ui.StashInfo
import git4idea.util.GitUIUtil
import git4idea.util.GitUntrackedFilesHelper
import git4idea.util.LocalChangesWouldBeOverwrittenHelper
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.nio.charset.Charset
import java.util.*
import javax.swing.event.HyperlinkEvent

private val LOG: Logger = Logger.getInstance("#git4idea.stash.GitStashUtils")

object GitStashOperations {

  @JvmStatic
  fun dropStashWithConfirmation(project: Project, parentComponent: Component?, stash: StashInfo): Boolean {
    val dialogBuilder = MessageDialogBuilder.yesNo(GitBundle.message("git.unstash.drop.confirmation.title", stash.stash),
                                                   GitBundle.message("git.unstash.drop.confirmation.message", stash.stash,
                                                                     stash.message)).icon(Messages.getQuestionIcon())
    val confirmed = if (parentComponent != null) dialogBuilder.ask(parentComponent) else dialogBuilder.ask(project)
    if (!confirmed) return false

    val h = GitLineHandler(project, stash.root, GitCommand.STASH)
    h.addParameters("drop", stash.stash)
    try {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable<Unit, VcsException> { Git.getInstance().runCommand(h).throwOnError() },
        GitBundle.message("unstash.dialog.remove.stash.progress.indicator.title", stash.stash),
        true,
        project
      )
      return true
    }
    catch (ex: VcsException) {
      GitUIUtil.showOperationError(project, ex, h.printableCommandLine())
    }
    return false
  }

  @JvmStatic
  fun clearStashesWithConfirmation(project: Project, root: VirtualFile, parentComponent: Component?): Boolean {
    val dialogBuilder = MessageDialogBuilder.yesNo(GitBundle.message("git.unstash.clear.confirmation.title"),
                                                   GitBundle.message("git.unstash.clear.confirmation.message")).icon(Messages.getWarningIcon())
    val confirmed = if (parentComponent != null) dialogBuilder.ask(parentComponent) else dialogBuilder.ask(project)
    if (!confirmed) return false

    val h = GitLineHandler(project, root, GitCommand.STASH)
    h.addParameters("clear")
    try {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable<Unit, VcsException> { Git.getInstance().runCommand(h).throwOnError() },
        GitBundle.message("unstash.clearing.stashes"),
        false,
        project
      )
      return true
    }
    catch (ex: VcsException) {
      GitUIUtil.showOperationError(project, ex, h.printableCommandLine())
    }
    return false
  }

  @JvmStatic
  fun viewStash(project: Project, stash: StashInfo, compareWithLocal: Boolean) {
    val emptyChangeList = CommittedChangeListImpl(stash.stash, stash.message, "", -1, Date(0), emptyList())
    val dialog = ChangeListViewerDialog(project, emptyChangeList, null)
    dialog.loadChangesInBackground {
      ChangeListViewerDialog.ChangelistData(loadStashedChanges(project, stash.root, stash.hash, compareWithLocal), null)
    }
    dialog.title = GitBundle.message("unstash.view.dialog.title", stash.stash)
    dialog.show()
  }

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  fun loadStashedChanges(project: Project, root: VirtualFile, hash: Hash, compareWithLocal: Boolean): GitCommittedChangeList {
    return GitChangeUtils.getRevisionChanges(project, GitUtil.getRootForFile(project, root), hash.asString(),
                                             true, compareWithLocal, false)
  }

  @JvmStatic
  fun unstash(project: Project, stash: StashInfo, branch: String?, popStash: Boolean, reinstateIndex: Boolean): Boolean {
    val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      ThrowableComputable {
        return@ThrowableComputable unstash(project, mapOf(Pair(stash.root, stash.hash)),
                                           { createUnstashHandler(project, stash, branch, popStash, reinstateIndex) },
                                           UnstashConflictResolver(project, stash))
      },
      GitBundle.message("unstash.unstashing"),
      true,
      project
    )
    if (!completed) return false

    VcsNotifier.getInstance(project).notifySuccess(GitNotificationIdsHolder.UNSTASH_PATCH_APPLIED, "",
                                                   VcsBundle.message("patch.apply.success.applied.text"))
    return true
  }

  /**
   * Unstash the given roots one by one, handling common error scenarios.
   *
   * If there's an error in one of the roots, stop and show the error.
   * If there's a conflict, show the merge dialog, and if the conflicts get resolved, continue with other roots.
   */
  @JvmStatic
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

        if (hash != null) refreshUnstashedChanges(project, hash, root)

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

  private fun refreshUnstashedChanges(project: Project, hash: Hash, root: VirtualFile) {
    try {
      val consumer = CollectConsumer<GitCommit>()
      GitLogUtil.readFullDetailsForHashes(project, root, listOf(hash.asString()),
                                          GitCommitRequirements(false, NO_RENAMES, DIFF_TO_PARENTS), consumer)
      val stashCommit = consumer.result.first()

      val changesInStash = (0 until stashCommit.parents.size).flatMap { stashCommit.getChanges(it) }
      GitUtil.refreshVfs(root, changesInStash)
    }
    catch (e: Exception) {
      LOG.warn("Couldn't load changes in root [$root] in stash resolved to [$hash]", e)
    }
  }
}

private class UnstashConflictResolver(project: Project, private val stashInfo: StashInfo) :
  GitConflictResolver(project, setOf(stashInfo.root), makeParams(project, stashInfo)) {

  override fun notifyUnresolvedRemain() {
    VcsNotifier.getInstance(myProject).notifyImportantWarning(GitNotificationIdsHolder.UNSTASH_UNRESOLVED_CONFLICTS,
                                                              GitBundle.message("unstash.dialog.unresolved.conflict.warning.notification.title"),
                                                              GitBundle.message("unstash.dialog.unresolved.conflict.warning.notification.message")
    ) { _, event ->
      if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        if (event.description == "resolve") {
          UnstashConflictResolver(myProject, stashInfo).mergeNoProceedInBackground()
        }
      }
    }
  }

  companion object {
    private fun makeParams(project: Project, stashInfo: StashInfo): Params {
      val params = Params(project)
      params.setErrorNotificationTitle(GitBundle.message("unstash.unstashed.with.conflicts.error.title"))
      params.setMergeDialogCustomizer(UnstashMergeDialogCustomizer(stashInfo))
      return params
    }
  }
}

private class UnstashMergeDialogCustomizer(private val stashInfo: StashInfo) : MergeDialogCustomizer() {
  override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>): String {
    return XmlStringUtil.wrapInHtml(
      GitBundle.message("unstash.conflict.dialog.description.label.text",
                        XmlStringUtil.wrapInHtmlTag("${stashInfo.stash}\"${stashInfo.message}\"", "code"))
    )
  }

  override fun getLeftPanelTitle(file: VirtualFile): String {
    return GitBundle.message("unstash.conflict.diff.dialog.left.title")
  }

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): String {
    return GitBundle.message("unstash.conflict.diff.dialog.right.title")
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
  val options = arrayOf(GitLogOption.HASH, GitLogOption.SHORT_REF_LOG_SELECTOR, GitLogOption.SUBJECT)
  val charset = Charset.forName(GitConfigUtil.getLogEncoding(project, root))

  val h = GitLineHandler(project, root, GitCommand.STASH.readLockingCommand())
  h.setSilent(true)
  h.addParameters("list", "--pretty=format:" + GitLogParser.makeFormatFromOptions(options, ":"))
  h.charset = charset
  val output = Git.getInstance().runCommand(h)
  output.throwOnError()

  val result = mutableListOf<StashInfo>()
  for (line in output.output) {
    val parts = line.split(':', limit = options.size + 1) // subject is usually prefixed by "WIP on <branch>:"
    when {
      parts.size < options.size -> {
        logger<GitUtil>().error("Can't parse stash record: ${line}")
      }
      parts.size == options.size -> {
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

private fun createUnstashHandler(project: Project, stash: StashInfo, branch: String?,
                                 popStash: Boolean, reinstateIndex: Boolean): GitLineHandler {
  val h = GitLineHandler(project, stash.root, GitCommand.STASH)
  if (branch.isNullOrBlank()) {
    h.addParameters(if (popStash) "pop" else "apply")
    if (reinstateIndex) {
      h.addParameters("--index")
    }
  }
  else {
    h.addParameters("branch", branch)
  }
  h.addParameters(stash.stash)
  return h
}