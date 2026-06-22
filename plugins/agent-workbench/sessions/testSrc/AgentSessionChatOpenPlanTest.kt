// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.platform.ai.agent.core.session.AgentSubAgent
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.platform.ai.agent.sessions.core.launch.resolveAgentSessionChatOpenPlan
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.ai.agent.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionChatOpenPlanTest {
  @Test
  fun resolvesBaseThreadPayload() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )

      val payload = resolveAgentSessionChatOpenPlan(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = null,
        launchSpecOverride = null,
        resumeLaunchSpecProvider = ::testResumeLaunchSpec,
      )

      assertThat(payload.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1"))
      assertThat(payload.runtimeThreadId).isEqualTo("thread-1")
      assertThat(payload.launchSpec.command)
        .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "thread-1")
      assertThat(payload.threadTitle).isEqualTo("Parent title")
      assertThat(payload.subAgentId).isNull()
    }
  }

  @Test
  fun resolvesSubAgentPayloadWithSubAgentRuntimeThreadIdAndTitle() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )
      val subAgent = AgentSubAgent(id = "sub-1", name = "Sub-agent label")

      val payload = resolveAgentSessionChatOpenPlan(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = subAgent,
        launchSpecOverride = null,
        resumeLaunchSpecProvider = ::testResumeLaunchSpec,
      )

      assertThat(payload.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.CODEX, "thread-1"))
      assertThat(payload.runtimeThreadId).isEqualTo("sub-1")
      assertThat(payload.launchSpec.command)
        .containsExactly("codex", "-c", "check_for_update_on_startup=false", "resume", "sub-1")
      assertThat(payload.threadTitle).isEqualTo("Sub-agent label")
      assertThat(payload.subAgentId).isEqualTo("sub-1")
    }
  }

  @Test
  fun resolvesSubAgentPayloadWithSubAgentIdWhenNameBlank() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )
      val subAgent = AgentSubAgent(id = "sub-1", name = "")

      val payload = resolveAgentSessionChatOpenPlan(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = subAgent,
        launchSpecOverride = null,
        resumeLaunchSpecProvider = ::testResumeLaunchSpec,
      )

      assertThat(payload.threadTitle).isEqualTo("sub-1")
    }
  }

  @Test
  fun keepsLaunchSpecOverrideWhenProvided() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )
      val subAgent = AgentSubAgent(id = "sub-1", name = "Sub-agent label")

      val payload = resolveAgentSessionChatOpenPlan(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = subAgent,
        launchSpecOverride = AgentSessionTerminalLaunchSpec(
          command = listOf("custom", "resume", "sub-1"),
          envVariables = mapOf("CUSTOM_ENV" to "1"),
        ),
      )

      assertThat(payload.launchSpec.command).containsExactly("custom", "resume", "sub-1")
      assertThat(payload.launchSpec.envVariables).containsExactlyEntriesOf(mapOf("CUSTOM_ENV" to "1"))
    }
  }

  @Test
  fun resolvesYoloResumeLaunchSpecWhenRequested() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )

      val payload = resolveAgentSessionChatOpenPlan(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = null,
        launchSpecOverride = null,
        launchMode = AgentSessionLaunchMode.YOLO,
        resumeLaunchSpecProvider = ::testResumeLaunchSpec,
      )

      assertThat(payload.launchSpec.command)
        .containsExactly("codex", "--yolo", "resume", "thread-1")
    }
  }

  @Test
  fun resolvesLaunchSpecFromAugmenterWhenOverrideMissing() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )

      val payload = withTestLaunchSpecAugmenter {
        resolveAgentSessionChatOpenPlan(
          projectPath = PROJECT_PATH,
          thread = thread,
          subAgent = null,
          launchSpecOverride = null,
          resumeLaunchSpecProvider = { _, sessionId, _ ->
            AgentSessionTerminalLaunchSpec(
              command = listOf("codex", "resume", sessionId),
              envVariables = mapOf("DISABLE_AUTOUPDATER" to "1"),
            )
          },
        )
      }

      assertThat(payload.launchSpec.command).containsExactly("codex", "resume", "thread-1")
      assertThat(payload.launchSpec.envVariables)
        .containsEntry("DISABLE_AUTOUPDATER", "1")
        .containsEntry(AGENT_WORKBENCH_TEST_ENV_NAME, AGENT_WORKBENCH_TEST_ENV_VALUE)
      assertThat(payload.launchSpec.envVariables.getValue("PATH").split(java.io.File.pathSeparator))
        .containsExactly(AGENT_WORKBENCH_TEST_PATH_PREPEND)
    }
  }

  @Test
  fun preservesRemoteResumeCommandFromResumeLaunchProvider() {
    runBlocking(Dispatchers.Default) {
      val thread = AgentSessionThread(
        id = "thread-1",
        title = "Parent title",
        updatedAt = 1,
        archived = false,
        provider = AgentSessionProvider.CODEX,
      )

      val payload = resolveAgentSessionChatOpenPlan(
        projectPath = PROJECT_PATH,
        thread = thread,
        subAgent = null,
        launchSpecOverride = null,
        resumeLaunchSpecProvider = { provider, sessionId, _ ->
          check(provider == AgentSessionProvider.CODEX)
          AgentSessionTerminalLaunchSpec(
            command = listOf(
              "codex",
              "-c",
              "check_for_update_on_startup=false",
              "--remote",
              "ws://127.0.0.1:31337",
              "resume",
              sessionId,
            ),
          )
        },
      )

      assertThat(payload.launchSpec.command).containsExactly(
        "codex",
        "-c",
        "check_for_update_on_startup=false",
        "--remote",
        "ws://127.0.0.1:31337",
        "resume",
        "thread-1",
      )
    }
  }

  @Test
  fun resolvesAutoModelCatalogForResumeLaunchesWithProjectContext() {
    val project = ProjectManager.getInstance().defaultProject
    var catalogProject: Project? = null
    val model = AgentPromptGenerationModel(id = "model-1", displayName = "Model 1")
    val descriptor = object : TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    ) {
      override val supportsGenerationModelSelection: Boolean
        get() = true

      override val resolvesGenerationModelCatalogForAutoSettings: Boolean
        get() = true

      override suspend fun listAvailableGenerationModels(project: Project?): List<AgentPromptGenerationModel> {
        catalogProject = project
        return listOf(model)
      }

      override fun applyGenerationModelCatalog(
        baseLaunchSpec: AgentSessionTerminalLaunchSpec,
        generationSettings: AgentPromptGenerationSettings,
        generationModelCatalog: List<AgentPromptGenerationModel>,
      ): AgentSessionTerminalLaunchSpec {
        if (generationModelCatalog.isEmpty()) {
          return baseLaunchSpec
        }
        return baseLaunchSpec.copy(
          command = baseLaunchSpec.command + "catalog:${generationModelCatalog.joinToString(",") { it.id }}",
          envVariables = baseLaunchSpec.envVariables + mapOf("MODEL_COUNT" to generationModelCatalog.size.toString()),
        )
      }
    }

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        val thread = AgentSessionThread(
          id = "thread-1",
          title = "Parent title",
          updatedAt = 1,
          archived = false,
          provider = AgentSessionProvider.CODEX,
        )

        val payload = resolveAgentSessionChatOpenPlan(
          projectPath = PROJECT_PATH,
          thread = thread,
          subAgent = null,
          launchSpecOverride = null,
          generationSettings = AgentPromptGenerationSettings.AUTO,
          project = project,
        )

        assertThat(catalogProject).isSameAs(project)
        assertThat(payload.launchSpec.command).containsExactly("test", "resume", "thread-1", "catalog:model-1")
        assertThat(payload.launchSpec.envVariables).containsEntry("MODEL_COUNT", "1")
      }
    }
  }
}

private fun testResumeLaunchSpec(
  provider: AgentSessionProvider,
  sessionId: String,
  launchMode: AgentSessionLaunchMode,
): AgentSessionTerminalLaunchSpec {
  check(provider == AgentSessionProvider.CODEX)
  if (launchMode == AgentSessionLaunchMode.YOLO) {
    return AgentSessionTerminalLaunchSpec(
      command = listOf("codex", "--yolo", "resume", sessionId),
    )
  }
  return AgentSessionTerminalLaunchSpec(
    command = listOf("codex", "-c", "check_for_update_on_startup=false", "resume", sessionId),
  )
}
