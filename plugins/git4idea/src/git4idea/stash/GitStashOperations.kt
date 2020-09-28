// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.merge.GitConflictResolver
import git4idea.repo.GitRepositoryManager
import git4idea.ui.StashInfo
import git4idea.util.GitUIUtil
import java.awt.Component
import java.util.*
import javax.swing.event.HyperlinkEvent

object GitStashOperations {
  @JvmStatic
  fun dropStashWithConfirmation(project: Project, root: VirtualFile, parentComponent: Component, stash: StashInfo): Boolean {
    if (Messages.YES == Messages.showYesNoDialog(parentComponent,
                                                 GitBundle.message("git.unstash.drop.confirmation.message", stash.stash, stash.message),
                                                 GitBundle.message("git.unstash.drop.confirmation.title", stash.stash),
                                                 Messages.getQuestionIcon())) {
      val current = ModalityState.current()
      ProgressManager.getInstance().run(object : Task.Modal(
        project,
        GitBundle.message("unstash.dialog.remove.stash.progress.indicator.title", stash.stash),
        true
      ) {
        override fun run(indicator: ProgressIndicator) {
          val h = GitLineHandler(project, root, GitCommand.STASH)
          h.addParameters("drop", stash.stash)
          try {
            Git.getInstance().runCommand(h).throwOnError()
          }
          catch (ex: VcsException) {
            ApplicationManager.getApplication().invokeLater(
              {
                GitUIUtil.showOperationError(project, ex, h.printableCommandLine())
              }, current)
          }
        }
      })
      return true
    }
    return false
  }

  @JvmStatic
  fun clearStashesWithConfirmation(project: Project,
                                   root: VirtualFile,
                                   parentComponent: Component): Boolean {
    if (Messages.YES == Messages.showYesNoDialog(parentComponent,
                                                 GitBundle.message("git.unstash.clear.confirmation.message"),
                                                 GitBundle.message("git.unstash.clear.confirmation.title"), Messages.getWarningIcon())) {
      val h = GitLineHandler(project, root, GitCommand.STASH)
      h.addParameters("clear")
      object : Task.Modal(project, GitBundle.message("unstash.clearing.stashes"), false) {
        override fun run(indicator: ProgressIndicator) {
          val result = Git.getInstance().runCommand(h)
          if (!result.success()) {
            ApplicationManager.getApplication().invokeLater {
              GitUIUtil.showOperationError(project,
                                           GitBundle.message("unstash.clearing.stashes"),
                                           result.errorOutputAsJoinedString)
            }
          }
        }
      }.queue()
      return true
    }
    return false
  }

  @JvmStatic
  fun viewStash(project: Project, root: VirtualFile, stash: StashInfo) {
    val selectedStash = stash.stash
    try {
      val hash = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable<String, VcsException> { resolveHashOfStash(project, root, selectedStash) },
        GitBundle.message("unstash.dialog.stash.details.load.progress.indicator.title"),
        true,
        project
      )
      GitUtil.showSubmittedFiles(project, hash, root, true, false)
    }
    catch (ex: VcsException) {
      GitUIUtil.showOperationError(project, ex, GitBundle.message("operation.name.resolving.revision"))
    }
  }

  @Throws(VcsException::class)
  private fun resolveHashOfStash(project: Project, root: VirtualFile, stash: String): String {
    val h = GitLineHandler(project, root, GitCommand.REV_LIST)
    h.setSilent(true)
    h.addParameters("--timestamp", "--max-count=1", stash)
    h.endOptions()
    val output = Git.getInstance().runCommand(h).getOutputOrThrow()
    return GitRevisionNumber.parseRevlistOutputAsRevisionNumber(h, output).asString()
  }

  @JvmStatic
  fun unstash(project: Project, root: VirtualFile, stash: StashInfo,
              branch: String?, popStash: Boolean, reinstateIndex: Boolean): Boolean {
    val h = unstashHandler(project, root, stash, branch, popStash, reinstateIndex)
    val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        //better to use quick to keep consistent state with ui
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root)!!
        val hash = Git.getInstance().resolveReference(repository, stash.stash)
        unstash(project, Collections.singletonMap(root, hash),
                { h },
                UnstashConflictResolver(project, root, stash))
      },
      GitBundle.message("unstash.unstashing"),
      true,
      project
    )
    if (completed) {
      VcsNotifier.getInstance(project).notifySuccess("git.unstash.patch.applied",
                                                     "", VcsBundle.message("patch.apply.success.applied.text"))
      return true
    }
    return false
  }

  private fun unstashHandler(project: Project,
                             root: VirtualFile,
                             stash: StashInfo,
                             branch: String?,
                             popStash: Boolean,
                             reinstateIndex: Boolean): GitLineHandler {
    val h = GitLineHandler(project, root, GitCommand.STASH)
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
                                      private val root: VirtualFile,
                                      private val stashInfo: StashInfo) :
  GitConflictResolver(project, setOf(root), makeParams(project, stashInfo)) {

  override fun notifyUnresolvedRemain() {
    VcsNotifier.getInstance(myProject).notifyImportantWarning("git.unstash.with.unresolved.conflicts",
                                                              GitBundle.message(
                                                                "unstash.dialog.unresolved.conflict.warning.notification.title"),
                                                              GitBundle.message(
                                                                "unstash.dialog.unresolved.conflict.warning.notification.message"))
    { _, event ->
      if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        if (event.description == "resolve") {
          UnstashConflictResolver(myProject, root, stashInfo).mergeNoProceed()
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
        XmlStringUtil.wrapInHtmlTag(stashInfo.stash + "\"" + stashInfo.message + "\"", "code")
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