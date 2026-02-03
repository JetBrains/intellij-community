// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.ui

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.withExplicitClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.IJSwingUtilities
import org.jetbrains.annotations.ApiStatus

private typealias ContentSupplier = (Content) -> Unit

@ApiStatus.Internal
object ToolWindowLazyContent {
  private val CONTENT_SUPPLIER: Key<ContentSupplier?> = Key.create("CONTENT_SUPPLIER")

  fun setContentSupplier(content: Content, supplier: ContentSupplier) {
    content.putUserData(CONTENT_SUPPLIER, supplier)
  }

  fun installInitializer(toolWindow: ToolWindow) {
    val listener = ContentInitializer(toolWindow)
    toolWindow.contentManager.addContentManagerListener(listener)
    Disposer.register(toolWindow.disposable, Disposable { toolWindow.contentManager.removeContentManagerListener(listener) })
    toolWindow.project.messageBus.connect(toolWindow.disposable).subscribe(ToolWindowManagerListener.TOPIC, listener)
  }

  fun initLazyContent(content: Content) {
    val provider = content.getUserData(CONTENT_SUPPLIER) ?: return
    content.putUserData(CONTENT_SUPPLIER, null)
    withExplicitClientId(ClientId.localId) {
      provider.invoke(content)
      IJSwingUtilities.updateComponentTreeUI(content.component)
    }
  }

  private class ContentInitializer(private val toolWindow: ToolWindow): ContentManagerListener, ToolWindowManagerListener {
    override fun stateChanged(toolWindowManager: ToolWindowManager) {
      if (toolWindow.isVisible) {
        val content = toolWindow.contentManager.selectedContent ?: return
        initLazyContent(content)
      }
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      if (toolWindow.isVisible) {
        initLazyContent(event.content)
      }
    }
  }
}