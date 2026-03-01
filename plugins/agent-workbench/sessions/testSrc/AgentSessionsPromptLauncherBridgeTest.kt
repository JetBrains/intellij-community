// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchError
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptLaunchRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderIcon
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.service.AgentSessionsPromptLauncherBridge
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class AgentSessionsPromptLauncherBridgeTest {
  @Test
  fun launchCreatesNewSessionForPromptRequest() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withService(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service ->
          val bridge = AgentSessionsPromptLauncherBridge { service }
          val request = promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH)

          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            providerBridge.createCalls.get() == 1
          }

          assertThat(providerBridge.lastCreatePath.get()).isEqualTo(INVALID_PROMPT_PROJECT_PATH)
          assertThat(providerBridge.lastCreateMode.get()).isEqualTo(AgentSessionLaunchMode.STANDARD)
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
          assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastStartupBaseCommand.get())
            .containsExactly("test", "create", INVALID_PROMPT_PROJECT_PATH, AgentSessionLaunchMode.STANDARD.name)
          assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")
        }
      }
    }
  }

  @Test
  fun launchFallsBackWhenStartupPromptCommandIsNotSupported() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      startupPromptCommandSupported = false,
    )
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withService(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service ->
          val bridge = AgentSessionsPromptLauncherBridge { service }
          val result = bridge.launch(promptLaunchRequest(projectPath = INVALID_PROMPT_PROJECT_PATH))

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          waitForCondition {
            providerBridge.createCalls.get() == 1
          }
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.startupCommandCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastStartupPrompt.get()).isEqualTo("composed:Refactor selected code")
        }
      }
    }
  }

  @Test
  fun launchRoutesPromptToExistingThreadWhenTargetThreadIdIsProvided() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withService(
          sessionSourcesProvider = {
            listOf(
              ScriptedSessionSource(
                provider = AgentSessionProvider.CODEX,
                listFromOpenProject = { path, _ ->
                  if (path == PROJECT_PATH) {
                    listOf(thread(id = "thread-existing", updatedAt = 200, provider = AgentSessionProvider.CODEX))
                  }
                  else {
                    emptyList()
                  }
                },
              )
            )
          },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service ->
          service.refresh()
          waitForCondition {
            val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
            project.hasLoaded && project.threads.any { thread -> thread.id == "thread-existing" }
          }

          val bridge = AgentSessionsPromptLauncherBridge { service }
          val request = promptLaunchRequest(targetThreadId = "thread-existing")

          val result = bridge.launch(request)

          assertThat(result.launched).isTrue()
          assertThat(result.error).isNull()
          assertThat(providerBridge.createCalls.get()).isZero()
          assertThat(providerBridge.composeCalls.get()).isEqualTo(1)
          assertThat(providerBridge.lastComposeRequest.get()).isEqualTo(request.initialMessageRequest)
        }
      }
    }
  }

  @Test
  fun launchReturnsThreadNotFoundWhenTargetThreadIsMissing() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withService(
          sessionSourcesProvider = {
            listOf(
              ScriptedSessionSource(
                provider = AgentSessionProvider.CODEX,
                listFromOpenProject = { path, _ ->
                  if (path == PROJECT_PATH) {
                    listOf(thread(id = "thread-existing", updatedAt = 200, provider = AgentSessionProvider.CODEX))
                  }
                  else {
                    emptyList()
                  }
                },
              )
            )
          },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service ->
          service.refresh()
          waitForCondition {
            service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
          }

          val bridge = AgentSessionsPromptLauncherBridge { service }
          val result = bridge.launch(promptLaunchRequest(targetThreadId = "thread-missing"))

          assertThat(result.launched).isFalse()
          assertThat(result.error).isEqualTo(AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND)
          assertThat(providerBridge.createCalls.get()).isZero()
          assertThat(providerBridge.composeCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun launchReturnsProviderUnavailableWhenBridgeIsMissing() {
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(emptyList())
    ) {
      runBlocking(Dispatchers.Default) {
        withService(
          sessionSourcesProvider = { emptyList() },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service ->
          val bridge = AgentSessionsPromptLauncherBridge { service }
          val result = bridge.launch(promptLaunchRequest(provider = AgentSessionProvider.CODEX))

          assertThat(result.launched).isFalse()
          assertThat(result.error).isEqualTo(AgentPromptLaunchError.PROVIDER_UNAVAILABLE)
        }
      }
    }
  }

  @Test
  fun launchReturnsUnsupportedLaunchModeWhenProviderDoesNotSupportMode() {
    val providerBridge = RecordingPromptLaunchProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
    )
    AgentSessionProviderBridges.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(providerBridge))
    ) {
      runBlocking(Dispatchers.Default) {
        withService(
          sessionSourcesProvider = { listOf(providerBridge.sessionSource) },
          projectEntriesProvider = { listOf(openProjectEntry(PROJECT_PATH, "Project A")) },
        ) { service ->
          val bridge = AgentSessionsPromptLauncherBridge { service }
          val result = bridge.launch(
            promptLaunchRequest(
              provider = AgentSessionProvider.CODEX,
              launchMode = AgentSessionLaunchMode.YOLO,
            )
          )

          assertThat(result.launched).isFalse()
          assertThat(result.error).isEqualTo(AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE)
          assertThat(providerBridge.createCalls.get()).isZero()
        }
      }
    }
  }

  @Test
  fun observeExistingThreadsFiltersProviderThreadsFromSharedState() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX))
              else emptyList()
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                listOf(
                  thread(id = "claude-1", updatedAt = 100, provider = AgentSessionProvider.CLAUDE),
                  thread(id = "claude-2", updatedAt = 300, provider = AgentSessionProvider.CLAUDE),
                )
              }
              else {
                emptyList()
              }
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val bridge = AgentSessionsPromptLauncherBridge { service }
      val snapshot = bridge.observeExistingThreads(
        projectPath = PROJECT_PATH,
        provider = AgentSessionProvider.CLAUDE,
      ).first { it.hasLoaded }

      assertThat(snapshot.threads.map { thread -> thread.id })
        .containsExactly("claude-2", "claude-1")
      assertThat(snapshot.hasError).isFalse()
    }
  }

  @Test
  fun refreshExistingThreadsBootstrapsWhenPathIsMissing() = runBlocking(Dispatchers.Default) {
    val openLoads = AtomicInteger(0)
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                openLoads.incrementAndGet()
                listOf(thread(id = "claude-1", updatedAt = 100, provider = AgentSessionProvider.CLAUDE))
              }
              else {
                emptyList()
              }
            },
          )
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      assertThat(service.state.value.projects).isEmpty()

      val bridge = AgentSessionsPromptLauncherBridge { service }
      bridge.refreshExistingThreads(projectPath = PROJECT_PATH, provider = AgentSessionProvider.CLAUDE)

      waitForCondition {
        val project = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH } ?: return@waitForCondition false
        project.hasLoaded && project.threads.map { thread -> thread.id } == listOf("claude-1")
      }

      assertThat(openLoads.get()).isEqualTo(1)
    }
  }

  @Test
  fun refreshExistingThreadsUsesProviderScopedRefreshForLoadedPath() = runBlocking(Dispatchers.Default) {
    val codexClosedLoads = AtomicInteger(0)
    val claudeClosedLoads = AtomicInteger(0)
    var claudeClosedThreadId = "claude-closed-1"

    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-open", updatedAt = 300, provider = AgentSessionProvider.CODEX))
              else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                codexClosedLoads.incrementAndGet()
                listOf(thread(id = "codex-closed", updatedAt = 250, provider = AgentSessionProvider.CODEX))
              }
              else {
                emptyList()
              }
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "claude-open", updatedAt = 200, provider = AgentSessionProvider.CLAUDE))
              else emptyList()
            },
            listFromClosedProject = { path ->
              if (path == PROJECT_PATH) {
                claudeClosedLoads.incrementAndGet()
                listOf(thread(id = claudeClosedThreadId, updatedAt = 400, provider = AgentSessionProvider.CLAUDE))
              }
              else {
                emptyList()
              }
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        val ids = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { thread -> thread.id } ?: return@waitForCondition false
        ids.contains("codex-open") && ids.contains("claude-open")
      }

      claudeClosedThreadId = "claude-closed-2"
      val bridge = AgentSessionsPromptLauncherBridge { service }
      bridge.refreshExistingThreads(projectPath = PROJECT_PATH, provider = AgentSessionProvider.CLAUDE)

      waitForCondition {
        val ids = service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.threads?.map { thread -> thread.id } ?: return@waitForCondition false
        ids.contains("codex-open") && ids.contains("claude-closed-2")
      }

      assertThat(claudeClosedLoads.get()).isEqualTo(1)
      assertThat(codexClosedLoads.get()).isEqualTo(0)
    }
  }

  @Test
  fun observeExistingThreadsMarksProviderWarningAsError() = runBlocking(Dispatchers.Default) {
    withService(
      sessionSourcesProvider = {
        listOf(
          ScriptedSessionSource(
            provider = AgentSessionProvider.CODEX,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) listOf(thread(id = "codex-1", updatedAt = 200, provider = AgentSessionProvider.CODEX))
              else emptyList()
            },
          ),
          ScriptedSessionSource(
            provider = AgentSessionProvider.CLAUDE,
            listFromOpenProject = { path, _ ->
              if (path == PROJECT_PATH) {
                throw IllegalStateException("Claude backend failed")
              }
              emptyList()
            },
          ),
        )
      },
      projectEntriesProvider = {
        listOf(openProjectEntry(PROJECT_PATH, "Project A"))
      },
    ) { service ->
      service.refresh()
      waitForCondition {
        service.state.value.projects.firstOrNull { it.path == PROJECT_PATH }?.hasLoaded == true
      }

      val bridge = AgentSessionsPromptLauncherBridge { service }
      val claudeSnapshot = bridge.observeExistingThreads(
        projectPath = PROJECT_PATH,
        provider = AgentSessionProvider.CLAUDE,
      ).first { it.hasLoaded && !it.isLoading }
      val codexSnapshot = bridge.observeExistingThreads(
        projectPath = PROJECT_PATH,
        provider = AgentSessionProvider.CODEX,
      ).first { it.hasLoaded && !it.isLoading }

      assertThat(claudeSnapshot.threads).isEmpty()
      assertThat(claudeSnapshot.hasError).isTrue()
      assertThat(codexSnapshot.threads.map { thread -> thread.id }).containsExactly("codex-1")
      assertThat(codexSnapshot.hasError).isFalse()
    }
  }
}

