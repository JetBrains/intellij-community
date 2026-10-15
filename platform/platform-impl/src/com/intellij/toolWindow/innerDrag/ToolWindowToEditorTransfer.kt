// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.innerDrag

import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.ToolWindowEditorTabService
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.openapi.wm.impl.content.ToolWindowInEditorSupport
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content

/**
 * Represents a strategy for moving tool window content to the editor.
 *
 * There are two implementations:
 * 1. [ToolWindowInEditorSupport], represented by [LegacyTransfer]
 * 2. `ToolWindowEditorTabSupport` of the module `intellij.platform.ide.tabInEditor`, represented by [EditorTabTransfer]
 *
 * Use [findApplicableTransfer] to obtain a [ToolWindowToEditorTransfer] for the given content.
 */
internal sealed interface ToolWindowToEditorTransfer {
  /**
   * Moves the content into [editorWindow].
   *
   * @return `true` if the caller should unsplit the source decorator when it becomes empty;
   * `false` if the transfer implementation handles unsplitting itself.
   */
  fun move(editorWindow: EditorWindow): Boolean

  companion object {
    /**
     * Returns a transfer strategy for the given [content], or null if the content cannot be moved to the editor.
     *
     * - When [ToolWindowEditorTabService.isEnabled] is true,
     * `ToolWindowEditorTabSupport` of the module `intellij.platform.ide.tabInEditor` is used.
     * - Otherwise, [ToolWindowInEditorSupport] is used.
     */
    fun findApplicableTransfer(
      content: Content,
      sourceDecorator: InternalDecoratorImpl?,
      targetProject: Project,
    ): ToolWindowToEditorTransfer? {
      sourceDecorator ?: return null

      val editorTabService = ToolWindowEditorTabService.getInstanceOrNull()
      return if (editorTabService != null && editorTabService.isEnabled()) {
        createEditorTabTransfer(content, sourceDecorator, editorTabService)
      }
      else {
        createLegacyTransfer(content, sourceDecorator, targetProject)
      }
    }

    /**
     * Creates [ToolWindowToEditorTransfer] for the given [content] if it can be moved to the editor.
     *
     * @return the transfer instance, or `null` if the content cannot be transferred.
     */
    private fun createEditorTabTransfer(
      content: Content,
      sourceDecorator: InternalDecoratorImpl,
      service: ToolWindowEditorTabService,
    ): ToolWindowToEditorTransfer? {
      val toolWindow = sourceDecorator.toolWindow
      if (!service.canMoveContentToEditor(toolWindow, content)) {
        return null
      }

      return EditorTabTransfer(
        content = content,
        sourceDecorator = sourceDecorator,
        service = service,
      )
    }

    /**
     * Creates [ToolWindowToEditorTransfer] for the given [content] if it can be moved to the editor.
     *
     * @return the transfer instance, or `null` if the content cannot be transferred.
     */
    private fun createLegacyTransfer(
      content: Content,
      sourceDecorator: InternalDecoratorImpl,
      targetProject: Project,
    ): ToolWindowToEditorTransfer? {
      val support = ToolWindowContentUi.getToolWindowInEditorSupport(sourceDecorator.toolWindow)
                    ?: return null

      if (!support.canOpenInEditor(targetProject, content)) {
        return null
      }

      return LegacyTransfer(
        content = content,
        support = support,
      )
    }
  }
}

private class EditorTabTransfer(
  private val content: Content,
  private val sourceDecorator: InternalDecoratorImpl,
  private val service: ToolWindowEditorTabService,
) : ToolWindowToEditorTransfer {
  private val toolWindow = sourceDecorator.toolWindow

  override fun move(editorWindow: EditorWindow): Boolean {
    val isMoved = service.moveContentToEditor(toolWindow, content, editorWindow, sourceDecorator)
    if (isMoved) {
      recordActionByDrag(toolWindow, "MoveToolWindowTabToEditorAction", sourceDecorator)
    }
    return false
  }
}

private class LegacyTransfer(
  private val content: Content,
  private val support: ToolWindowInEditorSupport,
) : ToolWindowToEditorTransfer {

  override fun move(editorWindow: EditorWindow): Boolean {
    // The support should extract the toolWindow-specific component from the content object and open it in the editor.
    // The lifecycle of the passed content is also under the control of the support after this call.
    support.openInEditor(content, editorWindow)
    return true
  }
}
