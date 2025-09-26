// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.devkit.compose.preview

import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.currentCompositeKeyHashCode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import java.awt.BorderLayout

internal class ComposePreviewToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun shouldBeAvailable(project: Project): Boolean {
    return Registry.`is`("devkit.compose.desktop.preview")
  }

  @OptIn(ExperimentalJewelApi::class)
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val wrapperPanel = ComposePreviewBusyPanel(project)

    val contentManager = toolWindow.contentManager
    val toolWindowContent = contentManager.factory.createContent(wrapperPanel, "", false)
    toolWindowContent.isCloseable = false
    contentManager.addContent(toolWindowContent)

    val actionManager = ActionManager.getInstance()
    val titleActionsGroup = actionManager.getAction("ComposePreviewActions.Title") as DefaultActionGroup
    toolWindow.setTitleActions(titleActionsGroup.getChildren(actionManager).toList())

    project.service<ComposePreviewChangesTracker>().startTracking(project, toolWindowContent) { virtualFile ->
      wrapperPanel.setPaintBusy(true)

      val provider = try {
        compileCode(virtualFile, project)
      }
      catch (e: Throwable) {
        thisLogger().warn("Unable to compile code for preview", e)
        return@startTracking
      }
      finally {
        wrapperPanel.setPaintBusy(false)
      }

      if (provider == null) {
        withContext(Dispatchers.EDT) {
          wrapperPanel.displayUnsupportedFile()
        }
        return@startTracking
      }

      withContext(Dispatchers.EDT) {
        // free up the previous content JVM classes, register new
        try {
          wrapperPanel.getUserData(PROVIDER_KEY)?.classLoader?.close()
        }
        catch (e: Exception) {
          thisLogger().warn("Unable to release classloader for UI preview", e)
        }
        wrapperPanel.putUserData(PROVIDER_KEY, provider)

        wrapperPanel.setPaintBusy(false)
        wrapperPanel.removeAll()

        wrapperPanel.add(compose(focusOnClickInside = true) {
          provider.build(currentComposer, currentCompositeKeyHashCode)
        }, BorderLayout.CENTER)
      }
    }
  }
}

private val PROVIDER_KEY = Key.create<ContentProvider>("ComposePreviewToolWindowFactory.ContentProvider")