private const val INVALID_PROMPT_PROJECT_PATH: String = "invalid\u0000project"

private fun promptLaunchRequest(
  provider: AgentSessionProvider = AgentSessionProvider.CODEX,
  launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  projectPath: String = PROJECT_PATH,
  targetThreadId: String? = null,
): AgentPromptLaunchRequest {
  return AgentPromptLaunchRequest(
    provider = provider,
    projectPath = projectPath,
    launchMode = launchMode,
    initialMessageRequest = AgentPromptInitialMessageRequest(
      prompt = "Refactor selected code",
      contextItems = listOf(
        AgentPromptContextItem(
          kindId = "project",
          title = "Project",
          content = "project-a",
        )
      ),
    ),
    targetThreadId = targetThreadId,
    preferredDedicatedFrame = null,
  )
}

private class RecordingPromptLaunchProviderBridge(
  override val provider: AgentSessionProvider,
  private val supportedModes: Set<AgentSessionLaunchMode>,
  private val startupPromptCommandSupported: Boolean = true,
) : AgentSessionProviderBridge {
  val createCalls: AtomicInteger = AtomicInteger(0)
  val composeCalls: AtomicInteger = AtomicInteger(0)
  val startupCommandCalls: AtomicInteger = AtomicInteger(0)
  val lastCreatePath: AtomicReference<String?> = AtomicReference(null)
  val lastCreateMode: AtomicReference<AgentSessionLaunchMode?> = AtomicReference(null)
  val lastComposeRequest: AtomicReference<AgentPromptInitialMessageRequest?> = AtomicReference(null)
  val lastStartupBaseCommand: AtomicReference<List<String>?> = AtomicReference(null)
  val lastStartupPrompt: AtomicReference<String?> = AtomicReference(null)

  override val displayNameKey: String
    get() = "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex"

  override val icon: AgentSessionProviderIcon
    get() = AgentSessionProviderIcon(path = "icons/codex@14x14.svg", iconClass = this::class.java)

  override val supportedLaunchModes: Set<AgentSessionLaunchMode>
    get() = supportedModes

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider
      get() = this@RecordingPromptLaunchProviderBridge.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }

  override val cliMissingMessageKey: String
    get() = "toolwindow.error.cli"

  override fun isCliAvailable(): Boolean = true

  override fun buildResumeCommand(sessionId: String): List<String> = listOf("test", "resume", sessionId)

  override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> = listOf("test", "new", mode.name)

  override fun buildNewEntryCommand(): List<String> = listOf("test")

  override fun buildCommandWithInitialPrompt(baseCommand: List<String>, prompt: String): List<String>? {
    startupCommandCalls.incrementAndGet()
    lastStartupBaseCommand.set(baseCommand)
    lastStartupPrompt.set(prompt)
    if (!startupPromptCommandSupported) {
      return null
    }
    return baseCommand + listOf("--", prompt)
  }

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    createCalls.incrementAndGet()
    lastCreatePath.set(path)
    lastCreateMode.set(mode)
    return AgentSessionLaunchSpec(sessionId = null, command = listOf("test", "create", path, mode.name))
  }

  override fun composeInitialMessage(request: AgentPromptInitialMessageRequest): String {
    composeCalls.incrementAndGet()
    lastComposeRequest.set(request)
    return "composed:${request.prompt.trim()}"
  }
}
