// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.devkit.compose.preview

import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.currentCompositeKeyHashCode
import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.StatusText
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
    val wrapperPanel = object : JBPanelWithEmptyText(), DumbAware {
    }
    wrapperPanel.setLayout(BorderLayout())

    val emptyText = wrapperPanel.emptyText
    emptyText.isCenterAlignText = false
    emptyText.clear()
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.empty.text.top"))
    emptyText.appendLine(DevkitComposeBundle.message("compose.preview.empty.text.compile"))
    emptyText.appendLine("")
    appendBuildHintText(project, emptyText)

    val contentManager = toolWindow.contentManager
    val toolWindowContent = contentManager.factory.createContent(wrapperPanel, "", false)
    toolWindowContent.isCloseable = false
    contentManager.addContent(toolWindowContent)

    val actionManager = ActionManager.getInstance()
    val titleActionsGroup = actionManager.getAction("ComposePreviewActions.Title") as DefaultActionGroup
    toolWindow.setTitleActions(titleActionsGroup.getChildren(actionManager).toList())

    project.service<EditorSelectionAndTextChangesTracker>().startTracking(project, toolWindowContent) { virtualFile ->
      try {
        val provider = compileCode(virtualFile, project) ?: return@startTracking

        withContext(Dispatchers.EDT) {
          // free up the previous content JVM classes, register new
          try {
            wrapperPanel.getUserData(PROVIDER_KEY)?.classLoader?.close()
          }
          catch (e: Exception) {
            thisLogger().error("Unable to release classloader for UI preview", e)
          }

          wrapperPanel.putUserData(PROVIDER_KEY, provider)
          wrapperPanel.removeAll()

          wrapperPanel.add(compose(focusOnClickInside = true) {
            provider.build(currentComposer, currentCompositeKeyHashCode)
          }, BorderLayout.CENTER)
        }
      }
      catch (e: Throwable) {
        thisLogger().error(e)
      }
    }
  }
}

private fun appendBuildHintText(project: Project, text: StatusText) {
  val shortcut = ActionManager.getInstance().getKeyboardShortcut("CompileDirty")
  text.appendLine(DevkitComposeBundle.message("compose.preview.build"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
    val compileAction = ActionManager.getInstance().getAction("CompileDirty")!!
    val dataContext = SimpleDataContext.builder().add(PROJECT, project).build()
    ActionUtil.performAction(compileAction, AnActionEvent.createEvent(dataContext, Presentation(), "", ActionUiKind.NONE, null))
  }
  val shortcutText = shortcut?.let { " (${KeymapUtil.getShortcutText(shortcut)})" } ?: ""
  text.appendText(shortcutText)
}

private val PROVIDER_KEY = Key.create<ContentProvider>("ComposePreviewToolWindowFactory.ContentProvider")
