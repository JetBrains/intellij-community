// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.QuickFixModelDto
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.backendId
import com.intellij.openapi.project.Project
import com.intellij.platform.problemsView.frontend.actions.dtoToIntentionActionDescriptor
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

internal data class QuickFixModel(
  val quickFixModelId: String,
  val actions: List<HighlightInfo.IntentionActionDescriptor>,
  val offset: Int,
)

private data class FrontendHighlightingProblemModel(
  val problem: HighlightingProblem,
  val quickFixModel: QuickFixModel? = null,
)

@Service(Service.Level.PROJECT)
internal class FrontendProblemsViewQuickFixService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {

  private val selectedProblemModel = AtomicReference<FrontendHighlightingProblemModel?>()
  private val quickFixLoadingMutex = Mutex()

  fun selectProblem(problem: HighlightingProblem?) {
    val selectedProblem = problem?.takeIf { it.highlighter.backendId != null }
    if (selectedProblemModel.get()?.problem == selectedProblem) return

    val newSelection = selectedProblem?.let(::FrontendHighlightingProblemModel)
    val previousSelection = selectedProblemModel.getAndSet(newSelection)
    previousSelection?.quickFixModel?.let(::discardBackendQuickFixModel)
  }

  suspend fun loadQuickFixModel(problem: HighlightingProblem): QuickFixModel? = quickFixLoadingMutex.withLock {
    loadQuickFixModelIfSelected(problem)
  }

  private suspend fun loadQuickFixModelIfSelected(problem: HighlightingProblem): QuickFixModel? {
    val selectionAtLoadStart = selectedProblemModel.get()?.takeIf { it.problem == problem } ?: return null
    selectionAtLoadStart.quickFixModel?.let { return it }

    val quickFixModel = ProblemsViewApi.getInstance().loadQuickFixes(
      project.projectId(),
      problem.file.rpcId(),
      problem.highlighter.backendId ?: return null,
    )?.toQuickFixModel() ?: return null

    if (storeQuickFixModelIfSelectionUnchanged(selectionAtLoadStart, quickFixModel)) return quickFixModel

    discardBackendQuickFixModel(quickFixModel)
    return null
  }

  private fun storeQuickFixModelIfSelectionUnchanged(
    selectionAtLoadStart: FrontendHighlightingProblemModel,
    quickFixModel: QuickFixModel,
  ): Boolean {
    val selectionWithQuickFixes = selectionAtLoadStart.copy(quickFixModel = quickFixModel)
    return selectedProblemModel.compareAndSet(selectionAtLoadStart, selectionWithQuickFixes)
  }

  fun executeQuickFix(quickFixModelId: String, intentionId: String) {
    val currentProblemModel = selectedProblemModel.get() ?: return
    if (currentProblemModel.quickFixModel?.quickFixModelId != quickFixModelId) return
    if (!selectedProblemModel.compareAndSet(currentProblemModel, currentProblemModel.copy(quickFixModel = null))) return

    coroutineScope.launch {
      ProblemsViewApi.getInstance().executeQuickFix(project.projectId(), quickFixModelId, intentionId)
    }
  }

  fun discardQuickFixes() {
    selectProblem(null)
  }

  private fun discardBackendQuickFixModel(quickFixModel: QuickFixModel) {
    coroutineScope.launch {
      ProblemsViewApi.getInstance().discardQuickFixModel(project.projectId(), quickFixModel.quickFixModelId)
    }
  }

  private fun QuickFixModelDto.toQuickFixModel(): QuickFixModel {
    val actions = quickFixes.map { quickFix ->
      dtoToIntentionActionDescriptor(quickFix, quickFixModelId)
    }
    return QuickFixModel(quickFixModelId, actions, offset)
  }

  companion object {
    fun getInstance(project: Project): FrontendProblemsViewQuickFixService = project.service()
  }
}
