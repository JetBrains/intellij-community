// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

class NoMergesFilterAction(private val parentFilterModel: ParentFilterModel) : DumbAwareToggleAction(VcsLogBundle.message("vcs.log.filter.no.merges")) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return parentFilterModel.getFilter() != null
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = parentFilterModel.isEnabled
    e.presentation.isVisible = parentFilterModel.isVisible
    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      parentFilterModel.setFilter(VcsLogFilterObject.noMerges())
    }
    else {
      parentFilterModel.setFilter(null)
    }
  }
}