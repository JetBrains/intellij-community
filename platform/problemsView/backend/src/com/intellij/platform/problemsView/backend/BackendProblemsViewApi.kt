// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.QuickFixModelDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.setProblemsViewImplementationForNextIdeRun
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.platform.problemsView.collector.ProjectErrorsCollector
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

internal class BackendProblemsViewApi : ProblemsViewApi {

  override suspend fun loadQuickFixes(
    projectId: ProjectId,
    fileId: VirtualFileId,
    highlighterId: Long,
  ): QuickFixModelDto? {
    val project = projectId.findProjectOrNull() ?: return null
    return BackendProblemsViewQuickFixService.getInstance(project).loadQuickFixes(fileId, highlighterId)
  }

  override suspend fun discardQuickFixModel(projectId: ProjectId, quickFixModelId: String) {
    val project = projectId.findProjectOrNull() ?: return
    BackendProblemsViewQuickFixService.getInstance(project).discardQuickFixModel(quickFixModelId)
  }

  override suspend fun executeQuickFix(projectId: ProjectId, quickFixModelId: String, intentionId: String) {
    val project = projectId.findProjectOrNull() ?: return
    BackendProblemsViewQuickFixService.getInstance(project).executeQuickFix(quickFixModelId, intentionId)
  }

  override suspend fun getProjectErrorsFlow(projectId: ProjectId): Flow<List<ProblemEventDto>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return ProjectErrorsCollector.getInstance(project).getProblemEventsFlow()
      .batchEvents()
      .map { batch -> buildChangelistFromEventsBatch(batch, project) }
  }

  override suspend fun changeProblemsViewImplementationForNextIdeRunAndRestart(shouldEnableSplitImplementation: Boolean) {
    setProblemsViewImplementationForNextIdeRun(shouldEnableSplitImplementation)
    ApplicationManagerEx.getApplicationEx().restart(true)
  }

}
