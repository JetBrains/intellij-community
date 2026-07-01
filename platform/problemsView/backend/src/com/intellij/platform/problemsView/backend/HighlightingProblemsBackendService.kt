// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewHighlightingFileRoot
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class HighlightingProblemsBackendService(private val project: Project) : Disposable {

  companion object {
    fun getInstance(project: Project): HighlightingProblemsBackendService =
        project.getService(HighlightingProblemsBackendService::class.java)
  }

  private val fileRootCache = ConcurrentHashMap<VirtualFile, ProblemsViewHighlightingFileRoot>()

  init {
    project.messageBus.connect(this).subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          removeFile(file)
        }
      }
    )
  }

  suspend fun getOrCreateEventFlowForFile(fileId: VirtualFileId): Flow<List<ProblemEventDto>> {
    val file = fileId.virtualFile() ?: run {
      thisLogger().warn("file with id $fileId not found")
      return emptyFlow()
    }

    val document = getDocumentFromFile(file) ?: run {
      thisLogger().warn("no document for file with ${file.name} found")
      return emptyFlow()
    }

    var rootWasCreated = false
    val root = fileRootCache.computeIfAbsent(file) { _ ->
      rootWasCreated = true
      val mockPanel = MockProblemsViewPanel(project)
      ProblemsViewHighlightingFileRoot(mockPanel, file, document)
    }

    return root.problemEvents
      .onStart {
        if (!rootWasCreated) {
          root.getFileProblems(file).forEach { problem ->
            emit(ProblemEvent.Appeared(problem))
          }
        }
      }
      .onCompletion { root.resetEventReplayCache() }
      .batchEvents()
      .map { batch -> buildChangelistFromEventsBatch(batch, project, root.lifetime, sourceFlow = "file:${file.name}") }
  }

  private suspend fun getDocumentFromFile(file: VirtualFile) : Document? {
    return readAction { FileDocumentManager.getInstance().getDocument(file) }
  }

  @RequiresEdt
  private fun removeFile(file: VirtualFile) {
    val root = fileRootCache.remove(file) ?: return
    Disposer.dispose(root.panel)
    Disposer.dispose(root)
  }

  override fun dispose() {
    fileRootCache.values.forEach { root ->
      Disposer.dispose(root.panel)
      Disposer.dispose(root)
    }
    fileRootCache.clear()
  }

  private class MockProblemsViewPanel(project: Project) :
    ProblemsViewPanel(project,
                      "mock-problems-panel",
                      ProblemsViewState.getInstance(project),
                      { "Mock Problems View Panel" })
}
