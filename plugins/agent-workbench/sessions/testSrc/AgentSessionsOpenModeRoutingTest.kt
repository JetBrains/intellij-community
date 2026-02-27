// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.codex.AgentChatOpenRoute
import com.intellij.agent.workbench.sessions.codex.resolveAgentChatOpenRoute
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AgentSessionsOpenModeRoutingTest {
  @Test
  fun dedicatedModeRoutesToDedicatedFrameWhenSourceProjectIsOpen() {
    val route = resolveAgentChatOpenRoute(
      openInDedicatedFrame = true,
      hasOpenSourceProject = true,
    )

    assertThat(route).isEqualTo(AgentChatOpenRoute.DedicatedFrame)
  }

  @Test
  fun dedicatedModeRoutesToDedicatedFrameWhenSourceProjectIsClosed() {
    val route = resolveAgentChatOpenRoute(
      openInDedicatedFrame = true,
      hasOpenSourceProject = false,
    )

    assertThat(route).isEqualTo(AgentChatOpenRoute.DedicatedFrame)
  }

  @Test
  fun currentProjectModeRoutesToOpenProjectWhenAlreadyOpen() {
    val route = resolveAgentChatOpenRoute(
      openInDedicatedFrame = false,
      hasOpenSourceProject = true,
    )

    assertThat(route).isEqualTo(AgentChatOpenRoute.CurrentProject)
  }

  @Test
  fun currentProjectModeRoutesToOpenSourceProjectWhenClosedAndPathValid() {
    val route = resolveAgentChatOpenRoute(
      openInDedicatedFrame = false,
      hasOpenSourceProject = false,
    )

    assertThat(route).isEqualTo(AgentChatOpenRoute.OpenSourceProject)
  }

  @Test
  fun threadAndSubAgentUseTheSameRouteDecision() {
    val threadRoute = resolveAgentChatOpenRoute(
      openInDedicatedFrame = false,
      hasOpenSourceProject = false,
    )
    val subAgentRoute = resolveAgentChatOpenRoute(
      openInDedicatedFrame = false,
      hasOpenSourceProject = false,
    )

    assertThat(threadRoute).isEqualTo(AgentChatOpenRoute.OpenSourceProject)
    assertThat(subAgentRoute).isEqualTo(threadRoute)
  }
}
