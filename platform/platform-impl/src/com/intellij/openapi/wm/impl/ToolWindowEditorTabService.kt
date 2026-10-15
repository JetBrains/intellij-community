// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus

/**
 * Opens tool window tabs as editor tabs.
 * The module `intellij.platform.ide.tabInEditor` registers the implementation as an application service.
 * Without the module, [getInstanceOrNull] returns `null`.
 */
@ApiStatus.Internal
interface ToolWindowEditorTabService {
  companion object {
    @JvmStatic
    fun getInstanceOrNull(): ToolWindowEditorTabService? =
      ApplicationManager.getApplication().getService(ToolWindowEditorTabService::class.java)
  }

  /**
   * Returns `true` if tool window tabs can open as editor tabs.
   */
  fun isEnabled(): Boolean

  /**
   * Returns `true` if a plugin supports editor tabs for the tool window with [toolWindowId].
   */
  fun hasSupport(toolWindowId: String): Boolean

  /**
   * Makes the tool window with [toolWindowId] a drop target for editor tabs.
   */
  fun installDockContainer(project: Project, toolWindowId: String, decorator: InternalDecorator)

  fun canMoveContentToEditor(toolWindow: ToolWindow, content: Content): Boolean

  /**
   * Moves [content] from [toolWindow] into [window] as an editor tab.
   *
   * @return `true` if the editor opened the content; `false` if the content stayed in the tool window.
   */
  fun moveContentToEditor(toolWindow: ToolWindow, content: Content, window: EditorWindow, sourceDecorator: InternalDecoratorImpl): Boolean
}
