// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
internal class EditorSelectionAndTextChangesTracker(val project: Project, val coroutineScope: CoroutineScope) {

  fun startTracking(project: Project, disposable: Disposable, processor: suspend (VirtualFile) -> Unit) {
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      observeEditorContentChanges(project, disposable)
        .debounce(1000L)
        .distinctUntilChanged()
        .collect { (_, virtualFile) ->
          processor(virtualFile)
        }
    }
  }

  private fun observeEditorContentChanges(project: Project, disposable: Disposable): Flow<Pair<String, VirtualFile>> {
    return callbackFlow {
      var currentEditor: Editor? = null
      var documentListener: DocumentListener? = null

      // Create message bus connection
      val connection: MessageBusConnection = project.messageBus.connect(disposable)

      // Helper function to remove listeners from the current editor
      val removeCurrentListeners = {
        documentListener?.let { listener -> currentEditor?.document?.removeDocumentListener(listener) }
        documentListener = null
      }

      // Helper function to add listeners to the new active editor
      val addListenersToCurrentEditor = { editor: Editor, file: VirtualFile ->
        // Remove old listeners first
        removeCurrentListeners()

        currentEditor = editor

        // Create and add a document listener
        documentListener = object : DocumentListener {
          override fun documentChanged(event: DocumentEvent) {
            val text = event.document.text
            trySend(text to file)
          }
        }
        currentEditor!!.document.addDocumentListener(documentListener!!, disposable)
      }

      // File editor manager listener for editor selection changes
      val fileEditorManagerListener = object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          val newFile = event.newFile
          val newEditor = (event.newEditor as? TextEditor)?.editor ?: return

          if (newFile != null) {
            val text = newEditor.document.text
            trySend(text to newFile)
            addListenersToCurrentEditor(newEditor, newFile)
          }
          else {
            // No active editor
            removeCurrentListeners()
            currentEditor = null
          }
        }
      }

      // Register the file editor manager listener
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorManagerListener)

      // Initialize with a currently active editor if any
      val fileEditorManager = FileEditorManager.getInstance(project)
      val selectedEditor = fileEditorManager.selectedEditor as? TextEditor
      val selectedFile = fileEditorManager.selectedFiles.firstOrNull()

      if (selectedEditor != null && selectedFile != null) {
        trySend("" to selectedFile)
        addListenersToCurrentEditor(selectedEditor.editor, selectedFile)
      }

      // Handle cleanup when the flow is canceled
      awaitClose {
        removeCurrentListeners()
        connection.disconnect()
      }
    }
  }
}