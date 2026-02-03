// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel

internal class GHPRDeleteMergedHeadAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    val vm = e.getData(GHPRTimelineViewModel.DATA_KEY) ?: return
    e.presentation.isEnabledAndVisible = vm.detailsVm.canDeleteMergedBranch.value
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getData(GHPRTimelineViewModel.DATA_KEY) ?: return
    vm.detailsVm.deleteMergedBranch()
  }
}