// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.*
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
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object UndoDocumentUtil {

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

  @JvmStatic
  fun separateLocalAndNonLocalActions(
    actions: List<UndoableAction>,
    affectedDocument: DocumentReference,
  ): Pair<List<UndoableAction>, List<UndoableAction>> {
    val localActions: MutableList<UndoableAction> = SmartList<UndoableAction>()
    val nonLocalActions: MutableList<UndoableAction> = SmartList<UndoableAction>()
    for (action in actions) {
      val affectedDocuments = action.getAffectedDocuments()
      if (affectedDocuments != null && affectedDocuments.size == 1 && affectedDocuments[0] == affectedDocument) {
        localActions.add(action)
      } else {
        nonLocalActions.add(action)
      }
    }
    return Pair(localActions, nonLocalActions)
  }

  @JvmStatic
  fun collectReadOnlyAffectedFiles(actions: List<UndoableAction>): Collection<VirtualFile> {
    val readOnlyFiles = ArrayList<VirtualFile>()
    for (action in actions) {
      if (action is MentionOnlyUndoableAction) {
        continue
      }
      val refs: Array<DocumentReference>? = action.getAffectedDocuments()
      if (refs == null) {
        continue
      }
      for (ref in refs) {
        val file = ref.getFile()
        if (file != null && file.isValid() && !file.isWritable()) {
          readOnlyFiles.add(file)
        }
      }
    }
    return readOnlyFiles
  }

  @JvmStatic
  fun collectReadOnlyDocuments(actions: List<UndoableAction>): Collection<Document> {
    val readOnlyDocs = ArrayList<Document>()
    for (action in actions) {
      if (action is MentionOnlyUndoableAction) {
        continue
      }
      val refs: Array<DocumentReference>? = action.getAffectedDocuments()
      if (refs == null) {
        continue
      }
      for (ref in refs) {
        if (ref is DocumentReferenceByDocument) {
          val doc = ref.document
          if (!doc.isWritable()) {
            readOnlyDocs.add(doc)
          }
        }
      }
    }
    return readOnlyDocs
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
