// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.util.PsiNavigationSupport
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
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

open class FileDropHandler(private val myEditor: Editor?) : EditorDropHandler {
  override fun canHandleDrop(transferFlavors: Array<DataFlavor>): Boolean {
    return FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors)
  }

  override fun handleDrop(t: Transferable, project: Project?, editorWindow: EditorWindow?) {
    if (project != null) {
      val fileList = FileCopyPasteUtil.getFileList(t)
      if (fileList != null) {
        val dropResult = ContainerUtil.process(CustomFileDropHandler.CUSTOM_DROP_HANDLER_EP.getExtensions(project)
        ) { handler: CustomFileDropHandler -> !(handler.canHandle(t, myEditor) && handler.handleDrop(t, myEditor, project)) }
        if (!dropResult) return
        openFiles(project, fileList, editorWindow)
      }
    }
  }

  private fun openFiles(project: Project, fileList: List<File>, editorWindowCandidate: EditorWindow?) {
    val editorWindow = editorWindowCandidate ?: findEditorWindow(project)

    val fileSystem = LocalFileSystem.getInstance()
    for (file in fileList) {
      val vFile = fileSystem.refreshAndFindFileByIoFile(file)
      val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
      if (vFile != null) {
        NonProjectFileWritingAccessProvider.allowWriting(listOf(vFile))
        if (editorWindow != null) {
          val pair = fileEditorManager.openFile(vFile, editorWindow, FileEditorOpenOptions().withRequestFocus())
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