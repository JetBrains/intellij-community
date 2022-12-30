// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.impl.BackgroundableActionLock
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import git4idea.merge.GitMergeUtil
import git4idea.repo.GitConflict
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls

object GitConflictsUtil {

  internal fun getConflictOperationLock(project: Project, conflict: GitConflict): BackgroundableActionLock {
    return BackgroundableActionLock.getLock(project, conflict.filePath)
  }

  internal fun acceptConflictSide(project: Project, handler: GitMergeHandler, selectedConflicts: List<GitConflict>, takeTheirs: Boolean) {
    acceptConflictSide(project, handler, selectedConflicts, takeTheirs) { root -> isReversedRoot(project, root) }
  }

  internal fun acceptConflictSide(project: Project, handler: GitMergeHandler, selectedConflicts: List<GitConflict>, takeTheirs: Boolean,
                                  isReversed: (root: VirtualFile) -> Boolean) {
    val conflicts = selectedConflicts.filterNot { getConflictOperationLock(project, it).isLocked }.toList()
    if (conflicts.isEmpty()) return

    val locks = conflicts.map { getConflictOperationLock(project, it) }
    locks.forEach { it.lock() }

    object : Task.Backgroundable(project, GitBundle.message("conflicts.accept.progress", conflicts.size), true) {
      override fun run(indicator: ProgressIndicator) {
        val reversedRoots = conflicts.mapTo(mutableSetOf()) { it.root }.filter(isReversed)
        handler.acceptOneVersion(conflicts, reversedRoots, takeTheirs)
      }

      override fun onFinished() {
        locks.forEach { it.unlock() }
      }
    }.queue()
  }

  private fun hasActiveMergeWindow(conflict: GitConflict) : Boolean {
    val file = LocalFileSystem.getInstance().findFileByPath(conflict.filePath.path) ?: return false
    return MergeConflictResolveUtil.hasActiveMergeWindow(file)
  }

  internal fun canShowMergeWindow(project: Project, handler: GitMergeHandler, conflict: GitConflict): Boolean {
    return handler.canResolveConflict(conflict) &&
           (!getConflictOperationLock(project, conflict).isLocked || hasActiveMergeWindow(conflict))
  }

  internal fun showMergeWindow(project: Project, handler: GitMergeHandler, selectedConflicts: List<GitConflict>) {
    showMergeWindow(project, handler, selectedConflicts, { root -> isReversedRoot(project, root) })
  }

  internal fun showMergeWindow(project: Project,
                               handler: GitMergeHandler,
                               selectedConflicts: List<GitConflict>,
                               isReversed: (root: VirtualFile) -> Boolean) {
    val conflicts = selectedConflicts.filter { canShowMergeWindow(project, handler, it) }.toList()
    if (conflicts.isEmpty()) return

    for (conflict in conflicts) {
      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(conflict.filePath.path)
      if (file == null) {
        VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.CANNOT_RESOLVE_CONFLICT,
                                                     GitBundle.message("conflicts.merge.window.error.title"),
                                                     GitBundle.message("conflicts.merge.window.error.message", conflict.filePath))
        continue
      }

      val lock = getConflictOperationLock(project, conflict)
      MergeConflictResolveUtil.showMergeWindow(project, file, lock) {
        handler.resolveConflict(conflict, file, isReversed(conflict.root))
      }
    }
  }

  @Nls
  internal fun getConflictType(conflict: GitConflict): String {
    val oursStatus = conflict.getStatus(GitConflict.ConflictSide.OURS, true)
    val theirsStatus = conflict.getStatus(GitConflict.ConflictSide.THEIRS, true)
    return when {
      oursStatus == GitConflict.Status.DELETED && theirsStatus == GitConflict.Status.DELETED ->
        GitBundle.message("conflicts.type.both.deleted")
      oursStatus == GitConflict.Status.ADDED && theirsStatus == GitConflict.Status.ADDED -> GitBundle.message("conflicts.type.both.added")
      oursStatus == GitConflict.Status.MODIFIED && theirsStatus == GitConflict.Status.MODIFIED ->
        GitBundle.message("conflicts.type.both.modified")
      oursStatus == GitConflict.Status.DELETED -> GitBundle.message("conflicts.type.deleted.by.you")
      theirsStatus == GitConflict.Status.DELETED -> GitBundle.message("conflicts.type.deleted.by.them")
      oursStatus == GitConflict.Status.ADDED -> GitBundle.message("conflicts.type.added.by.you")
      theirsStatus == GitConflict.Status.ADDED -> GitBundle.message("conflicts.type.added.by.them")
      else -> throw IllegalStateException("ours: $oursStatus; theirs: $theirsStatus")
    }
  }

  private fun isReversedRoot(project: Project, root: VirtualFile): Boolean {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) ?: return false
    return GitMergeUtil.isReverseRoot(repository)
  }
}