// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.ProblemsViewEditorUtils
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.QuickFixModelDto
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.problemsView.backend.actions.BackendQuickFixModel
import com.intellij.platform.problemsView.backend.actions.IntentionActionWithIds
import com.intellij.platform.problemsView.backend.actions.IntentionOptionWithId
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference


@Service(Service.Level.PROJECT)
internal class BackendProblemsViewQuickFixService(private val project: Project) {
  private val currentQuickFixModel = AtomicReference<BackendQuickFixModel?>()

  suspend fun loadQuickFixes(fileId: VirtualFileId, highlighterId: Long): QuickFixModelDto? {
    val pendingQuickFixModel = BackendQuickFixModel(quickFixModelId = UUID.randomUUID().toString())
    currentQuickFixModel.set(pendingQuickFixModel)

    try {
      val loadedQuickFixModel = loadQuickFixModel(pendingQuickFixModel, fileId, highlighterId) ?: return null
      if (!currentQuickFixModel.compareAndSet(pendingQuickFixModel, loadedQuickFixModel)) return null

      return loadedQuickFixModel.toDto()
    }
    finally {
      currentQuickFixModel.compareAndSet(pendingQuickFixModel, null)
    }
  }

  private suspend fun loadQuickFixModel(
    pendingQuickFixModel: BackendQuickFixModel,
    fileId: VirtualFileId,
    highlighterId: Long,
  ): BackendQuickFixModel? {
    val file = fileId.virtualFile() ?: return null
    val (highlighter, info) = findHighlightInfo(file, highlighterId) ?: return null

    LazyQuickFixUpdater.getInstance(project).waitQuickFixesSynchronously(info, project, highlighter.document)

    val quickFixes = readAction {
      if (!highlighter.isValid || findHighlighter(file, highlighterId) !== highlighter) return@readAction null
      collectAvailableQuickFixes(info, file, project).takeIf { it.isNotEmpty() }
    } ?: return null

    return pendingQuickFixModel.copy(
      file = file,
      highlighterId = highlighterId,
      quickFixes = quickFixes,
    )
  }

  fun discardQuickFixModel(quickFixModelId: String) {
    val quickFixModel = currentQuickFixModel.get()?.takeIf { it.quickFixModelId == quickFixModelId } ?: return
    currentQuickFixModel.compareAndSet(quickFixModel, null)
  }

  @RequiresReadLock
  internal fun collectAvailableQuickFixes(info: HighlightInfo, file: VirtualFile, project: Project): List<IntentionActionWithIds> {
    if (!file.isValid) return emptyList()

    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile == null) return emptyList()

    val editor = ProblemsViewEditorUtils.getEditor(psiFile)
    if (editor == null) return emptyList()

    val quickFixes = mutableListOf<IntentionActionWithIds>()

    info.findRegisteredQuickFix { intentionAction, _ ->
      val action = intentionAction.action
      val isActionAvailable = runCatching {
        action.isAvailable(psiFile.project, editor, psiFile)
      }.getOrDefault(false)

      if (isActionAvailable) {
        val options = intentionAction.getOptions(psiFile, editor).map { option ->
          IntentionOptionWithId(
            action = option,
            intentionId = UUID.randomUUID().toString(),
            text = option.text,
            familyName = option.familyName
          )
        }

        quickFixes.add(
          IntentionActionWithIds(
            descriptor = intentionAction,
            intentionId = UUID.randomUUID().toString(),
            options = options,
            text = action.text,
            familyName = action.familyName
          )
        )
      }
      null
    }

    return quickFixes
  }

  suspend fun executeQuickFix(quickFixModelId: String, intentionId: String) {
    val quickFixModel = currentQuickFixModel.get()?.takeIf { it.quickFixModelId == quickFixModelId } ?: return
    val file = quickFixModel.file ?: return
    val highlighterId = quickFixModel.highlighterId ?: return
    val action = quickFixModel.findQuickFixById(intentionId) ?: return

    if (!currentQuickFixModel.compareAndSet(quickFixModel, null)) return

    executeQuickFix(file, highlighterId, action)
  }

  private suspend fun executeQuickFix(file: VirtualFile, highlighterId: Long, action: IntentionAction) {
    val context = readAction {
      if (!file.isValid) return@readAction null
      val editor = ProblemsViewEditorUtils.getEditor(file, project) ?: return@readAction null
      val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@readAction null
      psiFile to editor
    } ?: return

    val (psiFile, editor) = context

    withContext(Dispatchers.EDT) {
      val targetEditor = ProblemsViewEditorUtils.openEditorIfNeeded(file, project, editor) ?: return@withContext
      if (!file.isValid || targetEditor.isDisposed) return@withContext
      val highlighter = findHighlighter(file, highlighterId) ?: return@withContext
      val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: return@withContext

      ShowIntentionActionsHandler.chooseActionAndInvoke(
        psiFile,
        targetEditor,
        action,
        action.text,
        info.actualStartOffset,
        IntentionSource.PROBLEMS_VIEW
      )
    }
  }

  private fun BackendQuickFixModel.toDto(): QuickFixModelDto? {
    val currentQuickFixModel = currentQuickFixModel.get() ?: return null
    if (this !== currentQuickFixModel || file == null) return null

    return QuickFixModelDto(
      quickFixModelId,
      quickFixes.map(::convertIntentionActionToDto)
    )
  }

  private fun findHighlighter(file: VirtualFile, highlighterId: Long): RangeHighlighterEx? {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val markupModel = DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx ?: return null
    return markupModel.allHighlighters.filterIsInstance<RangeHighlighterEx>().firstOrNull { it.id == highlighterId }
  }

  private suspend fun findHighlightInfo(file: VirtualFile, highlighterId: Long): Pair<RangeHighlighterEx, HighlightInfo>? {
    return readAction {
      val highlighter = findHighlighter(file, highlighterId) ?: return@readAction null
      val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: return@readAction null
      highlighter to info
    }
  }

  @TestOnly
  fun hasLoadedQuickFixes(): Boolean {
    val quickFixModel = currentQuickFixModel.get() ?: return false
    return quickFixModel.file != null
  }

  companion object {
    fun getInstance(project: Project): BackendProblemsViewQuickFixService = project.service()
  }
}
