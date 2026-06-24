// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.model.AgentSessionProviderLoadState
import com.intellij.agent.workbench.sessions.service.AgentSessionSourceLoadResult
import com.intellij.agent.workbench.sessions.service.mergeAgentSessionSourceLoadResults
import com.intellij.agent.workbench.sessions.service.mergeProviderLoadMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionLoadAggregationTest {
  @Test
  fun returnsErrorWhenAllSourcesFail() {
    val codexFailure = IllegalStateException("codex failed")
    val claudeFailure = IllegalStateException("claude failed")

    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.from("codex"), Result.failure(codexFailure)),
        AgentSessionSourceLoadResult(AgentSessionProvider.from("claude"), Result.failure(claudeFailure)),
      ),
      resolveErrorMessage = { provider, _ -> "${provider.value} unavailable" },
    )

    assertThat(result.threads).isEmpty()
    assertThat(result.errorMessage).isEqualTo("codex unavailable")
    assertThat(result.hasUnknownThreadCount).isFalse()
    assertThat(result.providerWarnings).isEmpty()
  }

  @Test
  fun reportsProviderWarningWhenAnySourceFailsButAtLeastOneSucceeds() {
    val codexFailure = IllegalStateException("codex failed")

    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.from("codex"), Result.failure(codexFailure)),
        AgentSessionSourceLoadResult(AgentSessionProvider.from("claude"), Result.success(emptyList())),
      ),
      resolveErrorMessage = { provider, _ -> "${provider.value} unavailable" },
      resolveWarningMessage = { provider, _ -> "${provider.value} warning" },
    )

    assertThat(result.threads).isEmpty()
    assertThat(result.errorMessage).isNull()
    assertThat(result.hasUnknownThreadCount).isFalse()
    assertThat(result.providerWarnings).hasSize(1)
    val warning = result.providerWarnings.single()
    assertThat(warning.provider).isEqualTo(AgentSessionProvider.from("codex"))
    assertThat(warning.message).isEqualTo("codex warning")
  }

  @Test
  fun mergesSuccessfulResultsAndSortsByUpdatedTime() {
    val codexThread = AgentSessionThread(
      id = "codex-1",
      title = "Codex Session",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.from("codex"),
    )
    val claudeThread = AgentSessionThread(
      id = "claude-1",
      title = "Claude Session",
      updatedAt = 200,
      archived = false,
      provider = AgentSessionProvider.from("claude"),
    )

    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.from("codex"), Result.success(listOf(codexThread))),
        AgentSessionSourceLoadResult(AgentSessionProvider.from("claude"), Result.success(listOf(claudeThread))),
      ),
      resolveErrorMessage = { _, _ -> "error" },
    )

    assertThat(result.errorMessage).isNull()
    assertThat(result.hasUnknownThreadCount).isFalse()
    assertThat(result.providerLoadStates)
      .containsEntry(AgentSessionProvider.from("codex"), AgentSessionProviderLoadState.LOADED)
      .containsEntry(AgentSessionProvider.from("claude"), AgentSessionProviderLoadState.LOADED)
    assertThat(result.threads.map { it.id }).containsExactly("claude-1", "codex-1")
    assertThat(result.providerWarnings).isEmpty()
  }

  @Test
  fun recordsProviderLoadStatesForSuccessesAndFailures() {
    val codexFailure = IllegalStateException("codex failed")

    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.from("codex"), Result.failure(codexFailure)),
        AgentSessionSourceLoadResult(AgentSessionProvider.from("claude"), Result.success(emptyList())),
      ),
      resolveErrorMessage = { provider, _ -> "${provider.value} unavailable" },
    )

    assertThat(result.providerLoadStates)
      .containsEntry(AgentSessionProvider.from("codex"), AgentSessionProviderLoadState.FAILED)
      .containsEntry(AgentSessionProvider.from("claude"), AgentSessionProviderLoadState.LOADED)
  }

  @Test
  fun mergesEqualUpdatedTimesDeterministically() {
    val codexThread = AgentSessionThread(
      id = "codex-1",
      title = "Codex Session",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.from("codex"),
    )
    val claudeThread = AgentSessionThread(
      id = "claude-1",
      title = "Claude Session",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.from("claude"),
    )

    fun merge(sourceResults: List<AgentSessionSourceLoadResult>): List<String> {
      return mergeAgentSessionSourceLoadResults(
        sourceResults = sourceResults,
        resolveErrorMessage = { _, _ -> "error" },
      ).threads.map { it.id }
    }

    val codexThenClaude = merge(
      listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.from("codex"), Result.success(listOf(codexThread))),
        AgentSessionSourceLoadResult(AgentSessionProvider.from("claude"), Result.success(listOf(claudeThread))),
      )
    )
    val claudeThenCodex = merge(
      listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.from("claude"), Result.success(listOf(claudeThread))),
        AgentSessionSourceLoadResult(AgentSessionProvider.from("codex"), Result.success(listOf(codexThread))),
      )
    )

    assertThat(codexThenClaude).containsExactly("claude-1", "codex-1")
    assertThat(claudeThenCodex).containsExactly("claude-1", "codex-1")
  }

  @Test
  fun marksUnknownThreadCountWhenSuccessfulSourceCannotReportExactTotal() {
    val codexThread = AgentSessionThread(
      id = "codex-1",
      title = "Codex Session",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.from("codex"),
    )

    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(
          provider = AgentSessionProvider.from("codex"),
          result = Result.success(listOf(codexThread)),
          hasUnknownTotal = true,
        ),
      ),
      resolveErrorMessage = { _, _ -> "error" },
    )

    assertThat(result.errorMessage).isNull()
    assertThat(result.hasUnknownThreadCount).isTrue()
    assertThat(result.providerWarnings).isEmpty()
  }

  @Test
  fun doesNotMarkUnknownThreadCountForFailedProvider() {
    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(
          provider = AgentSessionProvider.from("codex"),
          result = Result.failure(IllegalStateException("codex failed")),
          hasUnknownTotal = true,
        ),
      ),
      resolveErrorMessage = { _, _ -> "error" },
    )

    assertThat(result.providerLoadStates).containsEntry(AgentSessionProvider.from("codex"), AgentSessionProviderLoadState.FAILED)
    assertThat(result.hasUnknownThreadCount).isFalse()
  }

  @Test
  fun mergesProviderLoadMetadataByReplacingUnknownCountOnlyForUpdatedProviders() {
    val metadata = mergeProviderLoadMetadata(
      currentProviderLoadStates = mapOf(
        AgentSessionProvider.from("codex") to AgentSessionProviderLoadState.LOADED,
        AgentSessionProvider.from("claude") to AgentSessionProviderLoadState.LOADED,
      ),
      currentProvidersWithUnknownThreadCount = setOf(AgentSessionProvider.from("codex"), AgentSessionProvider.from("claude")),
      providerLoadStateUpdates = mapOf(AgentSessionProvider.from("codex") to AgentSessionProviderLoadState.LOADED),
      updatedProvidersWithUnknownThreadCount = emptySet(),
    )

    assertThat(metadata.providerLoadStates)
      .containsEntry(AgentSessionProvider.from("codex"), AgentSessionProviderLoadState.LOADED)
      .containsEntry(AgentSessionProvider.from("claude"), AgentSessionProviderLoadState.LOADED)
    assertThat(metadata.providersWithUnknownThreadCount).containsExactly(AgentSessionProvider.from("claude"))
  }

  @Test
  fun mergeProviderLoadMetadataClearsUnknownCountForLoadingProvider() {
    val metadata = mergeProviderLoadMetadata(
      currentProviderLoadStates = mapOf(
        AgentSessionProvider.from("codex") to AgentSessionProviderLoadState.LOADED,
        AgentSessionProvider.from("claude") to AgentSessionProviderLoadState.LOADED,
      ),
      currentProvidersWithUnknownThreadCount = setOf(AgentSessionProvider.from("codex"), AgentSessionProvider.from("claude")),
      providerLoadStateUpdates = mapOf(AgentSessionProvider.from("codex") to AgentSessionProviderLoadState.LOADING),
      updatedProvidersWithUnknownThreadCount = emptySet(),
    )

    assertThat(metadata.providerLoadStates)
      .containsEntry(AgentSessionProvider.from("codex"), AgentSessionProviderLoadState.LOADING)
      .containsEntry(AgentSessionProvider.from("claude"), AgentSessionProviderLoadState.LOADED)
    assertThat(metadata.providersWithUnknownThreadCount).containsExactly(AgentSessionProvider.from("claude"))
  }
}
