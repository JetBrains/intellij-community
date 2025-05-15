// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.DocumentReferenceProvider
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project


internal object UndoDocumentUtil {

  @JvmStatic
  fun getDocRefs(editor: FileEditor?): Collection<DocumentReference>? {
    if (editor is TextEditor && editor.getEditor().isViewer()) {
      return null
    }
    if (editor == null) {
      return emptyList()
    }
    return getDocumentReferences(editor)
  }

  @JvmStatic
  fun getDocumentReferences(editor: FileEditor): Collection<DocumentReference> {
    if (editor is DocumentReferenceProvider) {
      return editor.getDocumentReferences()
    }
    return TextEditorProvider.getDocuments(editor).filter { document ->
      // KirillK : in AnAction.update we may have an editor with an invalid file
      val file = FileDocumentManager.getInstance().getFile(document)
      file == null || file.isValid()
    }.map { document ->
      val original = getOriginalDocument(document)
      DocumentReferenceManager.getInstance().create(original)
    }.toList()
  }

  @JvmStatic
  fun getDocReference(project: Project, editorProvider: CurrentEditorProvider): DocumentReference? {
    val editor = getCurrentEditor(project, editorProvider)
    if (editor != null) {
      val file = FileDocumentManager.getInstance().getFile(editor.getDocument())
      if (file != null && file.isValid()) {
        return DocumentReferenceManager.getInstance().create(file)
      }
    }
    return null
  }

  @JvmStatic
  fun isDocumentOpened(project: Project?, docRef: DocumentReference): Boolean {
    val file = docRef.getFile()
    if (file != null) {
      if (project != null && FileEditorManager.getInstance(project).isFileOpen(file)) {
        return true
      }
    } else {
      val document = docRef.getDocument()
      if (document != null && EditorFactory.getInstance().editors(document, project).findAny().isPresent) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun isCopy(document: Document): Boolean {
    return document.getUserData(UndoManager.ORIGINAL_DOCUMENT) != null
  }

  private fun getCurrentEditor(project: Project, editorProvider: CurrentEditorProvider): Editor? {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      return CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext())
    } else {
      val fileEditor = editorProvider.getCurrentEditor(project)
      if (fileEditor is TextEditor) {
        return fileEditor.getEditor()
      }
    }
    return null
  }

  private fun getOriginalDocument(document: Document): Document {
    return document.getUserData(UndoManager.ORIGINAL_DOCUMENT) ?: document
  }
}
