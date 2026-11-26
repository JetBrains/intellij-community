// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector

import com.intellij.devkit.icons.inspector.model.IconInspectorControllerImpl
import com.intellij.devkit.icons.inspector.picker.GlobalPickGesture
import com.intellij.devkit.icons.inspector.ui.IconInspectorPanel
import com.intellij.devkit.icons.inspector.ui.IconInspectorWindows
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab

internal class IconInspectorToolWindowFactory : ToolWindowFactory, DumbAware {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    val internal = ApplicationManager.getApplication().isInternal
    // The global pick gesture (works in any window, incl. modal dialogs) must not depend on the tool window being opened.
    // This check runs anyway when the project opens, so installing it here adds no work for non-internal users.
    if (internal) GlobalPickGesture.getInstance().ensureInstalled()
    return internal
  }

  override fun shouldBeAvailable(project: Project): Boolean = ApplicationManager.getApplication().isInternal

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val controller = project.service<IconInspectorControllerImpl>()
    project.service<IconInspectorWindows>().ensureWatching()
    toolWindow.setTitleActions(listOfNotNull(ActionManager.getInstance().getAction(DETACH_ACTION_ID)))
    toolWindow.addComposeTab(DevkitIconsInspectorBundle.message("toolwindow.stripe.Icon_Inspector")) {
      IconInspectorPanel(controller)
    }
  }
}
