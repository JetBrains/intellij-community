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
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.openapi.wm.impl.content.ToolWindowInEditorSupport
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabSupportUtil
import com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabTransferController
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content
import java.awt.event.MouseEvent

/**
 * Represents a strategy for moving tool window content to the editor.
 *
 * There are two implementations:
 * 1. [ToolWindowInEditorSupport], represented by [LegacyTransfer]
 * 2. [com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabSupport], represented by [EditorTabTransfer]
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
     * - When [ToolWindowEditorTabSupportUtil.isEnabled] is true,
     * [com.intellij.openapi.wm.impl.tabInEditor.ToolWindowEditorTabSupport] is used.
     * - Otherwise, [ToolWindowInEditorSupport] is used.
     */
    fun findApplicableTransfer(
      content: Content,
      sourceDecorator: InternalDecoratorImpl?,
      targetProject: Project,
    ): ToolWindowToEditorTransfer? {
      sourceDecorator ?: return null

      return if (ToolWindowEditorTabSupportUtil.isEnabled()) {
        createEditorTabTransfer(content, sourceDecorator)
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
    ): ToolWindowToEditorTransfer? {
      val toolWindow = sourceDecorator.toolWindow
      val controller = ToolWindowEditorTabTransferController.getInstance(toolWindow.project)

      if (!controller.canMoveContentToEditor(toolWindow, content)) {
        return null
      }

      return EditorTabTransfer(
        content = content,
        sourceDecorator = sourceDecorator,
        controller = controller,
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
  private val controller: ToolWindowEditorTabTransferController,
) : ToolWindowToEditorTransfer {
  private val toolWindow = sourceDecorator.toolWindow

  override fun move(editorWindow: EditorWindow): Boolean {
    recordMoveToEditorByDrag()
    controller.moveContentToEditor(toolWindow, content, editorWindow, sourceDecorator)
    return false
  }

  private fun recordMoveToEditorByDrag() {
    val action = ActionManager.getInstance().getAction("MoveToolWindowTabToEditorAction") ?: return
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
      MouseEvent(sourceDecorator, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 0, 0, 0, false, MouseEvent.BUTTON1),
    )
    recordActionInvoked(toolWindow.project, action, event) { }
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
