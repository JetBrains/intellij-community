// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector

import com.intellij.devkit.icons.inspector.model.IconInspectorControllerImpl
import com.intellij.devkit.icons.inspector.picker.GlobalPickGesture
import com.intellij.devkit.icons.inspector.ui.IconInspectorWindows
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Starts the hover picker. Enabled in modal contexts, so a keymap shortcut assigned to it also works inside modal dialogs;
 * there the tool window's frame is blocked, so the inspector opens in a separate window owned by the dialog instead.
 * In the detached mode, the inspector always opens in the detached window (see IconInspectorWindows).
 */
internal class PickIconAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = ApplicationManager.getApplication().isInternal && e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    GlobalPickGesture.getInstance().ensureInstalled()
    val controller = project.service<IconInspectorControllerImpl>()
    val windows = project.service<IconInspectorWindows>()
    windows.ensureWatching()
    val context = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) ?: IdeFocusManager.getInstance(project).focusOwner
    if (windows.usesSeparateWindow(context)) {
      // show the separate inspector window first, then pick: the picker also hooks the newly opened window
      windows.showResults(context)
      controller.startPicking()
      return
    }
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ICON_INSPECTOR_TOOLWINDOW_ID)
    if (toolWindow == null) {
      controller.startPicking()
      return
    }
    // start picking once the tool window is shown, so its own activation does not interfere with the picker
    toolWindow.activate({ controller.startPicking() }, false, false)
  }
}
