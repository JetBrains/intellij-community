// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.ui.ExperimentalUI
import com.intellij.util.containers.asJBIterable
import git4idea.index.createThreeSidesDiffRequestProducer
import git4idea.index.createTwoSidesDiffRequestProducer
import git4idea.index.ui.GitStageDataKeys
import git4idea.index.ui.NodeKind

class GitStageDiffAction : AnActionExtensionProvider {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isActive(e: AnActionEvent): Boolean = e.getData(GitStageDataKeys.GIT_STAGE_TREE) != null

  override fun update(e: AnActionEvent) {
    updateAvailability(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val producers = e.getRequiredData(GitStageDataKeys.GIT_STAGE_TREE).statusNodesListSelection(true)
      .map { createTwoSidesDiffRequestProducer(e.project!!, it) }
    DiffManager.getInstance().showDiff(e.project, ChangeDiffRequestChain(producers), DiffDialogHints.DEFAULT)
  }

  companion object {
    @JvmStatic
    fun updateAvailability(e: AnActionEvent) {
      val nodes = e.getData(GitStageDataKeys.GIT_FILE_STATUS_NODES).asJBIterable()
      e.presentation.isEnabled = e.project != null &&
                                 nodes.filter { it.kind != NodeKind.IGNORED }.isNotEmpty
      e.presentation.isVisible =
        if (e.isFromActionToolbar && ExperimentalUI.isNewUI()) false
        else e.presentation.isEnabled || e.isFromActionToolbar
    }
  }
}

class GitStageThreeSideDiffAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val nodes = e.getData(GitStageDataKeys.GIT_FILE_STATUS_NODES).asJBIterable()
    e.presentation.isEnabled = e.project != null &&
                               e.getData(GitStageDataKeys.GIT_STAGE_TREE) != null &&
                               nodes.filter { it.kind != NodeKind.IGNORED }.isNotEmpty
    e.presentation.isVisible = e.presentation.isEnabled || e.isFromActionToolbar
  }

  override fun actionPerformed(e: AnActionEvent) {
    val producers = e.getRequiredData(GitStageDataKeys.GIT_STAGE_TREE).statusNodesListSelection(false)
      .map { createThreeSidesDiffRequestProducer(e.project!!, it) }
    DiffManager.getInstance().showDiff(e.project, ChangeDiffRequestChain(producers), DiffDialogHints.DEFAULT)
  }
}
