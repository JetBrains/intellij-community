// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.innerDrag

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.recordActionInvoked
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.wm.ToolWindow
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Reports the action with [actionId] as invoked, because a drag of a tab of [toolWindow] did the same operation.
 *
 * The event goes to the `actions` FUS group, like a menu or a shortcut invocation of the action.
 * The input event `MouseLeftDrag` and the place [ActionPlaces.TOOLWINDOW_CONTENT] identify the drag.
 * Does nothing when no action has [actionId].
 */
internal fun recordActionByDrag(toolWindow: ToolWindow, actionId: String, dragSource: Component) {
  val action = ActionManager.getInstance().getAction(actionId) ?: return
  val dataContext = SimpleDataContext.builder()
    .setParent(DataContext.EMPTY_CONTEXT)
    .add(PlatformDataKeys.TOOL_WINDOW, toolWindow)
    .build()
  val event = AnActionEvent.createEvent(
    action,
    dataContext,
    null,
    ActionPlaces.TOOLWINDOW_CONTENT,
    ActionUiKind.NONE,
    MouseEvent(dragSource, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 0, 0, 0, false, MouseEvent.BUTTON1),
  )
  recordActionInvoked(toolWindow.project, action, event) { }
}
