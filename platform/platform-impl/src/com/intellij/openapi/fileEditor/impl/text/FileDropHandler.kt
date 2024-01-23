// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.CustomFileDropHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

open class FileDropHandler(private val myEditor: Editor?) : EditorDropHandler {
  override fun canHandleDrop(transferFlavors: Array<DataFlavor>): Boolean {
    return FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors)
  }

  override fun handleDrop(t: Transferable, project: Project?, editorWindowCandidate: EditorWindow?) {
    if (project == null) return
    val fileList = FileCopyPasteUtil.getFileList(t) ?: return

    val dropResult = ContainerUtil.process(CustomFileDropHandler.CUSTOM_DROP_HANDLER_EP.getExtensions(project)
    ) { handler: CustomFileDropHandler -> !(handler.canHandle(t, myEditor) && handler.handleDrop(t, myEditor, project)) }
    if (!dropResult) return

    val editorWindow = editorWindowCandidate ?: findEditorWindow(project)
    (project as ComponentManagerEx).getCoroutineScope().launch {
      openFiles(project = project, fileList = fileList, editorWindow = editorWindow)
    }
  }

  private suspend fun openFiles(project: Project, fileList: List<File>, editorWindow: EditorWindow?) {
    val vFiles = withContext(Dispatchers.IO) {
      val fileSystem = LocalFileSystem.getInstance()
      fileList.mapNotNull { file -> fileSystem.refreshAndFindFileByIoFile(file) }
        .also { NonProjectFileWritingAccessProvider.allowWriting(it) }
    }

    withContext(Dispatchers.EDT) {
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

  private fun findEditorWindow(project: Project): EditorWindow? {
    val document = myEditor?.document ?: return null
    val file = FileDocumentManager.getInstance().getFile(document) ?: return null

    val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
    val windows = fileEditorManager.windows
    for (window in windows) {
      val composite = window.getComposite(file) ?: continue
      for (editor in composite.allEditors) {
        if (editor is TextEditor && editor.editor === myEditor) {
          return window
        }
      }
    }
    return null
  }
}