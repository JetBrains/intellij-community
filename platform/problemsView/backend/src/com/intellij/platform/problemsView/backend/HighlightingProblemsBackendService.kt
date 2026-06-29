// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.toolWindow.ProblemsViewHighlightingFileRoot
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewState
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater
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
    val connection = project.messageBus.connect(this)

    connection.subscribe(
      FileEditorManagerListener.FILE_EDITOR_MANAGER,
      object : FileEditorManagerListener {
        override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
          removeFile(file)
        }
      }
    )

    connection.subscribe(
      LazyQuickFixUpdater.TOPIC,
      LazyQuickFixUpdater.QuickFixesAvailableListener { info, document ->
        refreshProblemForAvailableQuickFixes(info, document)
      }
    )
  }

  private fun refreshProblemForAvailableQuickFixes(info: HighlightInfo, document: Document) {
    val highlighter = info.highlighter ?: return
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    val root = fileRootCache[file] ?: return
    val problem = root.findProblem(highlighter) ?: return

    root.problemUpdated(problem)
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

    val root = fileRootCache.computeIfAbsent(file) { _ ->
      val mockPanel = MockProblemsViewPanel(project)
      ProblemsViewHighlightingFileRoot(mockPanel, file, document)
    }

    return root.problemEvents
      .batchEvents()
      .map { batch -> buildChangelistFromEventsBatch(batch, project, root.lifetime) }
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
