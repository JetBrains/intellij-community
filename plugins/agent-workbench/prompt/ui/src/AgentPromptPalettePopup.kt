// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-entry.spec.md
// @spec community/plugins/agent-workbench/spec/actions/global-prompt-suggestions.spec.md
// @spec community/plugins/agent-workbench/spec/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptContextResolverService
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchers
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.awt.FlowLayout
import javax.swing.JPanel

internal class AgentPromptPalettePopup(
  private val invocationData: AgentPromptInvocationData,
  private val providersProvider: () -> List<AgentSessionProviderDescriptor> = AgentSessionProviders::allProviders,
  private val launcherProvider: () -> AgentPromptLauncherBridge? = AgentPromptLaunchers::find,
  private val onClosed: (() -> Unit)? = null,
) : AgentPromptPalettePopupSession {
  private val project: Project = invocationData.project
  private val contextResolverService: AgentPromptContextResolverService = project.service()
  private val uiStateService: AgentPromptUiSessionStateService = project.service()
  private val sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPalettePopup::class.java.classLoader)

  private val promptArea = AgentPromptTextField(project)

  @Suppress("RAW_SCOPE_CREATION")
  private val popupScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

  private var popup: JBPopup? = null
  private var popupActive: Boolean = false
  private lateinit var sessionController: AgentPromptPaletteSessionController

  override fun show() {
    val content = createContentPanel()
    sessionController.initialize()

    val createdPopup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content, promptArea)
      .setProject(project)
      .setModalContext(false)
      .setCancelOnClickOutside(true)
      .setRequestFocus(true)
      .setCancelKeyEnabled(true)
      .setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, "AgentWorkbench.PromptPalette", true)
      .setLocateWithinScreenBounds(false)
      .createPopup()

    popup = createdPopup
    popupActive = true
    createdPopup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        popupActive = false
        popup = null
        sessionController.onPopupClosed()
        popupScope.cancel("Agent prompt popup closed")
        onClosed?.invoke()
      }
    })

    sessionController.installHandlers()
    createdPopup.showCenteredInCurrentWindow(project)
  }

  override fun requestFocus() {
    val currentPopup = popup ?: return
    if (!currentPopup.isVisible) {
      return
    }

    currentPopup.setRequestFocus(true)
    val focusComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(currentPopup.content) ?: promptArea
    IdeFocusManager.getInstance(project).requestFocusInProject(focusComponent, project)
  }

  override fun isVisible(): Boolean {
    return popup?.isVisible == true
  }

  private fun createContentPanel(): JPanel {
    lateinit var controllerRef: AgentPromptPaletteSessionController
    val suggestions = AgentPromptSuggestionsComponent { candidate -> controllerRef.applySuggestedPrompt(candidate) }
    val contextChips = AgentPromptContextChipsComponent { entry -> controllerRef.removeContextEntry(entry) }
    val promptProviderOptionsPanel = createProviderOptionsPanel()
    val view = createAgentPromptPaletteView(
      promptArea = promptArea,
      suggestionsPanel = suggestions.component,
      contextChipsPanel = contextChips.component,
      providerOptionsPanel = promptProviderOptionsPanel,
      onProviderIconClicked = { controllerRef.showProviderChooser() },
      onExistingTaskSelected = { selected -> controllerRef.onExistingTaskSelected(selected) },
    )
    val providerOptionsPanel = checkNotNull(view.providerOptionsPanel)
    val providerSelector = AgentPromptProviderSelector(
      invocationData = invocationData,
      providerIconLabel = view.providerIconLabel,
      providerOptionsPanel = providerOptionsPanel,
      providersProvider = providersProvider,
      sessionsMessageResolver = sessionsMessageResolver,
    )
    val existingTaskController = AgentPromptExistingTaskController(
      existingTaskListModel = view.existingTaskListModel,
      existingTaskList = view.existingTaskList,
      popupScope = popupScope,
      sessionsMessageResolver = sessionsMessageResolver,
      onStateChanged = { controllerRef.onExistingTaskStateChanged() },
    )
    val suggestionController = AgentPromptSuggestionController(
      popupScope = popupScope,
      onSuggestionsUpdated = suggestions::render,
    )
    sessionController = AgentPromptPaletteSessionController(
      project = project,
      invocationData = invocationData,
      promptArea = promptArea,
      view = view,
      contextChips = contextChips,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      suggestionController = suggestionController,
      contextResolverService = contextResolverService,
      uiStateService = uiStateService,
      launcherProvider = launcherProvider,
      closePopup = { popup?.cancel() },
      isPopupActive = { popupActive },
      movePopupToFitScreen = { popup?.moveToFitScreen() },
    )
    controllerRef = sessionController
    return view.rootPanel
  }

  private fun createProviderOptionsPanel(): JPanel {
    return JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      isVisible = false
    }
  }
}
