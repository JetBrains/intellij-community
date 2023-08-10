// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.util.containers.asJBIterable
import git4idea.conflicts.GitConflictsUtil.acceptConflictSide
import git4idea.conflicts.GitConflictsUtil.canShowMergeWindow
import git4idea.conflicts.GitConflictsUtil.getConflictOperationLock
import git4idea.conflicts.GitConflictsUtil.showMergeWindow
import git4idea.conflicts.GitMergeHandler
import git4idea.i18n.GitBundle
import git4idea.index.ui.*
import git4idea.repo.GitConflict
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

class GitStageAcceptTheirsAction : GitStageAcceptConflictSideAction(GitBundle.messagePointer("conflicts.accept.theirs.action.text"), true)
class GitStageAcceptYoursAction : GitStageAcceptConflictSideAction(GitBundle.messagePointer("conflicts.accept.yours.action.text"), false)

abstract class GitStageConflictAction(text: Supplier<@Nls String>) :
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

abstract class GitStageAcceptConflictSideAction(text: Supplier<@Nls String>, private val takeTheirs: Boolean)
  : GitStageConflictAction(text) {
  override fun perform(project: Project, handler: GitMergeHandler, conflicts: List<GitConflict>) {
    acceptConflictSide(project, handler, conflicts, takeTheirs)
  }
}

class GitStageMergeConflictAction : GitStageConflictAction(GitBundle.messagePointer("action.Git.Merge.text")) {

  override fun isEnabled(project: Project, conflicts: Sequence<GitConflict>): Boolean {
    val handler = createMergeHandler(project)
    return conflicts.any { conflict -> canShowMergeWindow(project, handler, conflict) }
  }

  override fun perform(project: Project, handler: GitMergeHandler, conflicts: List<GitConflict>) {
    showMergeWindow(project, handler, conflicts)
  }
}
