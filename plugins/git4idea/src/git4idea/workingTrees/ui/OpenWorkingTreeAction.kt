// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.PlatformProjectOpenProcessor
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.io.path.Path

internal class OpenWorkingTreeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val data = e.getData(SELECTED_WORKING_TREES)
    e.presentation.isEnabled = isEnabledFor(data, e.project)
  }

  private fun isEnabledFor(trees: List<GitWorkingTree>?, project: Project?): Boolean {
    if (project == null || trees == null || trees.size != 1 || trees[0].isCurrent) return false
    val treePath = trees[0].path.path
    return ProjectManager.getInstance().openProjects.none {
      project.basePath == treePath
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val data = e.getData(SELECTED_WORKING_TREES)
    if (!isEnabledFor(data, project)) return

    val tree = data!!.first()
    GitWorkingTreesService.getInstance(project).coroutineScope.launch(Dispatchers.Default) {
      PlatformProjectOpenProcessor.getInstance().openProjectAndFile(Path(tree.path.path), false, OpenProjectTask.build())
    }
  }
}