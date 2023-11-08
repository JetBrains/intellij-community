// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import git4idea.conflicts.GitConflictsUtil.acceptConflictSide
import git4idea.conflicts.GitConflictsUtil.canShowMergeWindow
import git4idea.conflicts.GitConflictsUtil.getConflictOperationLock
import git4idea.conflicts.GitConflictsUtil.showMergeWindow
import git4idea.i18n.GitBundle
import git4idea.index.ui.createMergeHandler
import git4idea.repo.GitConflict
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

private class GitAcceptTheirsAction : GitAcceptConflictSideAction(GitBundle.messagePointer("conflicts.accept.theirs.action.text"), true)
private class GitAcceptYoursAction : GitAcceptConflictSideAction(GitBundle.messagePointer("conflicts.accept.yours.action.text"), false)

abstract class GitConflictAction(text: Supplier<@Nls String>) :
  DumbAwareAction(text, Presentation.NULL_STRING) {

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val nodes = getConflicts(project, e)
    e.presentation.isVisible = nodes.isNotEmpty()
    e.presentation.isEnabled = isEnabled(project, nodes)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val nodes = getConflicts(project, e)
    perform(project, nodes)
  }

  private fun getConflicts(project: Project, e: AnActionEvent): List<GitConflict> {
    if (e.getData(ChangesListView.DATA_KEY) == null) return emptyList()
    val changes = e.getData(VcsDataKeys.CHANGES) ?: return emptyList()

    val repositoryManager = GitRepositoryManager.getInstance(project)
    return changes.asSequence()
      .filter { it.fileStatus == FileStatus.MERGED_WITH_CONFLICTS }
      .map(ChangesUtil::getFilePath)
      .mapNotNull { path -> repositoryManager.getRepositoryForFileQuick(path)?.stagingAreaHolder?.findConflict(path) }
      .toList()
  }

  protected abstract fun isEnabled(project: Project, conflicts: List<GitConflict>): Boolean
  protected abstract fun perform(project: Project, conflicts: List<GitConflict>)
}

abstract class GitAcceptConflictSideAction(text: Supplier<@Nls String>, private val takeTheirs: Boolean) : GitConflictAction(text) {
  override fun isEnabled(project: Project, conflicts: List<GitConflict>): Boolean {
    return conflicts.any { conflict -> !getConflictOperationLock(project, conflict).isLocked }
  }

  override fun perform(project: Project, conflicts: List<GitConflict>) {
    acceptConflictSide(project, createMergeHandler(project), conflicts, takeTheirs)
  }
}

class GitMergeConflictAction : GitConflictAction(GitBundle.messagePointer("action.Git.Merge.text")) {
  override fun isEnabled(project: Project, conflicts: List<GitConflict>): Boolean {
    val handler = createMergeHandler(project)
    return conflicts.any { conflict -> canShowMergeWindow(project, handler, conflict) }
  }

  override fun perform(project: Project, conflicts: List<GitConflict>) {
    showMergeWindow(project, createMergeHandler(project), conflicts)
  }
}
