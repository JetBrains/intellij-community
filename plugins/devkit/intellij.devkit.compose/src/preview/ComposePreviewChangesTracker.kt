// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.ide.util.PropertiesComponent
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

private val manualRefreshCounter = AtomicInteger(0)

enum class RefreshReason {
  INITIAL,
  CHANGE,
  MANUAL {
    override fun toHash(): Int {
      return manualRefreshCounter.addAndGet(1)
    }
  };

  open fun toHash(): Int = ordinal
}

private data class RefreshSignal(
  val text: String?,
  val file: VirtualFile,
  val reason: RefreshReason,
  val hash: Int,
) {
  constructor(text: String?, file: VirtualFile, reason: RefreshReason) : this(text, file, reason, reason.toHash())
}

private const val REFRESH_SETTING_KEY = "compose.preview.refresh.auto"

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
internal class ComposePreviewChangesTracker(val project: Project, val coroutineScope: CoroutineScope) {
  @Volatile
  private var refreshCallback: (() -> Unit)? = null

  private val autoRefresh: MutableStateFlow<Boolean> =
    MutableStateFlow(PropertiesComponent.getInstance().getBoolean(REFRESH_SETTING_KEY, false))

  fun setAutoRefresh(value: Boolean) {
    autoRefresh.value = value
    PropertiesComponent.getInstance().setValue(REFRESH_SETTING_KEY, value)
  }

  fun isAutoRefresh(): Boolean {
    return autoRefresh.value
  }

  fun startTracking(project: Project, disposable: Disposable, processor: suspend (VirtualFile) -> Unit) {
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      val changesFlow = observeEditorContentChanges(project, disposable)
        .debounce(1000L)
        .distinctUntilChanged()
        .filter { autoRefresh.value }

      val manualRefreshFlow = observeManualRefresh()

      merge(changesFlow, manualRefreshFlow)
        .conflate() // drop older refreshes, there is no need to process them
        .collect { (_, virtualFile) ->
          processor(virtualFile)
        }
    }
  }

  fun refresh() {
    refreshCallback?.invoke()
  }

  private fun observeManualRefresh(): Flow<RefreshSignal> {
    return callbackFlow {
      refreshCallback = {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedEditor = fileEditorManager.selectedEditor as? TextEditor
        val selectedFile = fileEditorManager.selectedFiles.firstOrNull()

        if (selectedEditor != null && selectedFile != null) {
          trySend(RefreshSignal(null, selectedFile, RefreshReason.MANUAL))
        }
      }

      awaitClose {  }
    }
  }

  private fun observeEditorContentChanges(project: Project, disposable: Disposable): Flow<RefreshSignal> {
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
            trySend(RefreshSignal(text, file, RefreshReason.CHANGE))
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
            trySend(RefreshSignal(text, newFile, RefreshReason.CHANGE))
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
        trySend(RefreshSignal(null, selectedFile, RefreshReason.INITIAL))
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