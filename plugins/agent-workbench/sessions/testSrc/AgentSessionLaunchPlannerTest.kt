// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchIntent
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchOperation
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchPlanner
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionLaunchPlannerTest {
  @Test
  fun autoSettingsDoNotResolveModelCatalogByDefault(): Unit = runBlocking(Dispatchers.Default) {
    var catalogRefreshes = 0
    val descriptor = modelCatalogProvider(
      resolveCatalogForAutoSettings = false,
      listModels = {
        catalogRefreshes++
        listOf(AgentPromptGenerationModel(id = "model-1", displayName = "Model 1"))
      },
    )

    val plannedLaunch = planWithProvider(descriptor)

    assertThat(catalogRefreshes).isZero()
    assertThat(plannedLaunch.generationModelCatalog).isEmpty()
    assertThat(plannedLaunch.launchSpec.command).containsExactly("test", "new", "STANDARD")
  }

  @Test
  fun autoSettingsResolveModelCatalogForOptInProvider(): Unit = runBlocking(Dispatchers.Default) {
    var catalogRefreshes = 0
    val model = AgentPromptGenerationModel(id = "model-1", displayName = "Model 1")
    val descriptor = modelCatalogProvider(
      resolveCatalogForAutoSettings = true,
      listModels = {
        catalogRefreshes++
        listOf(model)
      },
    )

    val plannedLaunch = planWithProvider(descriptor)

    assertThat(catalogRefreshes).isEqualTo(1)
    assertThat(plannedLaunch.generationModelCatalog).containsExactly(model)
    assertThat(plannedLaunch.launchSpec.command).containsExactly("test", "new", "STANDARD", "catalog:model-1")
  }

  @Test
  fun autoSettingsKeepLaunchSpecWhenOptInModelCatalogRefreshFails(): Unit = runBlocking(Dispatchers.Default) {
    val descriptor = modelCatalogProvider(
      resolveCatalogForAutoSettings = true,
      listModels = { error("No model provider is configured") },
    )

    val plannedLaunch = planWithProvider(descriptor)

    assertThat(plannedLaunch.generationModelCatalog).isEmpty()
    assertThat(plannedLaunch.launchSpec.command).containsExactly("test", "new", "STANDARD")
  }

  private fun planWithProvider(
    descriptor: TestAgentSessionProviderDescriptor,
  ) = AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
    runBlocking(Dispatchers.Default) {
      AgentSessionLaunchPlanner.plan(
        intent = AgentSessionLaunchIntent(
          projectPath = "/tmp/project",
          provider = AgentSessionProvider.CODEX,
          operation = AgentSessionLaunchOperation.NEW,
        ),
        initialMessagePlan = AgentInitialMessagePlan.EMPTY,
      )
    }
  }

  private fun modelCatalogProvider(
    resolveCatalogForAutoSettings: Boolean,
    listModels: suspend () -> List<AgentPromptGenerationModel>,
  ): TestAgentSessionProviderDescriptor {
    return object : TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    ) {
      override val supportsGenerationModelSelection: Boolean
        get() = true

      override val resolvesGenerationModelCatalogForAutoSettings: Boolean
        get() = resolveCatalogForAutoSettings

      override suspend fun listAvailableGenerationModels(project: Project?): List<AgentPromptGenerationModel> {
        return listModels()
      }

      override fun applyGenerationModelCatalog(
        baseLaunchSpec: AgentSessionTerminalLaunchSpec,
        generationSettings: AgentPromptGenerationSettings,
        generationModelCatalog: List<AgentPromptGenerationModel>,
      ): AgentSessionTerminalLaunchSpec {
        if (generationModelCatalog.isEmpty()) {
          return baseLaunchSpec
        }
        return baseLaunchSpec.copy(command = baseLaunchSpec.command + "catalog:${generationModelCatalog.joinToString(",") { it.id }}")
      }
    }
  }
}
