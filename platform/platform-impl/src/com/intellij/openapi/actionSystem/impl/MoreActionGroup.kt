// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil

open class MoreActionGroup : DefaultActionGroup() {
  init {
    templatePresentation.isPopupGroup = true
    @Suppress("UnresolvedPluginConfigReference") // "fake" group
    templatePresentation.setText(ActionsBundle.groupText("MoreActionGroup"))
    templatePresentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
    templatePresentation.isHideGroupIfEmpty = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val uiKind = e.uiKind
    e.presentation.icon = when {
      uiKind is ActionUiKind.Toolbar && !uiKind.isHorizontal() -> AllIcons.Actions.MoreHorizontal
      else -> AllIcons.Actions.More
    }
  }
}