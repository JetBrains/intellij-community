// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class AgentWorkbenchProjectFrameCapabilitiesProviderTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  private val provider = AgentWorkbenchProjectFrameCapabilitiesProvider()

  @Test
  fun dedicatedProjectContributesBackgroundSuppressionCapability() {
    val project = testProject(basePath = AgentWorkbenchDedicatedFrameProjectManager.dedicatedProjectPath())

    val capabilities = provider.getCapabilities(project)

    assertThat(capabilities)
      .contains(ProjectFrameCapability.SUPPRESS_VCS_UI)
      .contains(ProjectFrameCapability.SUPPRESS_PROJECT_VIEW)
      .contains(ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES)
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
