// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchResult
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLauncherBridge
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextPickerRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptManualContextSourceBridge
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@TestApplication
class AgentPromptManualContextAvailabilityDecisionsTest {
  @Test
  fun dedicatedFrameUsesResolvedSourceProjectForManualContextAvailability() {
    val hostProject = projectProxy(name = "Agent Dedicated Frame", basePath = "/dedicated")
    val sourceProject = projectProxy(name = "Source Project", basePath = "/repo")
    val invocationData = invocationData(hostProject)
    val seenProjects = ArrayList<Project>()
    val availableSource = testManualContextSource(sourceId = "source.available") { project ->
      seenProjects.add(project)
      project === sourceProject
    }
    val unavailableSource = testManualContextSource(sourceId = "source.unavailable") { project ->
      seenProjects.add(project)
      false
    }

    val availability = resolveManualContextAvailability(
      hostProject = hostProject,
      invocationData = invocationData,
      launcher = testLauncher(sourceProject),
      sources = listOf(unavailableSource, availableSource),
    )

    assertThat(availability?.sourceProject).isSameAs(sourceProject)
    assertThat(availability?.sources).containsExactly(availableSource)
    assertThat(seenProjects).containsExactly(sourceProject, sourceProject)
  }

  @Test
  fun unresolvedDedicatedFrameHidesManualContextAvailability() {
    val hostProject = projectProxy(name = "Agent Dedicated Frame", basePath = "/dedicated")
    val invocationData = invocationData(hostProject)
    var checkedProject: Project? = null
    val source = testManualContextSource(sourceId = "source.available") { project ->
      checkedProject = project
      true
    }

    val availability = resolveManualContextAvailability(
      hostProject = hostProject,
      invocationData = invocationData,
      launcher = testLauncher(null),
      sources = listOf(source),
    )

    assertThat(availability).isNull()
    assertThat(checkedProject).isNull()
  }

  private fun invocationData(project: Project): AgentPromptInvocationData {
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent with Context",
      actionPlace = "test",
      invokedAtMs = 0,
    )
  }

  private fun testLauncher(sourceProject: Project?): AgentPromptLauncherBridge {
    return object : AgentPromptLauncherBridge {
      override fun launch(request: AgentPromptLaunchRequest): AgentPromptLaunchResult {
        return AgentPromptLaunchResult.SUCCESS
      }

      override fun resolveSourceProject(invocationData: AgentPromptInvocationData): Project? {
        return sourceProject
      }
    }
  }

  private fun testManualContextSource(
    sourceId: String,
    availability: (Project) -> Boolean,
  ): AgentPromptManualContextSourceBridge {
    return object : AgentPromptManualContextSourceBridge {
      override val sourceId: String = sourceId

      override fun isAvailable(project: Project): Boolean {
        return availability(project)
      }

      override fun getDisplayName(): String = sourceId

      override fun showPicker(request: AgentPromptManualContextPickerRequest) {
        error("Picker is not expected in availability tests")
      }
    }
  }

  private fun projectProxy(name: String, basePath: String?): Project {
    val handler = InvocationHandler { proxy, method, args ->
      when (method.name) {
        "getName" -> name
        "getBasePath" -> basePath
        "isOpen" -> true
        "isDisposed" -> false
        "toString" -> "MockProject($name)"
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> proxy === args?.firstOrNull()
        else -> null
      }
    }
    return Proxy.newProxyInstance(
      ProjectManager::class.java.classLoader,
      arrayOf(Project::class.java),
      handler,
    ) as Project
  }
}
