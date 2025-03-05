// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ToolWindowContentPostProcessor
import com.intellij.platform.vcs.impl.frontend.shelf.ShelfTreeUpdater.Companion.CONTENT_PROVIDER_SUPPLIER_KEY
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShelveContentReplacer() : ToolWindowContentPostProcessor {
  override fun isEnabled(project: Project, content: Content, toolWindow: ToolWindow): Boolean {
    return Registry.`is`("vcs.shelves.rhizome.enabled") && toolWindow.id == ToolWindowId.COMMIT && content.tabName == SHELF_CONTENT_NAME
  }

  override fun postprocessContent(project: Project, content: Content, toolWindow: ToolWindow) {
    val listener = ShelfTabListener(toolWindow)
    content.putUserData(CONTENT_PROVIDER_SUPPLIER_KEY) { ShelfTreeUpdater.getInstance(project).createToolWindowPanel() }
    toolWindow.contentManager.addContentManagerListener(listener)
  }

  class ShelfTabListener(val toolWindow: ToolWindow) : ContentManagerListener, ToolWindowManagerListener {
    override fun stateChanged(toolWindowManager: ToolWindowManager) {
      val selectedContent = toolWindow.contentManager.selectedContent
      if (toolWindow.isVisible && SHELF_CONTENT_NAME == selectedContent?.tabName) {
        initContent(selectedContent)
      }
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      val content = event.content
      if (toolWindow.isVisible && event.operation == ContentManagerEvent.ContentOperation.add && SHELF_CONTENT_NAME == content.tabName) {
        initContent(content)
      }
    }

    private fun initContent(content: Content) {
      val toolWindowPanel = content.getUserData(CONTENT_PROVIDER_SUPPLIER_KEY)?.invoke() ?: return
      content.putUserData(CONTENT_PROVIDER_SUPPLIER_KEY, null)
      content.component = toolWindowPanel
    }
  }
}

private const val SHELF_CONTENT_NAME = "Shelf"