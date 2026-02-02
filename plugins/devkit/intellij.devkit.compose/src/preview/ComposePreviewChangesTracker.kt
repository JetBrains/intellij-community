// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.TOPIC
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

private val manualRefreshCounter = AtomicInteger(0)

internal enum class RefreshReason {
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
      val changesFlow = observeEditorContentChanges(disposable)
        .debounce(1000L)
        .distinctUntilChanged()
        .filter { isPreviewVisible() }
        .filter { autoRefresh.value }

      val manualRefreshFlow = observeManualRefresh()

      merge(changesFlow, manualRefreshFlow)
        .conflate() // drop older refreshes, there is no need to process them
        .collect { (_, virtualFile) ->
          writeAction {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            FileDocumentManager.getInstance().saveAllDocuments()
          }

          try {
            processor(virtualFile)
          }
          catch (e: Throwable) {
            thisLogger().error("Error during Compose UI Preview refresh chain", e)
          }
        }
    }
  }

  private fun isPreviewVisible(): Boolean {
    return ToolWindowManager.getInstance(project).getToolWindow(TOOLWINDOW_ID)?.isVisible ?: return false
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

      awaitClose { }
    }
  }

  private fun observeEditorContentChanges(disposable: Disposable): Flow<RefreshSignal> {
    return callbackFlow {
      val connection = project.messageBus.connect(disposable)

      connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener {
        val selectedFile = getSelectedEditorFile()
        if (selectedFile != null) {
          val editor = FileEditorManager.getInstance(project).getSelectedEditor(selectedFile)
          val text = (editor as? TextEditor)?.editor?.document?.text

          trySend(RefreshSignal(text, selectedFile, RefreshReason.CHANGE))
        }
      })

      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          val newFile = event.newFile
          val newEditor = (event.newEditor as? TextEditor)?.editor ?: return

          if (newFile != null) {
            val text = newEditor.document.text
            trySend(RefreshSignal(text, newFile, RefreshReason.CHANGE))
          }
        }
      })

      val selectedFile = withContext(Dispatchers.EDT) { getSelectedEditorFile() }
      if (selectedFile != null) {
        trySend(RefreshSignal(null, selectedFile, RefreshReason.INITIAL))
      }

      var wasPreviewVisible = withContext(Dispatchers.EDT) { isPreviewVisible() }
      connection.subscribe(TOPIC, object : ToolWindowManagerListener {
        override fun stateChanged(toolWindowManager: ToolWindowManager) {
          val selectedFile = getSelectedEditorFile()

          if (selectedFile != null) {
            val nowVisible = isPreviewVisible()
            if (wasPreviewVisible != nowVisible) {
              trySend(RefreshSignal(null, selectedFile, RefreshReason.INITIAL))
              wasPreviewVisible = nowVisible
            }
          }
        }
      })

      // Handle cleanup when the flow is canceled
      awaitClose {
        connection.disconnect()
      }
    }
  }

  private fun getSelectedEditorFile(): VirtualFile? {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val selectedEditor = fileEditorManager.selectedEditor as? TextEditor
    val selectedFile = selectedEditor?.let { fileEditorManager.selectedFiles.firstOrNull() }
    return selectedFile
  }
}