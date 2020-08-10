// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import git4idea.conflicts.acceptConflictSide
import git4idea.conflicts.getConflictOperationLock
import git4idea.i18n.GitBundle
import git4idea.index.ui.*
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

class GitAcceptTheirsAction: GitAcceptConflictSideAction(true)
class GitAcceptYoursAction: GitAcceptConflictSideAction(false)

abstract class GitAcceptConflictSideAction(private val takeTheirs: Boolean) :
  GitFileStatusNodeAction(getActionText(takeTheirs), Presentation.NULL_STRING, null) {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val statusInfoStream = e.getData(GIT_FILE_STATUS_NODES_STREAM)
    if (project == null || statusInfoStream == null || !statusInfoStream.anyMatch(this::matches)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isVisible = true
    e.presentation.isEnabled = e.getRequiredData(GIT_FILE_STATUS_NODES_STREAM).anyMatch { node ->
      node.createConflict()?.let { conflict -> getConflictOperationLock(project, conflict).isLocked } == false
    }
  }

  override fun matches(statusNode: GitFileStatusNode): Boolean = statusNode.kind == NodeKind.CONFLICTED

  override fun perform(project: Project, nodes: List<GitFileStatusNode>) {
    val conflicts = nodes.mapNotNull { it.createConflict() }
    acceptConflictSide(project, createMergeHandler(project), conflicts, takeTheirs, project::isReversedRoot)
  }
}

private fun getActionText(takeTheirs: Boolean): Supplier<@Nls String> {
  return if (takeTheirs) GitBundle.messagePointer("conflicts.accept.theirs.action.text")
  else GitBundle.messagePointer("conflicts.accept.yours.action.text")
}