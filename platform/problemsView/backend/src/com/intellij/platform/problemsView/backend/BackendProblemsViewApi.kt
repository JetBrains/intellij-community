// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEventDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemLifetime
import com.intellij.analysis.problemsView.toolWindow.splitApi.setProblemsViewImplementationForNextIdeRun
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.platform.problemsView.collector.ProjectErrorsCollector
import com.intellij.platform.problemsView.backend.actions.ProblemsViewQuickFixExecutor
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

internal class BackendProblemsViewApi : ProblemsViewApi {

  override suspend fun getFileProblemsFlow(projectId: ProjectId, fileId: VirtualFileId): Flow<List<ProblemEventDto>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return HighlightingProblemsBackendService.getInstance(project).getOrCreateEventFlowForFile(fileId)
  }

  override suspend fun executeQuickFix(projectId: ProjectId, fileId: VirtualFileId, problemId: String, intentionId: String) {
    val project = projectId.findProjectOrNull() ?: return
    ProblemsViewQuickFixExecutor.executeQuickFix(project, fileId, problemId, intentionId)
  }

  override suspend fun getProjectErrorsFlow(projectId: ProjectId): Flow<List<ProblemEventDto>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return flow {
      val lifetime = ProblemLifetime(CoroutineScope(currentCoroutineContext()))
      emitAll(
        ProjectErrorsCollector.getInstance(project).getProblemEventsFlow()
          .batchEvents()
          .map { batch -> buildChangelistFromEventsBatch(batch, project, lifetime) }
      )
    }
  }

  override suspend fun changeProblemsViewImplementationForNextIdeRunAndRestart(shouldEnableSplitImplementation: Boolean) {
    setProblemsViewImplementationForNextIdeRun(shouldEnableSplitImplementation)
    ApplicationManagerEx.getApplicationEx().restart(true)
  }

}
