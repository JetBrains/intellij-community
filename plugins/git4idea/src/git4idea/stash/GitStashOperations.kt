// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog
import com.intellij.openapi.vcs.changes.ui.ChangeListViewerDialog.ChangelistData
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeListImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.Hash
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitNotificationIdsHolder.Companion.UNSTASH_PATCH_APPLIED
import git4idea.GitNotificationIdsHolder.Companion.UNSTASH_UNRESOLVED_CONFLICTS
import git4idea.GitUtil
import git4idea.changes.GitChangeUtils
import git4idea.changes.GitCommittedChangeList
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.merge.GitConflictResolver
import git4idea.ui.StashInfo
import git4idea.util.GitUIUtil
import java.awt.Component
import java.util.*
import javax.swing.event.HyperlinkEvent

object GitStashOperations {

  @JvmStatic
  fun dropStashWithConfirmation(project: Project, parentComponent: Component?, stash: StashInfo): Boolean {
    val dialogBuilder = yesNo(GitBundle.message("git.unstash.drop.confirmation.title", stash.stash),
                              GitBundle.message("git.unstash.drop.confirmation.message", stash.stash, stash.message)).icon(
      Messages.getQuestionIcon())
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
    val dialogBuilder = yesNo(GitBundle.message("git.unstash.clear.confirmation.title"),
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
      ChangelistData(loadStashedChanges(project, stash.root, stash.hash, compareWithLocal), null)
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
                                           { unstashHandler(project, stash, branch, popStash, reinstateIndex) },
                                           UnstashConflictResolver(project, stash))
      },
      GitBundle.message("unstash.unstashing"),
      true,
      project
    )
    if (!completed) return false

    VcsNotifier.getInstance(project).notifySuccess(UNSTASH_PATCH_APPLIED, "",
                                                   VcsBundle.message("patch.apply.success.applied.text"))
    return true
  }

  private fun unstashHandler(project: Project,
                             stash: StashInfo,
                             branch: String?,
                             popStash: Boolean,
                             reinstateIndex: Boolean): GitLineHandler {
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
}

private class UnstashConflictResolver(project: Project,
                                      private val stashInfo: StashInfo) :
  GitConflictResolver(project, setOf(stashInfo.root), makeParams(project, stashInfo)) {

  override fun notifyUnresolvedRemain() {
    VcsNotifier.getInstance(myProject).notifyImportantWarning(UNSTASH_UNRESOLVED_CONFLICTS,
                                                              GitBundle.message(
                                                                "unstash.dialog.unresolved.conflict.warning.notification.title"),
                                                              GitBundle.message(
                                                                "unstash.dialog.unresolved.conflict.warning.notification.message")
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
      GitBundle.message(
        "unstash.conflict.dialog.description.label.text",
        XmlStringUtil.wrapInHtmlTag("${stashInfo.stash}\"${stashInfo.message}\"", "code")
      )
    )
  }

  override fun getLeftPanelTitle(file: VirtualFile): String {
    return GitBundle.message("unstash.conflict.diff.dialog.left.title")
  }

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): String {
    return GitBundle.message("unstash.conflict.diff.dialog.right.title")
  }
}