// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class ToolWindowEditorTabTransferController(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  fun canMoveContentToEditor(toolWindow: ToolWindow, content: Content): Boolean {
    return ToolWindowEditorTabSupportUtil.isEnabled() && getSupport(toolWindow)?.canBeMovedToEditor(content) == true
  }

  fun moveContentToEditor(
    toolWindow: ToolWindow,
    content: Content,
    window: EditorWindow? = null,
    sourceDecorator: InternalDecoratorImpl? = null,
  ) {
    if (!canMoveContentToEditor(toolWindow, content)) return

    val support = getSupport(toolWindow) ?: return

    content.withTemporaryRemovedFlag {
      content.manager?.removeContent(content, false)
    }

    if (sourceDecorator != null && sourceDecorator.contentManager.isEmpty) {
      sourceDecorator.unsplit(null)
    }

    val file = createToolWindowTabFile(toolWindow, content, support)
    FileEditorManagerEx.getInstanceEx(project).openFile(
      file = file,
      window = window,
      options = FileEditorOpenOptions(
        requestFocus = true,
        // Keep the default: Wait for the editor composite to be fully opened so the check `isFileOpen`
        // observes the final result rather than racing an asynchronous open.
        waitForCompositeOpen = true,
      )
    )

    // Restore the content to the tool window if opening the editor tab failed. This is unexpected, but still possible.
    if (!FileEditorManager.getInstance(project).isFileOpen(file)) {
      restoreContentToToolWindow(content, toolWindow, sourceDecorator?.contentManager?.takeIf { !it.isDisposed })
      file.invalidate()
    }
  }

  fun canMoveContentToToolWindow(toolWindow: ToolWindow, file: ToolWindowEditorTabFile): Boolean {
    return ToolWindowEditorTabSupportUtil.isEnabled() && file.toolWindowId == toolWindow.id && getSupport(toolWindow) != null
  }

  fun moveContentToToolWindow(
    toolWindow: ToolWindow,
    file: ToolWindowEditorTabFile,
    targetDecorator: InternalDecoratorImpl? = null,
  ) {
    if (!canMoveContentToToolWindow(toolWindow, file)) return

    val editorManager = FileEditorManagerEx.getInstanceEx(project)
    val sourceWindow = editorManager.currentWindow?.takeIf { it.getComposite(file) != null }
                       ?: editorManager.windows.firstOrNull { it.getComposite(file) != null }

    file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    try {
      when (sourceWindow) {
        // During drag-and-drop of the last file, the source window may already be empty,
        // so use FileEditorManager.closeFile()
        null -> FileEditorManager.getInstance(project).closeFile(file)
        // Action "Return to Tool Window" closes the file in its original window.
        // The generic close path (via FileEditorManager.closeFile()) may unsplit
        // the editor and reselect a stale composite,
        // which triggers the exception.
        else -> editorManager.closeFile(file, sourceWindow)
      }
    }
    finally {
      file.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
    }

    restoreContentToToolWindow(file.content, toolWindow, targetDecorator?.contentManager)
    file.invalidate()
  }

  private fun getSupport(toolWindow: ToolWindow): ToolWindowEditorTabSupport? {
    return ToolWindowEditorTabSupportUtil.getSupport(toolWindow.id)
  }

  private fun createToolWindowTabFile(
    toolWindow: ToolWindow,
    content: Content,
    support: ToolWindowEditorTabSupport,
  ): ToolWindowEditorTabFile {
    val component = content.component
    return ToolWindowEditorTabFile(
      presentationFlow = support.getTabPresentationFlow(project, content),
      toolWindowId = toolWindow.id,
      component = component,
      preferredFocusedComponent =
        content.preferredFocusableComponent
        ?: component.getPreferredFocusedComponent()
        ?: component,
      content = content,
      project = project,
      parentCoroutineScope = coroutineScope,
    )
  }

  private fun restoreContentToToolWindow(
    content: Content,
    toolWindow: ToolWindow,
    targetContentManager: ContentManager?,
  ) {
    // This function may run while Content.TEMPORARY_REMOVED_KEY is set,
    // for example in the rollback path of moveContentToEditor().
    // Clear it here so addContent() uses the normal tool window restore path.
    content.withTemporaryRemovedFlagCleared {
      val manager = targetContentManager ?: toolWindow.contentManager
      manager.addContent(content)
      manager.setSelectedContent(content, true)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ToolWindowEditorTabTransferController = project.service()
  }
}

private inline fun Content.withTemporaryRemovedState(
  value: Boolean?,
  action: () -> Unit,
) {
  val initialState = getUserData(Content.TEMPORARY_REMOVED_KEY)
  putUserData(Content.TEMPORARY_REMOVED_KEY, value)
  try {
    action()
  }
  finally {
    putUserData(Content.TEMPORARY_REMOVED_KEY, initialState)
  }
}

private inline fun Content.withTemporaryRemovedFlag(action: () -> Unit) {
  withTemporaryRemovedState(true, action)
}

private inline fun Content.withTemporaryRemovedFlagCleared(action: () -> Unit) {
  withTemporaryRemovedState(null, action)
}
