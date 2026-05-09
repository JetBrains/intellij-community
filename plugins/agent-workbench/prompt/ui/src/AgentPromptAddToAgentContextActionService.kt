// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextTargetCandidate
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptAddContextToTargetResult
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.agent.workbench.prompt.ui.context.dataContextOrNull
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import javax.swing.JList

private class AgentPromptAddToAgentContextActionServiceLog

private val LOG = logger<AgentPromptAddToAgentContextActionServiceLog>()

@Service(Service.Level.PROJECT)
internal class AgentPromptAddToAgentContextActionService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  fun addToAgentContext(invocationData: AgentPromptInvocationData) {
    val contextItems = project.service<AgentPromptContextResolverService>().collectDefaultContext(invocationData)
    if (contextItems.isEmpty()) {
      showStatus(AgentPromptBundle.message("popup.status.context.empty"))
      return
    }

    val popupService = project.service<AgentPromptPalettePopupService>()
    val visibleResult = popupService.applyAddContextToVisible(
      AgentPromptAddContextRequest(contextItems = contextItems, target = null)
    )
    if (visibleResult != null) {
      return
    }

    val launcher = AgentPromptLaunchers.find()
    val projectPath = resolveProjectPath(invocationData = invocationData, launcher = launcher)
    if (launcher == null || projectPath == null) {
      popupService.showAddContext(invocationData, AgentPromptAddContextRequest(contextItems = contextItems, target = null))
      return
    }

    coroutineScope.launch(Dispatchers.UI) {
      if (project.isDisposed) {
        return@launch
      }
      val candidates = loadTargetCandidates(launcher = launcher, projectPath = projectPath)
      val preferredTarget = resolvePreferredTarget(candidates)
      when {
        preferredTarget != null -> addContextToTargetOrFallback(
          invocationData = invocationData,
          contextItems = contextItems,
          target = preferredTarget,
          popupService = popupService,
        )
        candidates.isEmpty() -> popupService.showAddContextFromUiDispatcher(
          invocationData,
          AgentPromptAddContextRequest(contextItems = contextItems, target = null),
        )
        else -> showTargetChooser(
          invocationData = invocationData,
          contextItems = contextItems,
          candidates = candidates,
          popupService = popupService,
        )
      }
    }
  }

  private fun resolvePreferredTarget(candidates: List<AgentPromptAddContextTargetCandidate>): AgentPromptAddContextTargetCandidate? {
    if (candidates.size == 1) {
      return candidates.single()
    }
    return candidates.singleOrNull { candidate -> candidate.selected }
  }

  private suspend fun addContextToTargetOrFallback(
    invocationData: AgentPromptInvocationData,
    contextItems: List<AgentPromptContextItem>,
    target: AgentPromptAddContextTargetCandidate,
    popupService: AgentPromptPalettePopupService,
  ) {
    val result = AgentPromptLaunchers.find()?.addContextToOpenChatTarget(
      AgentPromptAddContextToTargetRequest(
        target = target,
        contextItems = contextItems,
      )
    ) ?: AgentPromptAddContextToTargetResult.UNAVAILABLE
    when (result) {
      AgentPromptAddContextToTargetResult.ADDED_TO_CHAT -> {
        showStatus(AgentPromptBundle.message("popup.status.context.added"))
        return
      }
      AgentPromptAddContextToTargetResult.ALREADY_ADDED_TO_CHAT -> {
        showStatus(AgentPromptBundle.message("popup.status.context.already.added"))
        return
      }
      AgentPromptAddContextToTargetResult.UNAVAILABLE -> Unit
    }
    popupService.showAddContextFromUiDispatcher(
      invocationData,
      AgentPromptAddContextRequest(contextItems = contextItems, target = target),
    )
  }

  private suspend fun loadTargetCandidates(
    launcher: AgentPromptLauncherBridge,
    projectPath: String,
  ): List<AgentPromptAddContextTargetCandidate> {
    return try {
      val candidates = launcher.listAddContextTargetCandidates(projectPath)
      val (selected, other) = candidates.partition { candidate -> candidate.selected }
      selected + other
    }
    catch (e: Exception) {
      if (e is CancellationException) {
        throw e
      }
      LOG.warn("Failed to resolve Add to Agent Context target candidates", e)
      emptyList()
    }
  }

  @Suppress("SplitModeApiUsage")
  private fun showTargetChooser(
    invocationData: AgentPromptInvocationData,
    contextItems: List<AgentPromptContextItem>,
    candidates: List<AgentPromptAddContextTargetCandidate>,
    popupService: AgentPromptPalettePopupService,
  ) {
    val dataContext = invocationData.dataContextOrNull()
    val popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(candidates)
      .setTitle(AgentPromptBundle.message("popup.add.context.target.chooser.title"))
      .setRenderer(object : ColoredListCellRenderer<AgentPromptAddContextTargetCandidate>() {
        override fun customizeCellRenderer(
          list: JList<out AgentPromptAddContextTargetCandidate>,
          value: AgentPromptAddContextTargetCandidate?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          value ?: return
          append(value.displayText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          if (value.secondaryText.isNotBlank()) {
            append(value.secondaryText, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          }
        }
      })
      .setItemChosenCallback { target ->
        coroutineScope.launch(Dispatchers.UI) {
          addContextToTargetOrFallback(
            invocationData = invocationData,
            contextItems = contextItems,
            target = target,
            popupService = popupService,
          )
        }
      }
      .createPopup()

    if (dataContext != null) {
      popup.showInBestPositionFor(dataContext)
    }
    else {
      popup.showCenteredInCurrentWindow(project)
    }
  }

  private fun resolveProjectPath(
    invocationData: AgentPromptInvocationData,
    launcher: AgentPromptLauncherBridge?,
  ): String? {
    return launcher
             ?.resolveWorkingProjectPath(invocationData)
             ?.takeIf { path -> path.isNotBlank() }
           ?: project.basePath?.takeIf { path -> path.isNotBlank() }
  }

  private fun showStatus(message: @Nls String) {
    StatusBar.Info.set(message, project)
  }
}
