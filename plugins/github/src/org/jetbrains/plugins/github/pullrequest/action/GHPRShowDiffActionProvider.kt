// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel

class GHPRShowDiffActionProvider : AnActionExtensionProvider {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isActive(e: AnActionEvent): Boolean = e.getData(GHPRChangeListViewModel.DATA_KEY) != null

  override fun update(e: AnActionEvent) {
    updateAvailability(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val vm = e.getRequiredData(GHPRChangeListViewModel.DATA_KEY)
    vm.showDiff()
  }

  companion object {
    @JvmStatic
    fun updateAvailability(e: AnActionEvent) {
      val project = e.project
      val vm = e.getData(GHPRChangeListViewModel.DATA_KEY)
      e.presentation.isEnabled = project != null && vm != null && vm.canShowDiff()
    }
  }
}
