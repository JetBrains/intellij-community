// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextEnvelopeSummary
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.swing.JPanel

fun captureNewTaskPromptLaunchRequest(
  descriptor: AgentSessionProviderDescriptor,
  prompt: String,
  workingProjectPath: String,
  project: Project = ProjectManager.getInstance().defaultProject,
): AgentPromptLaunchRequest {
  var capturedRequest: AgentPromptLaunchRequest? = null
  val availableDescriptor = object : AgentSessionProviderDescriptor by descriptor {
    override suspend fun isCliAvailable(): Boolean = true
  }
  service<AgentSessionProviderSettingsService>().setProviderEnabled(descriptor.provider, true)
  project.service<AgentSessionProviderAvailabilityService>().setAvailabilityForTest(mapOf(descriptor.provider to true))

  runInEdtAndWait {
    val classLoader = AgentPromptPaletteSubmitController::class.java.classLoader
    val promptArea = EditorTextField()
    val view = createAgentPromptPaletteView(
      promptArea = EditorTextField(),
      contextChipsPanel = JPanel(),
      onExistingTaskSelected = {},
    )
    val providerSelector = AgentPromptProviderSelector(
      invocationData = testInvocationData(project),
      headerControls = view.headerControls,
      providersProvider = { listOf(availableDescriptor) },
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(classLoader),
    )
    val existingTaskController = AgentPromptExistingTaskController(
      existingTaskListModel = javax.swing.DefaultListModel(),
      existingTaskList = com.intellij.ui.components.JBList(),
      popupScope = testScope(),
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(classLoader),
      onStateChanged = {},
    )
    val launchState = AgentPromptPaletteLaunchState().also {
      it.selectedWorkingProjectPath = workingProjectPath
    }
    val controller = AgentPromptPaletteSubmitController(
      project = project,
      invocationData = testInvocationData(project),
      promptArea = promptArea,
      providerSelector = providerSelector,
      existingTaskController = existingTaskController,
      launcherProvider = {
        object : AgentPromptLauncherBridge {
          override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
            capturedRequest = request
            return AgentPromptLaunchResult.SUCCESS
          }

          override fun resolveWorkingProjectPath(invocationData: AgentPromptInvocationData): String = workingProjectPath
        }
      },
      launchState = launchState,
      currentTargetMode = { PromptTargetMode.NEW_TASK },
      activeExtensionTab = { null },
      buildVisibleContextEntries = { emptyList() },
      resolveContextSelection = { _, _ ->
        AgentPromptPaletteContextSelection(emptyList(), AgentPromptContextEnvelopeSummary())
      },
      onWorkingProjectPathSelected = {},
      onSubmitBlocked = {},
      onSubmitSucceeded = {},
    )

    providerSelector.refresh()
    providerSelector.selectProvider(descriptor.provider)
    promptArea.text = prompt
    controller.submit()
  }

  return checkNotNull(capturedRequest)
}

private fun testInvocationData(project: Project): AgentPromptInvocationData {
  return AgentPromptInvocationData(
    project = project,
    actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
    actionText = "Ask Agent",
    actionPlace = "MainMenu",
    invokedAtMs = 0L,
  )
}

@Suppress("RAW_SCOPE_CREATION")
private fun testScope(): CoroutineScope {
  return CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
}
