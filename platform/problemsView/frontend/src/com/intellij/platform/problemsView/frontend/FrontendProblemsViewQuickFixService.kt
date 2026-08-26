// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.toolWindow.HighlightingProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.QuickFixModelDto
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.ActivityTracker
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.backendId
import com.intellij.openapi.project.Project
import com.intellij.platform.problemsView.frontend.actions.dtoToIntentionActionDescriptor
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

  private val selectedProblemModel = MutableStateFlow<FrontendHighlightingProblemModel?>(null)

  init {
    coroutineScope.launch {
      selectedProblemModel
        .map { it?.problem }
        .distinctUntilChanged()
        .collectLatest(::loadQuickFixes)
    }
  }

  fun selectProblem(problem: HighlightingProblem?) {
    val newSelectedProblem = problem?.takeIf { it.highlighter.backendId != null }

    selectedProblemModel.update { currentProblemModel ->
      if (currentProblemModel?.problem == newSelectedProblem) currentProblemModel
      else newSelectedProblem?.let(::FrontendHighlightingProblemModel)
    }
  }

  fun getQuickFixModel(problem: HighlightingProblem): QuickFixModel? {
    val currentProblemModel = selectedProblemModel.value
    return currentProblemModel?.quickFixModel?.takeIf { currentProblemModel.problem == problem }
  }

  fun executeQuickFix(quickFixModelId: String, intentionId: String) {
    val currentProblemModel = selectedProblemModel.value ?: return
    if (currentProblemModel.quickFixModel?.quickFixModelId != quickFixModelId) return
    if (!selectedProblemModel.compareAndSet(currentProblemModel, currentProblemModel.copy(quickFixModel = null))) return

    coroutineScope.launch {
      ProblemsViewApi.getInstance().executeQuickFix(project.projectId(), quickFixModelId, intentionId)
    }
  }

  fun discardQuickFixes() {
    selectProblem(null)
  }

  private suspend fun loadQuickFixes(problem: HighlightingProblem?) {
    if (problem == null) {
      discardBackendQuickFixes()
      return
    }

    val quickFixModel = ProblemsViewApi.getInstance().loadQuickFixes(
      project.projectId(),
      problem.file.rpcId(),
      problem.highlighter.backendId ?: return,
    )?.toQuickFixModel()

    val currentProblemModel = selectedProblemModel.value ?: return
    if (currentProblemModel.problem != problem) return
    if (!selectedProblemModel.compareAndSet(currentProblemModel, currentProblemModel.copy(quickFixModel = quickFixModel))) return

    requestActionUpdate()
  }

  private suspend fun discardBackendQuickFixes() {
    ProblemsViewApi.getInstance().discardQuickFixes(project.projectId())
  }

  private fun QuickFixModelDto.toQuickFixModel(): QuickFixModel {
    val actions = quickFixes.map { quickFix ->
      dtoToIntentionActionDescriptor(quickFix, quickFixModelId)
    }
    return QuickFixModel(quickFixModelId, actions, offset)
  }

  companion object {
    fun getInstance(project: Project): FrontendProblemsViewQuickFixService = project.service()

    private fun requestActionUpdate() { ActivityTracker.getInstance().inc() }
  }
}
