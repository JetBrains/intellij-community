// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.ide.ui.customization.GroupEnd
import com.intellij.ide.ui.customization.ToolbarAddQuickActionInfo
import com.intellij.ide.ui.customization.ToolbarAddQuickActionsAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.impl.actions.XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP
import com.intellij.xdebugger.impl.actions.XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP

private val insertStrategy = GroupEnd(TOOL_WINDOW_TOP_TOOLBAR_3_GROUP)

private class DebuggerToolbarQuickActionsGroup : ActionGroup(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val actionManager = ActionManager.getInstance()
    val mainGroup = actionManager.getAction(TOOL_WINDOW_TOP_TOOLBAR_3_GROUP) as ActionGroup
    val extraGroup = actionManager.getAction(TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP) as ActionGroup
    return (mainGroup.getChildren(e) + extraGroup.getChildren(e)).mapNotNull {
      if (it is Separator) return@mapNotNull it
      val id = actionManager.getId(it)
      val name = it.templatePresentation.text
      if (id == null || it is ActionGroup || name == null) return@mapNotNull null
      ToolbarAddQuickActionsAction(ToolbarAddQuickActionInfo(listOf(id), name, it.templatePresentation.icon, insertStrategy))
    }.toTypedArray()
  }
}