// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.skipWindowDeactivationEvents
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.TextPresentation
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

internal class SkipWindowDeactivationEventsAction : DumbAwareToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    return skipWindowDeactivationEvents
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    changeWindowDeactivationEventsStatus(state)
  }
}

private const val STATUS_BAR_WIDGET_ID: String = "SkipWindowDeactivationEventsStatus"

private fun changeWindowDeactivationEventsStatus(state: Boolean) {
  skipWindowDeactivationEvents = state
  manageWidget(state)
}

private fun manageWidget(enabled: Boolean) {
  for (project in ProjectManager.getInstance().openProjects) {
    val statusBar = WindowManager.getInstance().getStatusBar(project) ?: continue
    if (statusBar.getWidget(STATUS_BAR_WIDGET_ID) == null) {
      if (enabled) {
        // Do not register StatusBarWidgetFactory extension for internal action.
        @Suppress("DEPRECATION")
        statusBar.addWidget(StatusWidget(), "before " + StatusBar.StandardWidgets.POSITION_PANEL)
        statusBar.updateWidget(STATUS_BAR_WIDGET_ID)
      }
    }
    else if (!enabled) {
      statusBar.removeWidget(STATUS_BAR_WIDGET_ID)
    }
  }
}

private class StatusWidget : StatusBarWidget, TextPresentation {
  override fun ID(): String = STATUS_BAR_WIDGET_ID

  override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

  override fun getTooltipText(): String = "Click to re-enable window deactivation events"

  override fun getClickConsumer(): Consumer<MouseEvent> = Consumer<MouseEvent> {
    changeWindowDeactivationEventsStatus(false)
  }

  override fun getText(): String = "WD events are skipped!"

  override fun getAlignment(): Float = Component.CENTER_ALIGNMENT
}