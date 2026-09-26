// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ChangesViewNodeAction
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.HoverIcon
import com.intellij.openapi.vcs.merge.MergeConflictManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.actions.GitRevertResolvedAction
import git4idea.conflicts.GitConflictsUtil
import git4idea.conflicts.GitConflictsUtil.showMergeWindow
import git4idea.i18n.GitBundle
import git4idea.index.ui.createMergeHandler
import git4idea.repo.GitRepositoryManager

class GitChangesViewNodeAction(val project: Project) : ChangesViewNodeAction {
  override fun createNodeHoverIcon(node: ChangesBrowserNode<*>): HoverIcon? {
    val change = node.userObject as? Change

    val path = node.userObject.virtualFile?.let(VcsUtil::getFilePath)
    if (path != null && MergeConflictManager.getInstance(project).isResolvedConflict(path)) {
      return GitRollbackHoverIcon(project)
    }

    if (change == null || path == null) return null
    if (change.fileStatus != FileStatus.MERGED_WITH_CONFLICTS) return null

    val stagingAreaHolder = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(path)?.stagingAreaHolder
    if (stagingAreaHolder?.findConflict(path) == null) return null

    return GitMergeHoverIcon(project)
  }

  override fun handleDoubleClick(node: ChangesBrowserNode<*>): Boolean {
    val change = node.userObject as? Change ?: return false
    if (change.fileStatus != FileStatus.MERGED_WITH_CONFLICTS) return false

    val path = ChangesUtil.getFilePath(change)
    val stagingAreaHolder = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(path)?.stagingAreaHolder
    val conflict = stagingAreaHolder?.findConflict(path) ?: return false

    val handler = createMergeHandler(project)
    if (!GitConflictsUtil.canShowMergeWindow(project, handler, conflict)) return false

    showMergeWindow(project, handler, listOf(conflict))
    return true
  }

  private data class GitMergeHoverIcon(val project: Project)
    : HoverIcon(AllIcons.Vcs.Merge, GitBundle.message("changes.view.merge.action.text")) {
    override fun invokeAction(node: ChangesBrowserNode<*>) {
      val change = node.userObject as? Change ?: return

      val path = ChangesUtil.getFilePath(change)
      val stagingAreaHolder = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(path)?.stagingAreaHolder
      val conflict = stagingAreaHolder?.findConflict(path) ?: return

      showMergeWindow(project, createMergeHandler(project), listOf(conflict))
    }
  }

  private data class GitRollbackHoverIcon(val project: Project)
    : HoverIcon(AllIcons.Actions.Rollback, GitBundle.message("changes.view.rollback.action.text")) {
    override fun invokeAction(node: ChangesBrowserNode<*>) {
      val file = node.userObject.virtualFile
      if (file == null) return

      GitRevertResolvedAction.rollbackFiles(project, listOf(file))
    }
  }

}

private val Any?.virtualFile
  get() = when (this) {
  is Change -> ChangesUtil.getFilePath(this).virtualFile
  is VirtualFile -> this
  is FilePath -> this.virtualFile
  else -> null
}
