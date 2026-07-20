// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.annotations.RequiresEdt

@Service(Service.Level.PROJECT)
internal class ToolWindowEditorTabTransferController(
  private val project: Project,
) {
  fun canMoveContentToEditor(toolWindow: ToolWindow, content: Content): Boolean {
    if (!ToolWindowEditorTabSupportUtil.isEnabled()) return false

    val support = getSupport(toolWindow) ?: return false
    return support.getEditorTabDescriptor(toolWindow, content) != null
  }

  fun moveContentToEditor(
    toolWindow: ToolWindow,
    content: Content,
    window: EditorWindow? = null,
    sourceDecorator: InternalDecoratorImpl? = null,
  ) {
    if (!canMoveContentToEditor(toolWindow, content)) return

    val support = getSupport(toolWindow) ?: return
    val descriptor = support.getEditorTabDescriptor(toolWindow, content) ?: return

    // Keeps the content alive in the gap between disposing its current parent, the content manager,
    // and creating its new disposing parent, editorLifetime in the ToolWindowEditorTabFile.
    val transferLifetime = Disposer.newDisposable("ToolWindowContentTransfer:${toolWindow.id}")

    if (sourceDecorator != null &&
        (sourceDecorator.contentManager.isEmpty || // when the tab is dragging from the tool window to the editor, the manager is already empty
         (sourceDecorator.contentManager.contentCount == 1 && sourceDecorator.contentManager.getIndexOfContent(content) != -1))) {
      Disposer.register(transferLifetime, content)
      sourceDecorator.unsplit(content)
    }

    val file = createToolWindowTabFile(toolWindow, content, descriptor)
    FileEditorManagerEx.getInstanceEx(project).openFile(file, window, FileEditorOpenOptions(requestFocus = true))

    if (!FileEditorManager.getInstance(project).isFileOpen(file)) {
      restoreContentToToolWindow(content, toolWindow, sourceDecorator?.contentManager?.takeIf { !it.isDisposed })
      file.invalidateEditorTabFile()
    }
    else {
      file.bindContentToEditorLifetime()
      content.withTemporaryRemovedFlag {
        content.manager?.removeContent(content, false)
      }
    }
    Disposer.dispose(transferLifetime)
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

    // explicitly remove the file from recent files
    EditorHistoryManager.getInstance(project).removeFile(file)
    project.messageBus.syncPublisher(IdeDocumentHistoryImpl.RecentFileHistoryOrderListener.TOPIC).recentFileRemoved(file)

    restoreContentToToolWindow(file.content, toolWindow, targetDecorator?.contentManager)
    file.invalidateEditorTabFile()
  }

  @RequiresEdt
  fun updateEditorTabPresentation(
    toolWindow: ToolWindow,
    content: Content,
    descriptor: ToolWindowEditorTabDescriptor,
  ) {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val file = fileEditorManager.openFiles
                 .filterIsInstance<ToolWindowEditorTabFile>()
                 .firstOrNull { it.toolWindowId == toolWindow.id && it.content === content }
               ?: return

    file.updatePresentation(descriptor)
    fileEditorManager.updateFilePresentation(file)
  }

  private fun getSupport(toolWindow: ToolWindow): ToolWindowEditorTabSupport? {
    return ToolWindowEditorTabSupportUtil.getSupport(toolWindow.id)
  }

  private fun createToolWindowTabFile(
    toolWindow: ToolWindow,
    content: Content,
    descriptor: ToolWindowEditorTabDescriptor,
  ): ToolWindowEditorTabFile {
    val component = content.component
    return ToolWindowEditorTabFile(
      editorTitle = descriptor.title,
      toolWindowId = toolWindow.id,
      component = component,
      preferredFocusedComponent = content.preferredFocusableComponent ?: component.getPreferredFocusedComponent() ?: component,
      persistInEditorHistory = descriptor.persistInEditorHistory,
      content = content,
      tabIcon = descriptor.icon,
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

      content.manager?.let {
        contentManager -> Disposer.register(contentManager, content)
      }
    }
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
