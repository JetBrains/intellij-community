// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.actions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.platform.projectView.window.ProjectViewToolWindowService
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import kotlin.collections.addAll
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
@Serializable
enum class EditorChoice {
  ALL_SELECTED,
  LAST_FOCUSED_ONLY,
}

@Service(Service.Level.PROJECT)
internal class SelectInSplitProjectViewImpl(private val project: Project, coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): SelectInSplitProjectViewImpl = project.service()
  }

  private val tasks = Channel<SelectTask>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(CoroutineName("SelectInSplitProjectViewImpl")) {
      performTasks()
    }
  }

  fun isSelectOpenedFileEnabled(): Boolean {
    // TODO when Always Select Opened File is ready
    return true
  }

  fun selectOpenedFile(editorChoice: EditorChoice) {
    check(tasks.trySend(SelectTask(editorChoice)).isSuccess)
  }

  private suspend fun performTasks() {
    tasks.consumeAsFlow().collectLatest { task ->
      try {
        selectImpl(task)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.error("An exception occurred while selecting a node: $task", e)
      }
    }
  }

  private suspend fun selectImpl(task: SelectTask) {
    val paneId = ProjectViewToolWindowService.getInstance(project).currentPaneId ?: return
    val rpc = ProjectViewRpc.getInstance()
    withContext(Dispatchers.EDT) { // "thanks" to a ton of legacy API (like FileEditor.isValid), we need both EDT and read action here
      val fileEditors = task.fileEditors()
      for (fileEditor in fileEditors) {
        // TODO generic FileEditor support, not just text editors
        if (fileEditor !is TextEditor || !fileEditor.isValid) continue
        val editor = fileEditor.editor
        if (editor.isDisposed) continue
        val nodePath = withTimeoutOrNull(15.seconds) {
          rpc.findNodeForOpenedFile(project.projectId(), paneId, task.editorChoice)
        }
        if (nodePath != null) {
          // The nodes should be already loaded at this moment.
          // But due to the world being completely async, they might take a few instants to actually arrive to the tree.
          // Or it might not even arrive at all, for example, if it was removed at a very unlucky moment.
          withTimeoutOrNull(5.seconds) {
            ProjectViewToolWindowService.getInstance(project).selectNode(nodePath)
          }
          break
        }
      }
    }
  }

  private fun SelectTask.fileEditors(): Collection<FileEditor> {
    return when (editorChoice) {
      EditorChoice.ALL_SELECTED -> allFileEditors()
      EditorChoice.LAST_FOCUSED_ONLY -> selectedFileEditor()
    }
  }

  private fun allFileEditors(): Collection<FileEditor> {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val result = mutableListOf<FileEditor?>()
    result.add(fileEditorManager.selectedEditor)
    result.addAll(fileEditorManager.selectedEditors)
    return result.filterNotNull()
  }

  private fun selectedFileEditor(): Collection<FileEditor> {
    return listOfNotNull(FileEditorManager.getInstance(project).selectedEditor)
  }
}

private data class SelectTask(val editorChoice: EditorChoice)

private val LOG = logger<SelectInSplitProjectViewImpl>()
