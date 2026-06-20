// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AGENT_CHAT_THREAD_OUTLINE_TOOL_WINDOW_ID
import com.intellij.agent.workbench.settings.AgentWorkbenchSettings
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.frame.AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchProjectFrameCapabilitiesProvider
import com.intellij.agent.workbench.sessions.frame.isAgentChatThreadOutlineToolWindowAvailable
import com.intellij.agent.workbench.sessions.frame.refreshAgentChatThreadOutlineToolWindowAvailability
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.toolWindow.ProjectFrameToolWindowLayoutService
import com.intellij.toolWindow.ToolWindowLayoutProfileService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchProjectFrameCapabilitiesProviderTest {
  private val provider = AgentWorkbenchProjectFrameCapabilitiesProvider()

  @Test
  fun dedicatedProjectContributesBackgroundSuppressionCapability() {
    val project = testProject(basePath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath())

    val capabilities = provider.getCapabilities(project)

    assertThat(capabilities)
      .contains(ProjectFrameCapability.SUPPRESS_VCS_UI)
      .contains(ProjectFrameCapability.SUPPRESS_PROJECT_VIEW)
      .contains(ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES)
      .contains(ProjectFrameCapability.SUPPRESS_INDEXING_ACTIVITIES)
      .contains(ProjectFrameCapability.EXCLUDE_FROM_PROJECT_WINDOW_SWITCH_ORDER)
  }

  @Test
  fun nonDedicatedProjectContributesNoCapabilities() {
    val project = testProject(basePath = "/tmp/not-agent-workbench-dedicated")

    assertThat(provider.getCapabilities(project)).isEmpty()
  }

  @Test
  fun uiPolicyRequiresAggregatedVcsSuppressionCapability() {
    val project = testProject(basePath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath())

    assertThat(
      provider.getUiPolicy(
        project,
        setOf(ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES),
      )
    ).isNull()

    assertThat(
      provider.getUiPolicy(
        project,
        setOf(ProjectFrameCapability.SUPPRESS_VCS_UI),
      )
    ).isNotNull()
  }

  @Test
  fun dedicatedLayoutSuppressesProjectAndStructureButKeepsThreadOutlineRegistered() {
    val suppressedToolWindowIds = service<ProjectFrameToolWindowLayoutService>().getSuppressedToolWindowIds(
      frameType = null,
      profileId = AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID,
    )

    assertThat(suppressedToolWindowIds)
      .contains(ToolWindowId.PROJECT_VIEW)
      .contains(ToolWindowId.STRUCTURE_VIEW)
      .doesNotContain(AGENT_CHAT_THREAD_OUTLINE_TOOL_WINDOW_ID)

    val profile = service<ToolWindowLayoutProfileService>().getProfile(
      project = testProject(basePath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath()),
      profileId = AGENT_WORKBENCH_DEDICATED_LAYOUT_PROFILE_ID,
      isNewUi = true,
    )
    assertThat(profile).isNotNull()
    assertThat(profile!!.migrationVersion).isEqualTo(5)
  }

  @Test
  fun threadOutlineToolWindowAvailabilityFollowsFrameAndOpenMode() {
    val settings = AgentWorkbenchSettings.getInstance()
    settings.loadState(AgentWorkbenchSettings.SettingsState())
    try {
      val dedicatedProject = testProject(basePath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath())
      val sourceProject = testProject(basePath = "/tmp/not-agent-workbench-dedicated")

      assertThat(isAgentChatThreadOutlineToolWindowAvailable(dedicatedProject)).isTrue()
      assertThat(isAgentChatThreadOutlineToolWindowAvailable(sourceProject)).isFalse()

      AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)

      assertThat(isAgentChatThreadOutlineToolWindowAvailable(dedicatedProject)).isTrue()
      assertThat(isAgentChatThreadOutlineToolWindowAvailable(sourceProject)).isTrue()
    }
    finally {
      settings.loadState(AgentWorkbenchSettings.SettingsState())
    }
  }

  @Test
  fun refreshesThreadOutlineToolWindowAvailabilityForOpenProjects() {
    val settings = AgentWorkbenchSettings.getInstance()
    settings.loadState(AgentWorkbenchSettings.SettingsState())
    try {
      val dedicatedProject = testProject(basePath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath())
      val sourceProject = testProject(basePath = "/tmp/not-agent-workbench-dedicated")
      val dedicatedToolWindow = testToolWindow()
      val sourceToolWindow = testToolWindow()
      val updates = LinkedHashMap<ToolWindow, Boolean>()

      val updatedCount = refreshAgentChatThreadOutlineToolWindowAvailability(
        projects = arrayOf(dedicatedProject, sourceProject),
        toolWindowProvider = { project ->
          when (project) {
            dedicatedProject -> dedicatedToolWindow
            sourceProject -> sourceToolWindow
            else -> null
          }
        },
        setAvailable = { toolWindow, available -> updates[toolWindow] = available },
      )

      assertThat(updatedCount).isEqualTo(2)
      assertThat(updates).containsEntry(dedicatedToolWindow, true)
      assertThat(updates).containsEntry(sourceToolWindow, false)

      updates.clear()
      AgentChatOpenModeSettings.setOpenInDedicatedFrame(false)

      refreshAgentChatThreadOutlineToolWindowAvailability(
        projects = arrayOf(dedicatedProject, sourceProject),
        toolWindowProvider = { project -> if (project == sourceProject) sourceToolWindow else null },
        setAvailable = { toolWindow, available -> updates[toolWindow] = available },
      )

      assertThat(updates).containsExactlyEntriesOf(mapOf(sourceToolWindow to true))
    }
    finally {
      settings.loadState(AgentWorkbenchSettings.SettingsState())
    }
  }
}

private fun testProject(basePath: String): Project {
  val handler = java.lang.reflect.InvocationHandler { proxy, method, args ->
    when (method.name) {
      "getBasePath" -> basePath
      "isDisposed" -> false
      "toString" -> "Project($basePath)"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> null
    }
  }
  return Proxy.newProxyInstance(Project::class.java.classLoader, arrayOf(Project::class.java), handler) as Project
}

private fun testToolWindow(): ToolWindow {
  val handler = java.lang.reflect.InvocationHandler { proxy, method, args ->
    when (method.name) {
      "toString" -> "ToolWindow(${System.identityHashCode(proxy)})"
      "hashCode" -> System.identityHashCode(proxy)
      "equals" -> proxy === args?.firstOrNull()
      else -> null
    }
  }
  return Proxy.newProxyInstance(ToolWindow::class.java.classLoader, arrayOf(ToolWindow::class.java), handler) as ToolWindow
}
