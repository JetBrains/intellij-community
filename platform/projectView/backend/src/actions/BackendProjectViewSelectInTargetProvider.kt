// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.actions

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.ide.impl.ProjectViewSelectInTargetProvider
import com.intellij.ide.projectView.impl.isProjectViewSplit
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.projectView.backend.pane.BackendProjectViewPaneService
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.projectView.pane.SelectInTargetDescriptor
import com.intellij.platform.projectView.pane.serialize
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

internal class BackendProjectViewSelectInTargetProvider : ProjectViewSelectInTargetProvider {
  override fun getSelectInTargets(project: Project): Collection<SelectInTarget> {
    // In the monolith mode, the frontend service does the job.
    // This one is only needed when some code at the backend wants to select something.
    if (!isProjectViewSplit() || IdeProductMode.isMonolith) return emptyList()
    return buildList { 
      val paneService = BackendProjectViewPaneService.getInstance(project)
      val paneDescriptors = paneService.getPaneDescriptors().partition { paneService.isPaneSelected(it.id) }
      val targetDescriptors = sortedTargetDescriptors(paneDescriptors.first) + sortedTargetDescriptors(paneDescriptors.second)
      addAll(targetDescriptors.map { BackendProjectViewSelectInTarget(it) })
    }
  }

  private fun sortedTargetDescriptors(paneDescriptors: List<ProjectViewPaneDescriptorImpl>): List<SelectInTargetDescriptor> {
    return paneDescriptors
      .flatMap { it.selectInTargetDescriptors }
      .sortedBy { it.weight }
  }
}

private class BackendProjectViewSelectInTarget(private val selectInTargetDescriptor: SelectInTargetDescriptor) : SelectInTarget {
  override fun canSelect(context: SelectInContext): Boolean = true // maybe later

  override fun selectIn(context: SelectInContext, requestFocus: Boolean) {
    BackendProjectViewSelectInService.getInstance(context.project).scheduleSelectIn(selectInTargetDescriptor, context, requestFocus)
  }

  override fun getToolWindowId(): @NonNls String = ToolWindowId.PROJECT_VIEW

  override fun getMinorViewId(): @NonNls String = selectInTargetDescriptor.id

  override fun getWeight(): Float = selectInTargetDescriptor.weight

  // Has to be this way, a part of the SelectInTarget API.
  override fun toString(): @Nls String = selectInTargetDescriptor.presentableName
}

@Service(Service.Level.PROJECT)
internal class BackendProjectViewSelectInService(
  private val project: Project,
  coroutineScope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): BackendProjectViewSelectInService = project.service()
  }
  
  private val tasks = Channel<SelectInTask>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  
  init {
    coroutineScope.launch(CoroutineName("BackendProjectViewSelectInService")) {
      for (task in tasks) {
        try {
          select(task)
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          LOG.warn("Select task failed", e)
        }
      }
    }
  }

  fun scheduleSelectIn(selectInTargetDescriptor: SelectInTargetDescriptor, context: SelectInContext, requestFocus: Boolean) {
    val result = tasks.trySend(SelectInTask(selectInTargetDescriptor, context, requestFocus))
    check(result.isSuccess)
  }
  
  private suspend fun select(task: SelectInTask) {
    val paneService = BackendProjectViewPaneService.getInstance(project)
    val path = paneService.findNodeForSelectIn(
      SelectInRequestDTO(
        targetId = task.selectInTargetDescriptor.id,
        contextDTO = task.context.serialize(),
      )
    ) ?: return
    paneService.selectNode(path) { options ->
      options.requestFocus = task.requestFocus
    }
  }
  
  private data class SelectInTask(
    val selectInTargetDescriptor: SelectInTargetDescriptor,
    val context: SelectInContext,
    val requestFocus: Boolean,
  )
}

private val LOG = logger<BackendProjectViewSelectInService>()
