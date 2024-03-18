// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.index.ui.GitFileStatusNode
import git4idea.index.ui.GitStageDataKeys
import java.util.function.Supplier
import javax.swing.Icon

abstract class GitFileStatusNodeAction(text: Supplier<String>, description: Supplier<String>, icon: Icon? = null)
  : DumbAwareAction(text, description, icon) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val nodes = e.getData(GitStageDataKeys.GIT_FILE_STATUS_NODES)
    e.presentation.isEnabledAndVisible =
      e.project != null && nodes != null &&
      nodes.asSequence().filter(this::matches).firstOrNull() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val nodes = e.getData(GitStageDataKeys.GIT_FILE_STATUS_NODES) ?: return

    perform(project, nodes.asSequence().filter(this::matches).toList())
  }

  abstract fun matches(statusNode: GitFileStatusNode): Boolean
  abstract fun perform(project: Project, nodes: List<GitFileStatusNode>)
}