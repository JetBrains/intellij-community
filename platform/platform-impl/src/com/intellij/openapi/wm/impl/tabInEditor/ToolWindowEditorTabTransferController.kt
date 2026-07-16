// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.openapi.wm.ToolWindow
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class ToolWindowEditorTabTransferController(
  private val project: Project,
) {
  private val collector = KeyedExtensionCollector<ToolWindowEditorTabSupport, String>("com.intellij.toolWindowEditorTabSupport")

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
      restoreContentToToolWindow(content, toolWindow, sourceDecorator?.contentManager.takeIf { !it?.isDisposed!! })
      file.releaseEditorLifetime()
      file.isValid = false
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
    return ToolWindowEditorTabSupportUtil.isEnabled() &&
           file.toolWindowId == toolWindow.id &&
           file.content != null &&
           getSupport(toolWindow) != null
  }

  fun moveContentToToolWindow(
    toolWindow: ToolWindow,
    file: ToolWindowEditorTabFile,
    targetDecorator: InternalDecoratorImpl? = null,
  ) {
    if (!canMoveContentToToolWindow(toolWindow, file)) return

    val content = file.content ?: return

    file.withSkippedCloseHandler {
      FileEditorManager.getInstance(project).closeFile(file)
    }

    EditorHistoryManager.getInstance(project).removeFile(file)
    project.messageBus.syncPublisher(IdeDocumentHistoryImpl.RecentFileHistoryOrderListener.TOPIC).recentFileRemoved(file)

    restoreContentToToolWindow(content, toolWindow, targetDecorator?.contentManager)
    file.releaseEditorLifetime()

    file.isValid = false
  }

  private fun getSupport(toolWindow: ToolWindow): ToolWindowEditorTabSupport? {
    return collector.forKey(toolWindow.id).firstOrNull()
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
      fileType = descriptor.toFileType(),
      component = component,
      content.preferredFocusableComponent ?: component.getPreferredFocusedComponent() ?: component,
      persistInEditorHistory = descriptor.persistInEditorHistory,
      content = content,
      onEditorClosed = { file ->
        file.isValid = false
        file.releaseEditorLifetime()
      },
    )
  }

  private fun restoreContentToToolWindow(
    content: Content,
    toolWindow: ToolWindow,
    targetContentManager: ContentManager?,
  ) {
    content.withTemporaryRemovedFlagCleared {
      val manager = targetContentManager ?: toolWindow.contentManager
      manager.addContent(content)
      manager.setSelectedContent(content, true)
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
