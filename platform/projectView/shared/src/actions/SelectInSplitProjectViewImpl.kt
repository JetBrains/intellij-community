// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.actions

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.projectView.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.pane.ProjectViewNodePath
import com.intellij.platform.projectView.pane.SelectInContextDTO
import com.intellij.platform.projectView.pane.SelectInRequestDTO
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
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
@Serializable
enum class EditorChoice {
  ALL_SELECTED,
  LAST_FOCUSED_ONLY,
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SelectInSplitProjectViewImpl(private val project: Project, coroutineScope: CoroutineScope) {
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

  fun selectOpenedFile(editorChoice: EditorChoice, invokedManually: Boolean) {
    LOG.debug { "Scheduling selection, editor choice = $editorChoice" }
    check(tasks.trySend(SelectOpenedFileTask(project, editorChoice, invokedManually)).isSuccess)
  }

  fun selectIn(context: SelectInContext, target: SelectInTarget, requestFocus: Boolean) {
    LOG.debug { "Scheduling selection, target = ${target.minorViewId}, context = $context, requestFocus = $requestFocus" }
    check(tasks.trySend(SelectInTask(context, target, requestFocus)).isSuccess)
  }

  private suspend fun performTasks() {
    tasks.consumeAsFlow().collectLatest { task ->
      try {
        LOG.debug { "Executing the selection task $task" }
        task.select()
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.error("An exception occurred while selecting a node: $task", e)
      }
    }
  }
}

private sealed class SelectTask {
  protected abstract val project: Project
  abstract suspend fun select()

  protected suspend fun selectNodePath(nodePath: ProjectViewNodePath, requestFocus: Boolean) {
    // The nodes should be already loaded at this moment.
    // But due to the world being completely async, they might take a few instants to actually arrive to the tree.
    // Or it might not even arrive at all, for example, if it was removed at a very unlucky moment.
    withTimeoutOrNull(5.seconds) {
      LOG.debug { "${if (requestFocus) "Activating" else "Showing"} the Project View tool window" }
      ProjectViewToolWindowService.getInstance(project).show(requestFocus = requestFocus)
      LOG.debug { "Selecting the node $nodePath" }
      ProjectViewToolWindowService.getInstance(project).selectNode(nodePath)
      LOG.debug { "Selected the node $nodePath" }
    }
  }
}

private data class SelectOpenedFileTask(
  override val project: Project,
  private val editorChoice: EditorChoice,
  private val invokedManually: Boolean,
) : SelectTask() {
  override suspend fun select() {
    val paneDescriptor = ProjectViewToolWindowService.getInstance(project).currentPaneDescriptor ?: return
    val aggregator = FrontendProjectViewPaneAggregator.getInstance(project)
    withContext(Dispatchers.EDT) { // "thanks" to a ton of legacy API (like FileEditor.isValid), we need both EDT and read action here
      val fileEditors = fileEditors()
      for (fileEditor in fileEditors) {
        // TODO generic FileEditor support, not just text editors
        if (fileEditor !is TextEditor || !fileEditor.isValid) {
          LOG.debug { "Skipping the editor $fileEditor, isValid = ${fileEditor.isValid}" }
          continue
        }
        val editor = fileEditor.editor
        if (editor.isDisposed) {
          LOG.debug { "Skipping the editor $editor because it's disposed" }
          continue
        }
        val nodePath = withTimeoutOrNull(15.seconds) {
          LOG.debug { "Looking for the node to select for $fileEditor" }
          aggregator.findNodeForOpenedFile(paneDescriptor, editorChoice, invokedManually)
        }
        LOG.debug { "Found the node to select: $nodePath" }
        if (nodePath != null) {
          selectNodePath(nodePath, requestFocus = invokedManually)
          break
        }
      }
    }
  }

  private fun fileEditors(): Collection<FileEditor> {
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

private data class SelectInTask(
  private val context: SelectInContext,
  private val target: SelectInTarget,
  private val requestFocus: Boolean,
) : SelectTask() {
  override val project: Project = context.project

  override suspend fun select() {
    if (target !is SplitProjectViewSelectInTarget) {
      LOG.error("Target is not supported: $target")
      return
    }
    val aggregator = FrontendProjectViewPaneAggregator.getInstance(project)
    val nodePath = withTimeoutOrNull(15.seconds) {
      LOG.debug { "Looking for the node to select for $context" }
      aggregator.findNodeForSelectIn(SelectInRequestDTO(
        targetId = target.minorViewId,
        contextDTO = serialize(context),
        context = context,
      ))
    }
    LOG.debug { "Found the node to select: $nodePath" }
    if (nodePath != null) {
      selectNodePath(nodePath, requestFocus = requestFocus)
    }
  }

  private fun serialize(context: SelectInContext): SelectInContextDTO {
    return SelectInContextDTO(context.virtualFile.rpcId())
  }
}

@ApiStatus.Internal
class SplitProjectViewSelectInTarget(
  private val minorViewId: @NonNls String,
  private val presentableName: @Nls String,
  private val weight: Float,
) : SelectInTarget {
  override fun canSelect(context: SelectInContext): Boolean = true // TODO maybe implement somehow someday

  override fun selectIn(context: SelectInContext, requestFocus: Boolean) {
    LOG.debug("Select in $minorViewId: $context, requestFocus=$requestFocus")
    SelectInSplitProjectViewImpl.getInstance(context.project).selectIn(context, this, requestFocus)
  }

  override fun getToolWindowId(): @NonNls String = ToolWindowId.PROJECT_VIEW

  override fun getMinorViewId(): @NonNls String = minorViewId

  override fun getWeight(): Float = weight

  // This is actually a part of the SelectInTarget API (sic!).
  override fun toString(): @Nls String = presentableName
}

private val LOG = logger<SelectInSplitProjectViewImpl>()
