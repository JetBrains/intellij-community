// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import git4idea.index.ui.GIT_FILE_STATUS_NODES_STREAM
import git4idea.index.ui.GitFileStatusNode
import java.util.function.Supplier
import javax.swing.Icon
import kotlin.streams.toList

abstract class GitFileStatusNodeAction(text: Supplier<String>, description: Supplier<String>, icon: Icon? = null)
  : DumbAwareAction(text, description, icon) {

  override fun update(e: AnActionEvent) {
    val statusInfoStream = e.getData(GIT_FILE_STATUS_NODES_STREAM)
    e.presentation.isEnabledAndVisible = e.project != null && statusInfoStream != null &&
                                         statusInfoStream.anyMatch(this::matches)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val nodes = e.getRequiredData(GIT_FILE_STATUS_NODES_STREAM).filter(this::matches).toList()

    perform(project, nodes)
  }

  abstract fun matches(statusNode: GitFileStatusNode): Boolean
  abstract fun perform(project: Project, nodes: List<GitFileStatusNode>)
}