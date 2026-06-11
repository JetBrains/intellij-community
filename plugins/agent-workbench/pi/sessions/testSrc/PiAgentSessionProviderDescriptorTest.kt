// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageMode
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessageStartupPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PiAgentSessionProviderDescriptorTest {
  private val descriptor = PiAgentSessionProviderDescriptor(
    executableResolver = { "pi" },
    sessionIdGenerator = { "pi-session-1" },
    cliAvailableProbe = { true },
    extensionLaunchResourcesResolver = {
      PiExtensionLaunchResources(
        extensionPath = Path.of("/tmp/pi-extension/agent-workbench-extension.ts"),
        stateFilePath = Path.of("/tmp/pi-extension/state/current-theme.txt"),
      )
    },
    statusLaunchEnvironmentResolver = { sessionId ->
      mapOf(
        PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE to "http://localhost:63342/agent-workbench/pi/status",
        PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE to "status-token-$sessionId",
      )
    },
  )

  @Test
  fun exposesPiProviderMetadata() {
    assertThat(descriptor.provider).isEqualTo(AgentSessionProvider.PI)
    assertThat(descriptor.displayPriority).isEqualTo(3)
    assertThat(descriptor.displayNameKey).isEqualTo("toolwindow.provider.pi")
    assertThat(descriptor.newSessionLabelKey).isEqualTo("toolwindow.action.new.session.pi")
    assertThat(descriptor.cliMissingMessageKey).isEqualTo("toolwindow.error.pi.cli")
    assertThat(descriptor.cliVisibilityPolicy).isEqualTo(AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE)
    assertThat(descriptor.terminalAgentKey).isEqualTo("pi")
    assertThat(descriptor.supportedLaunchModes).containsExactly(AgentSessionLaunchMode.STANDARD)
    assertThat(descriptor.promptOptions).isEmpty()
    assertThat(descriptor.supportsArchiveThread).isTrue()
    assertThat(descriptor.supportsUnarchiveThread).isTrue()
    assertThat(descriptor.supportsNewThreadRebind).isTrue()
    assertThat(descriptor.emitsScopedRefreshSignals).isTrue()
    assertThat(descriptor.refreshPathAfterCreateNewSession).isTrue()
  }

  @Test
  fun buildNewSessionLaunchSpecUsesSessionIdFlag(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(launchSpec.command).containsExactly(
      "pi", "--extension", "/tmp/pi-extension/agent-workbench-extension.ts", "--session-id", "pi-session-1"
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_THEME_STATE_ENVIRONMENT_VARIABLE,
      "/tmp/pi-extension/state/current-theme.txt",
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE,
      "http://localhost:63342/agent-workbench/pi/status",
    )
    assertThat(launchSpec.envVariables).containsEntry(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE, "status-token-pi-session-1")
    assertThat(launchSpec.preallocatedSessionId).isEqualTo("pi-session-1")
  }

  @Test
  fun buildResumeLaunchSpecUsesSessionFlag(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildResumeLaunchSpec("thread-1")

    assertThat(launchSpec.command).containsExactly(
      "pi", "--extension", "/tmp/pi-extension/agent-workbench-extension.ts", "--session", "thread-1"
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_THEME_STATE_ENVIRONMENT_VARIABLE,
      "/tmp/pi-extension/state/current-theme.txt",
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_STATUS_ENDPOINT_ENVIRONMENT_VARIABLE,
      "http://localhost:63342/agent-workbench/pi/status",
    )
    assertThat(launchSpec.envVariables).containsEntry(PI_STATUS_TOKEN_ENVIRONMENT_VARIABLE, "status-token-thread-1")
  }

  @Test
  fun buildLaunchSpecOmitsExtensionWhenExtensionSupportUnavailable(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = PiAgentSessionProviderDescriptor(
      executableResolver = { "pi" },
      sessionIdGenerator = { "pi-session-1" },
      cliAvailableProbe = { true },
      extensionLaunchResourcesResolver = { null },
    )

    val launchSpec = descriptor.buildNewSessionLaunchSpec(AgentSessionLaunchMode.STANDARD)

    assertThat(launchSpec.command).containsExactly("pi", "--session-id", "pi-session-1")
    assertThat(launchSpec.envVariables).isEmpty()
  }

  @Test
  fun buildLaunchSpecWithInitialMessageAppendsPositionalArgument(): Unit = runBlocking(Dispatchers.Default) {
    val launchSpec = descriptor.buildLaunchSpecWithInitialMessage(
      baseLaunchSpec = descriptor.buildResumeLaunchSpec("thread-1"),
      initialMessagePlan = AgentInitialMessagePlan(message = "Summarize changes"),
    )

    assertThat(launchSpec.command).containsExactly(
      "pi", "--extension", "/tmp/pi-extension/agent-workbench-extension.ts", "--session", "thread-1", "Summarize changes"
    )
    assertThat(launchSpec.envVariables).containsEntry(
      PI_THEME_STATE_ENVIRONMENT_VARIABLE,
      "/tmp/pi-extension/state/current-theme.txt",
    )
  }

  @Test
  fun buildInitialMessagePlanUsesStandardComposeDefault() {
    val plan = descriptor.buildInitialMessagePlan(
      AgentPromptInitialMessageRequest(
        prompt = "  Refactor this  ",
        contextItems = listOf(
          AgentPromptContextItem(
            rendererId = AgentPromptContextRendererIds.SNIPPET,
            title = "Selection",
            body = "val answer = 42",
            source = "editor",
          )
        ),
      )
    )

    assertThat(plan.mode).isEqualTo(AgentInitialMessageMode.STANDARD)
    assertThat(plan.startupPolicy).isEqualTo(AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND)
    assertThat(plan.message).startsWith("Refactor this\n\n### IDE Context")
  }

  @Test
  fun cliAvailabilityUsesInjectedProbe(): Unit = runBlocking(Dispatchers.Default) {
    val unavailableDescriptor = PiAgentSessionProviderDescriptor(
      sessionSource = emptySource(),
      executableResolver = { "pi" },
      cliAvailableProbe = { false },
    )

    assertThat(unavailableDescriptor.isCliAvailable()).isFalse()
  }

  @Test
  fun resolverTerminalAgentUsesPiLookupLocations() {
    val terminalAgent = PiCliSupport.resolverTerminalAgent

    assertThat(terminalAgent.agentKey.key).isEqualTo("pi")
    assertThat(terminalAgent.binaryName).isEqualTo("pi")
    assertThat(terminalAgent.posixKnownLocationCandidates).containsExactly("$" + "HOME/.local/bin", "/usr/local/bin")
    assertThat(terminalAgent.windowsKnownLocationCandidates).containsExactly(
      "$" + "HOME\\AppData\\Roaming\\npm",
      "$" + "HOME\\.local\\bin",
    )
  }

  @Test
  fun archiveUnarchiveAndRenameDelegateToThreadMutationBackend(): Unit = runBlocking(Dispatchers.Default) {
    var archivedPath: String? = null
    var archivedThreadId: String? = null
    var unarchivedPath: String? = null
    var unarchivedThreadId: String? = null
    var renamedPath: String? = null
    var renamedThreadId: String? = null
    var renamedName: String? = null
    val descriptor = PiAgentSessionProviderDescriptor(
      sessionSource = emptySource(),
      threadMutationBackend = object : PiSessionThreadMutationBackend {
        override fun renameThread(path: String, threadId: String, normalizedName: String): Boolean {
          renamedPath = path
          renamedThreadId = threadId
          renamedName = normalizedName
          return true
        }

        override fun archiveThread(path: String, threadId: String): Boolean {
          archivedPath = path
          archivedThreadId = threadId
          return true
        }

        override fun unarchiveThread(path: String, threadId: String): Boolean {
          unarchivedPath = path
          unarchivedThreadId = threadId
          return true
        }
      },
    )

    assertThat(descriptor.archiveThread("/tmp/project", "thread-1")).isTrue()
    assertThat(descriptor.unarchiveThread("/tmp/project", "thread-1")).isTrue()
    val renameAction = checkNotNull(descriptor.threadRenameAction)
    assertThat(renameAction("/tmp/project", "thread-1", "Renamed thread")).isTrue()
    assertThat(archivedPath).isEqualTo("/tmp/project")
    assertThat(archivedThreadId).isEqualTo("thread-1")
    assertThat(unarchivedPath).isEqualTo("/tmp/project")
    assertThat(unarchivedThreadId).isEqualTo("thread-1")
    assertThat(renamedPath).isEqualTo("/tmp/project")
    assertThat(renamedThreadId).isEqualTo("thread-1")
    assertThat(renamedName).isEqualTo("Renamed thread")
  }
}

private fun emptySource(): AgentSessionSource {
  return object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = AgentSessionProvider.PI

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }
}
