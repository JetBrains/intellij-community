// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroupWrapper
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.projectView.pane.ProjectViewPaneKind

internal class ProjectViewToolbarActionGroup(delegate: ActionGroup) : ActionGroupWrapper(delegate) {
  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    val paneKind = e?.getData(ProjectViewPaneKind.DATA_KEY)
    if (
      paneKind == null || // no pane => no actions
      paneKind == ProjectViewPaneKind.UI_ONLY // pure UI panes don't support actions at the moment
    ) {
      return emptyArray() 
    }
    return super.getChildren(e)
  }
}
