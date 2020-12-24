// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.util.containers.asJBIterable
import git4idea.conflicts.GitMergeHandler
import git4idea.conflicts.acceptConflictSide
import git4idea.conflicts.getConflictOperationLock
import git4idea.conflicts.showMergeWindow
import git4idea.i18n.GitBundle
import git4idea.index.ui.*
import git4idea.repo.GitConflict
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

class GitAcceptTheirsAction : GitAcceptConflictSideAction(true)
class GitAcceptYoursAction : GitAcceptConflictSideAction(false)

abstract class GitConflictAction(text: Supplier<@Nls String>) :
  GitFileStatusNodeAction(text, Presentation.NULL_STRING, null) {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val nodes = e.getData(GitStageDataKeys.GIT_FILE_STATUS_NODES).asJBIterable()
    if (project == null || nodes.filter(this::matches).isEmpty) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = isEnabled(project, nodes.filterMap(GitFileStatusNode::createConflict).asSequence() as Sequence<GitConflict>)
  }

  override fun matches(statusNode: GitFileStatusNode): Boolean = statusNode.kind == NodeKind.CONFLICTED

  override fun perform(project: Project, nodes: List<GitFileStatusNode>) {
    perform(project, createMergeHandler(project), nodes.mapNotNull { it.createConflict() })
  }

  protected open fun isEnabled(project: Project, conflicts: Sequence<GitConflict>): Boolean {
    return conflicts.any { conflict -> !getConflictOperationLock(project, conflict).isLocked }
  }

  protected abstract fun perform(project: Project, handler: GitMergeHandler, conflicts: List<GitConflict>)
}

abstract class GitAcceptConflictSideAction(private val takeTheirs: Boolean) : GitConflictAction(getActionText(takeTheirs)) {
  override fun perform(project: Project, handler: GitMergeHandler, conflicts: List<GitConflict>) {
    acceptConflictSide(project, createMergeHandler(project), conflicts, takeTheirs, project::isReversedRoot)
  }
}

private fun getActionText(takeTheirs: Boolean): Supplier<@Nls String> {
  return if (takeTheirs) GitBundle.messagePointer("conflicts.accept.theirs.action.text")
  else GitBundle.messagePointer("conflicts.accept.yours.action.text")
}

class GitMergeConflictAction : GitConflictAction(GitBundle.messagePointer("action.Git.Merge.text")) {

  override fun isEnabled(project: Project, conflicts: Sequence<GitConflict>): Boolean {
    val handler = createMergeHandler(project)
    return conflicts.any { conflict ->
      !getConflictOperationLock(project, conflict).isLocked && handler.canResolveConflict(conflict)
    }
  }

  override fun perform(project: Project, handler: GitMergeHandler, conflicts: List<GitConflict>) {
    showMergeWindow(project, handler, conflicts, project::isReversedRoot)
  }
}
