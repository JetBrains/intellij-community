// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtension
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.EditorTextField
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import javax.swing.JTabbedPane

@TestApplication
class AgentPromptPaletteDraftControllerTest {
  @Test
  fun restoreTaskDraftsRestoresExtensionInitialTextWhenSavedDraftIsMissing() {
    runInEdtAndWait {
      val fixture = createFixture()
      fixture.contextState.activeExtensionTabs = listOf(
        AgentPromptPaletteExtensionTab(
          extension = extension(),
          tabPanel = JPanel(),
          taskKey = "extension:test",
        )
      )

      fixture.controller.restoreTaskDrafts(
        AgentPromptUiDraft(
          promptText = "new task prompt",
          taskDrafts = mapOf(
            PromptTargetMode.NEW_TASK.name to "new task prompt",
            PromptTargetMode.EXISTING_TASK.name to "existing task prompt",
          ),
        )
      )

      assertThat(fixture.draftState.taskPromptStates[PromptTargetMode.NEW_TASK.name]?.liveText).isEqualTo("new task prompt")
      assertThat(fixture.draftState.taskPromptStates[PromptTargetMode.EXISTING_TASK.name]?.liveText).isEqualTo("existing task prompt")
      assertThat(fixture.draftState.taskPromptStates["extension:test"]?.liveText).isEqualTo("extension prompt")
    }
  }

  @Test
  fun loadPromptTextForSelectedTabUsesResolvedTaskKey() {
    runInEdtAndWait {
      val fixture = createFixture()
      val tabPanel = JPanel()
      fixture.tabbedPane.addTab("New", tabPanel)
      fixture.tabbedPane.selectedComponent = tabPanel
      fixture.draftState.taskPromptStates["tab-key"] = restoredTaskPromptDraftState("restored prompt")
      fixture.resolveTaskKey = { "tab-key" }

      fixture.controller.loadPromptTextForSelectedTab()

      assertThat(fixture.promptArea.text).isEqualTo("restored prompt")
      assertThat(fixture.draftState.activeTaskKey).isEqualTo("tab-key")
    }
  }

  private fun createFixture(): DraftControllerFixture {
    val project = ProjectManager.getInstance().defaultProject
    val promptArea = EditorTextField()
    val tabbedPane = JTabbedPane()
    val contextState = AgentPromptPaletteContextState()
    val draftState = AgentPromptPaletteDraftState()
    val resolveTaskKeyHolder = arrayOf<(JPanel?) -> String?>({ null })
    val controller = AgentPromptPaletteDraftController(
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
        actionText = "Ask Agent",
        actionPlace = "MainMenu",
        invokedAtMs = 0L,
      ),
      promptArea = promptArea,
      tabbedPane = tabbedPane,
      providerSelector = createProviderSelector(project),
      existingTaskController = createExistingTaskController(),
      uiStateService = AgentPromptUiSessionStateService(),
      launcherProvider = { null },
      contextState = contextState,
      draftState = draftState,
      refreshContextEntries = {},
      resolveExtensionTabs = {},
      reloadExistingTasks = {},
      updateProviderOptionsVisibility = {},
      setTargetMode = {},
      resolveTaskKey = { panel -> resolveTaskKeyHolder[0](panel) },
    )
    return DraftControllerFixture(
      controller = controller,
      promptArea = promptArea,
      tabbedPane = tabbedPane,
      contextState = contextState,
      draftState = draftState,
      resolveTaskKeyHolder = resolveTaskKeyHolder,
    )
  }

  private fun createProviderSelector(project: com.intellij.openapi.project.Project): AgentPromptProviderSelector {
    return AgentPromptProviderSelector(
      invocationData = AgentPromptInvocationData(
        project = project,
        actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
        actionText = "Ask Agent",
        actionPlace = "MainMenu",
        invokedAtMs = 0L,
      ),
      providerIconLabel = com.intellij.ui.components.JBLabel(),
      providerOptionsPanel = JPanel(),
      providersProvider = { emptyList() },
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPaletteDraftControllerTest::class.java.classLoader),
    )
  }

  private fun createExistingTaskController(): AgentPromptExistingTaskController {
    @Suppress("RAW_SCOPE_CREATION")
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
    return AgentPromptExistingTaskController(
      existingTaskListModel = javax.swing.DefaultListModel(),
      existingTaskList = com.intellij.ui.components.JBList(),
      popupScope = scope,
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptPaletteDraftControllerTest::class.java.classLoader),
      onStateChanged = {},
    )
  }

  private fun extension(): AgentPromptPaletteExtension {
    return object : AgentPromptPaletteExtension {
      override fun matches(contextItems: List<com.intellij.agent.workbench.prompt.core.AgentPromptContextItem>): Boolean = true

      override fun getTabTitle(): String = "Extension"

      override fun getInitialPromptText(project: com.intellij.openapi.project.Project): String = "extension prompt"

      override fun getSubmitActionId(): String? = null

      override fun getFooterHint(): String? = null

      override fun shouldAutoSelect(contextItems: List<com.intellij.agent.workbench.prompt.core.AgentPromptContextItem>): Boolean = false
    }
  }

  private class DraftControllerFixture(
    val controller: AgentPromptPaletteDraftController,
    val promptArea: EditorTextField,
    val tabbedPane: JTabbedPane,
    val contextState: AgentPromptPaletteContextState,
    val draftState: AgentPromptPaletteDraftState,
    val resolveTaskKeyHolder: Array<(JPanel?) -> String?>,
  ) {
    var resolveTaskKey: (JPanel?) -> String?
      get() = resolveTaskKeyHolder[0]
      set(value) {
        resolveTaskKeyHolder[0] = value
      }
  }
}
