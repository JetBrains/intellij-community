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

    removeContentFromContentManager(content, sourceDecorator)

    val file = createToolWindowTabFile(toolWindow, content, descriptor)
    FileEditorManagerEx.getInstanceEx(project).openFile(file, window, FileEditorOpenOptions(requestFocus = true))
    if (!FileEditorManager.getInstance(project).isFileOpen(file)) {
      restoreContentToToolWindow(content, toolWindow, sourceDecorator?.contentManager)
      file.isValid = false
    }
  }

  fun canMoveContentToToolWindow(toolWindow: ToolWindow, file: ToolWindowEditorTabFile): Boolean {
    if (!ToolWindowEditorTabSupportUtil.isEnabled()) return false

    return file.toolWindowId == toolWindow.id && file.content != null && getSupport(toolWindow) != null
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

    file.isValid = false

    restoreContentToToolWindow(content, toolWindow, targetDecorator?.contentManager)
  }

  private fun removeContentFromContentManager(
    content: Content,
    sourceDecorator: InternalDecoratorImpl?,
  ) {
    if (sourceDecorator != null && (sourceDecorator.contentManager.isEmpty ||
                                    (sourceDecorator.contentManager.contentCount == 1 &&
                                     sourceDecorator.contentManager.getIndexOfContent(content) != -1))) {
      val temporaryHostDecorator = sourceDecorator.findTemporaryHostDecorator()

      content.withTemporaryRemovedFlag {
        sourceDecorator.setSplitUnsplitInProgress(true)
        try {
          sourceDecorator.contentManager.removeContent(content, false)
        }
        finally {
          sourceDecorator.setSplitUnsplitInProgress(false)
        }
      }

      if (temporaryHostDecorator == null) return
      val temporaryHostContentManager = temporaryHostDecorator.contentManager
      temporaryHostContentManager.addContent(content, -1)
      temporaryHostContentManager.setSelectedContent(content, false)

      if (sourceDecorator.contentManager.isEmpty) {
        sourceDecorator.unsplit(content)
      }
    }

    content.withTemporaryRemovedFlag {
      content.manager?.removeContent(content, false)
    }
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
        content.release()
      },
    )
  }

  private fun restoreContentToToolWindow(
    content: Content,
    toolWindow: ToolWindow,
    targetContentManager: ContentManager?,
  ) {
    content.withTemporaryRemovedFlag {
      val manager = targetContentManager ?: toolWindow.contentManager
      manager.addContent(content)
      manager.setSelectedContent(content, true)
    }
  }
}

private fun InternalDecoratorImpl.findTemporaryHostDecorator(): InternalDecoratorImpl? {
  val sourceDecorator = this

  return InternalDecoratorImpl.findTopLevelDecorator(sourceDecorator)
    ?.getOrderedCells()
    ?.firstOrNull { decorator ->
      decorator != sourceDecorator && !decorator.contentManager.isEmpty
    }
}

private inline fun Content.withTemporaryRemovedFlag(action: () -> Unit) {
  putUserData(Content.TEMPORARY_REMOVED_KEY, true)
  try {
    action()
  }
  finally {
    putUserData(Content.TEMPORARY_REMOVED_KEY, null)
  }
}
