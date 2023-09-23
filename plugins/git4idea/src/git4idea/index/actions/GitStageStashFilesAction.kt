// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import git4idea.GitNotificationIdsHolder
import git4idea.GitStashUsageCollector
import git4idea.commands.Git
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import git4idea.index.GitStageTracker
import git4idea.index.ui.GitFileStatusNode
import git4idea.index.ui.NodeKind
import git4idea.stash.GitStashOperations
import git4idea.stash.createStashPushHandler
import git4idea.stash.refreshStash
import java.util.function.Supplier
import javax.swing.Icon

object GitStashOperation : StagingAreaOperation {
  override val actionText: Supplier<String> = GitBundle.messagePointer("stash.files.action.text")
  override val progressTitle: String = GitBundle.message("stash.files.progress.title")
  override val icon: Icon? = null
  override val errorMessage: String = GitBundle.message("stash.files.error.message")

  override fun matches(statusNode: GitFileStatusNode): Boolean {
    return statusNode.kind == NodeKind.UNSTAGED || statusNode.kind == NodeKind.UNTRACKED || statusNode.kind == NodeKind.STAGED
  }

  override fun processPaths(project: Project, root: VirtualFile, nodes: List<GitFileStatusNode>) {
    val activity = GitStashUsageCollector.logStashPush(project)
    try {
      val handler = createStashPushHandler(project, root, nodes.map { it.filePath }, "-u")
      Git.getInstance().runCommand(handler).throwOnError()
    }
    finally {
      activity.finished()
    }

    StagingAreaOperation.refreshVirtualFiles(nodes, true)
    refreshStash(project, root)
  }

  override fun reportResult(project: Project,
                            nodes: List<GitFileStatusNode>,
                            successfulRoots: Collection<VirtualFile>,
                            exceptionsByRoot: MultiMap<VirtualFile, VcsException>) {
    super.reportResult(project, nodes, successfulRoots, exceptionsByRoot)
    if (successfulRoots.isNotEmpty()) {
      GitStashOperations.showSuccessNotification(project, successfulRoots, !exceptionsByRoot.isEmpty)
      showNonEmptyStagingAreaNotification(project, nodes, successfulRoots)
    }
  }

  private const val SHOW_NOTIFICATION_PROPERTY = "git.stash.notify.non.empty.index"

  private fun showNonEmptyStagingAreaNotification(project: Project, nodes: List<GitFileStatusNode>, successfulRoots: Collection<VirtualFile>) {
    if (PropertiesComponent.getInstance(project).getBoolean(SHOW_NOTIFICATION_PROPERTY, true)) {
      val tracker = GitStageTracker.getInstance(project)
      val stagedRoots = tracker.state.stagedRoots

      val stagedRootsToStash = mutableSetOf<VirtualFile>()
      for ((root, nodesInRoot) in nodes.filter { stagedRoots.contains(it.root) && successfulRoots.contains(it.root) }.groupBy { it.root }) {
        val paths = nodesInRoot.mapTo(mutableSetOf()) { it.filePath }
        if (tracker.stagedFiles(root).any { !paths.contains(it) }) {
          stagedRootsToStash.add(root)
        }
      }
      if (stagedRootsToStash.isEmpty()) return

      val message = GitBundle.message("stash.changes.non.empty.index.for.roots.notification.text",
                                      GitStashOperations.getRootsText(project, stagedRootsToStash))
      val action = NotificationAction.createSimpleExpiring(GitBundle.message("stash.changes.non.empty.index.notification.action")) {
        PropertiesComponent.getInstance(project).setValue(SHOW_NOTIFICATION_PROPERTY, false)
      }
      VcsNotifier.getInstance(project).notifyMinorWarning(GitNotificationIdsHolder.STASH_NON_EMPTY_INDEX_DETECTED, "", message, action)
    }
  }

  private fun GitStageTracker.stagedFiles(root: VirtualFile): Collection<FilePath> {
    val rootState = state.rootStates[root] ?: return emptyList()
    return rootState.statuses.filterValues { it.getStagedStatus() != null }.map { it.key }
  }
}

class GitStageStashFilesAction : StagingAreaOperationAction(GitStashOperation) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null &&
        !GitVersionSpecialty.STASH_PUSH_PATHSPEC_SUPPORTED.existsIn(GitExecutableManager.getInstance().getVersion(project))) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      super.update(e)
    }
  }
}