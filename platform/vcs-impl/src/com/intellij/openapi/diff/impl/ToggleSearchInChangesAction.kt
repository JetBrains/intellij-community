// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUserDataKeys.ENABLE_SEARCH_IN_CHANGES
import com.intellij.diff.util.DiffUtil
import com.intellij.find.SearchSession
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware

internal class ToggleSearchInChangesAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val search = e.getData(SearchSession.KEY)
    val diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT)
    if (search == null || search.findModel.isReplaceState || diffContext == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT) ?: return false

    return DiffUtil.isUserDataFlagSet(ENABLE_SEARCH_IN_CHANGES, diffContext)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT) ?: return

    diffContext.putUserData(ENABLE_SEARCH_IN_CHANGES, state)

    val search = e.getData(SearchSession.KEY) ?: return
    search.findModel.refresh()
  }
}
