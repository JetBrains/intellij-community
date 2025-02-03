// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ChangesViewNodeAction
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.HoverIcon
import git4idea.conflicts.GitConflictsUtil
import git4idea.conflicts.GitConflictsUtil.showMergeWindow
import git4idea.i18n.GitBundle
import git4idea.index.ui.createMergeHandler
import git4idea.repo.GitRepositoryManager

class GitChangesViewNodeAction(val project: Project) : ChangesViewNodeAction {
  override fun createNodeHoverIcon(node: ChangesBrowserNode<*>): HoverIcon? {
    val change = node.userObject as? Change ?: return null
    if (change.fileStatus != FileStatus.MERGED_WITH_CONFLICTS) return null

    val path = ChangesUtil.getFilePath(change)
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
}