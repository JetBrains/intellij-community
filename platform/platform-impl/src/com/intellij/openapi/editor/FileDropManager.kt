// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

@ApiStatus.Experimental
fun containsFileDropTargets(transferFlavors: Array<DataFlavor>): Boolean {
  return FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors)
}

private val LOG = Logger.getInstance(FileDropManager::class.java)

private val EP_NAME: ExtensionPointName<FileDropHandler> = ExtensionPointName("com.intellij.fileDropHandler")

@Service(Service.Level.PROJECT)
class FileDropManager(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  fun scheduleDrop(transferable: Transferable, editor: Editor?, editorWindowCandidate: EditorWindow?) {
    val fileList = FileCopyPasteUtil.getFileList(transferable) ?: return
    coroutineScope.launch {
      handleDrop(transferable, editor, editorWindowCandidate, fileList)
    }
  }

  suspend fun handleDrop(transferable: Transferable, editor: Editor?, editorWindowCandidate: EditorWindow?) {
    val fileList = FileCopyPasteUtil.getFileList(transferable) ?: return
    handleDrop(transferable = transferable, editor = editor, editorWindowCandidate = editorWindowCandidate, fileList = fileList)
  }

  private suspend fun handleDrop(transferable: Transferable,
                                 editor: Editor?,
                                 editorWindowCandidate: EditorWindow?,
                                 fileList: Collection<File>) {
    val event = FileDropEvent(project, transferable, fileList, editor)
    val dropHandled = (listOf(CustomFileDropHandlerBridge()) + EP_NAME.extensionList).any {
      try {
        it.handleDrop(event)
      }
      catch (e: Exception) {
        LOG.error("Unable to handle drop event in $it", e)
        false
      }
    }

    if (!dropHandled) {
      val editorWindow = editorWindowCandidate ?: readAction {
        if (editor?.isDisposed == true) return@readAction null

        findEditorWindow(project, editor)
      }

      openFiles(project = project, fileList = fileList, editorWindow = editorWindow)
    }
  }

  fun openFilesInTab(editor: Editor?, fileList: List<File>, editorWindowCandidate: EditorWindow? = null) {
    val editorWindow = editorWindowCandidate ?: findEditorWindow(project, editor)
    coroutineScope.launch {
      openFiles(project = project, fileList = fileList, editorWindow = editorWindow)
    }
  }

  private fun findEditorWindow(project: Project, editor: Editor?): EditorWindow? {
    val document = editor?.document ?: return null
    val file = FileDocumentManager.getInstance().getFile(document) ?: return null

    val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
    val windows = fileEditorManager.windows
    for (window in windows) {
      val composite = window.getComposite(file) ?: continue
      for (ed in composite.allEditors) {
        if (ed is TextEditor && ed.editor === editor) {
          return window
        }
      }
    }
    return null
  }

  private suspend fun openFiles(project: Project, fileList: Collection<File>, editorWindow: EditorWindow?) {
    val vFiles = withContext(Dispatchers.IO) {
      val fileSystem = LocalFileSystem.getInstance()
      fileList.mapNotNull { file -> fileSystem.refreshAndFindFileByIoFile(file) }
        .also { NonProjectFileWritingAccessProvider.allowWriting(it) }
    }

    withContext(Dispatchers.EDT) {
      blockingContext {
        for (vFile in vFiles) {
          if (editorWindow != null && !editorWindow.isDisposed) {
            val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
            val pair = fileEditorManager.openFile(vFile, editorWindow, FileEditorOpenOptions(requestFocus = true))
            if (pair.allEditors.isNotEmpty()) {
              continue
            }
          }

          PsiNavigationSupport.getInstance().createNavigatable(project, vFile, -1).navigate(true)
        }
      }
    }
  }
}

@Suppress("DEPRECATION")
private class CustomFileDropHandlerBridge : FileDropHandler {
  override suspend fun handleDrop(e: FileDropEvent): Boolean {
    val extensions = CustomFileDropHandler.CUSTOM_DROP_HANDLER_EP.getExtensions(e.project)
    if (extensions.isEmpty()) return false

    return withContext(Dispatchers.EDT) {
      blockingContext {
        extensions.any {
          it.canHandle(e.transferable, e.editor)
          && it.handleDrop(e.transferable, e.editor, e.project)
        }
      }
    }
  }
}
