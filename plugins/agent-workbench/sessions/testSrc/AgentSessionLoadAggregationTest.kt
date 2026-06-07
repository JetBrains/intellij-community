// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
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
        AgentSessionSourceLoadResult(AgentSessionProvider.CODEX, Result.failure(codexFailure)),
        AgentSessionSourceLoadResult(AgentSessionProvider.CLAUDE, Result.failure(claudeFailure)),
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
        AgentSessionSourceLoadResult(AgentSessionProvider.CODEX, Result.failure(codexFailure)),
        AgentSessionSourceLoadResult(AgentSessionProvider.CLAUDE, Result.success(emptyList())),
      ),
      resolveErrorMessage = { provider, _ -> "${provider.value} unavailable" },
      resolveWarningMessage = { provider, _ -> "${provider.value} warning" },
    )

    assertThat(result.threads).isEmpty()
    assertThat(result.errorMessage).isNull()
    assertThat(result.hasUnknownThreadCount).isFalse()
    assertThat(result.providerWarnings).hasSize(1)
    val warning = result.providerWarnings.single()
    assertThat(warning.provider).isEqualTo(AgentSessionProvider.CODEX)
    assertThat(warning.message).isEqualTo("codex warning")
  }

  @Test
  fun mergesSuccessfulResultsAndSortsByUpdatedTime() {
    val codexThread = AgentSessionThread(
      id = "codex-1",
      title = "Codex Session",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.CODEX,
    )
    val claudeThread = AgentSessionThread(
      id = "claude-1",
      title = "Claude Session",
      updatedAt = 200,
      archived = false,
      provider = AgentSessionProvider.CLAUDE,
    )

    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.CODEX, Result.success(listOf(codexThread))),
        AgentSessionSourceLoadResult(AgentSessionProvider.CLAUDE, Result.success(listOf(claudeThread))),
      ),
      resolveErrorMessage = { _, _ -> "error" },
    )

    assertThat(result.errorMessage).isNull()
    assertThat(result.hasUnknownThreadCount).isFalse()
    assertThat(result.providerLoadStates)
      .containsEntry(AgentSessionProvider.CODEX, AgentSessionProviderLoadState.LOADED)
      .containsEntry(AgentSessionProvider.CLAUDE, AgentSessionProviderLoadState.LOADED)
    assertThat(result.threads.map { it.id }).containsExactly("claude-1", "codex-1")
    assertThat(result.providerWarnings).isEmpty()
  }

  @Test
  fun recordsProviderLoadStatesForSuccessesAndFailures() {
    val codexFailure = IllegalStateException("codex failed")

    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.CODEX, Result.failure(codexFailure)),
        AgentSessionSourceLoadResult(AgentSessionProvider.CLAUDE, Result.success(emptyList())),
      ),
      resolveErrorMessage = { provider, _ -> "${provider.value} unavailable" },
    )

    assertThat(result.providerLoadStates)
      .containsEntry(AgentSessionProvider.CODEX, AgentSessionProviderLoadState.FAILED)
      .containsEntry(AgentSessionProvider.CLAUDE, AgentSessionProviderLoadState.LOADED)
  }

  @Test
  fun mergesEqualUpdatedTimesDeterministically() {
    val codexThread = AgentSessionThread(
      id = "codex-1",
      title = "Codex Session",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.CODEX,
    )
    val claudeThread = AgentSessionThread(
      id = "claude-1",
      title = "Claude Session",
      updatedAt = 100,
      archived = false,
      provider = AgentSessionProvider.CLAUDE,
    )

    fun merge(sourceResults: List<AgentSessionSourceLoadResult>): List<String> {
      return mergeAgentSessionSourceLoadResults(
        sourceResults = sourceResults,
        resolveErrorMessage = { _, _ -> "error" },
      ).threads.map { it.id }
    }

    val codexThenClaude = merge(
      listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.CODEX, Result.success(listOf(codexThread))),
        AgentSessionSourceLoadResult(AgentSessionProvider.CLAUDE, Result.success(listOf(claudeThread))),
      )
    )
    val claudeThenCodex = merge(
      listOf(
        AgentSessionSourceLoadResult(AgentSessionProvider.CLAUDE, Result.success(listOf(claudeThread))),
        AgentSessionSourceLoadResult(AgentSessionProvider.CODEX, Result.success(listOf(codexThread))),
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
      provider = AgentSessionProvider.CODEX,
    )

    val result = mergeAgentSessionSourceLoadResults(
      sourceResults = listOf(
        AgentSessionSourceLoadResult(
          provider = AgentSessionProvider.CODEX,
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
          provider = AgentSessionProvider.CODEX,
          result = Result.failure(IllegalStateException("codex failed")),
          hasUnknownTotal = true,
        ),
      ),
      resolveErrorMessage = { _, _ -> "error" },
    )

    assertThat(result.providerLoadStates).containsEntry(AgentSessionProvider.CODEX, AgentSessionProviderLoadState.FAILED)
    assertThat(result.hasUnknownThreadCount).isFalse()
  }

  @Test
  fun mergesProviderLoadMetadataByReplacingUnknownCountOnlyForUpdatedProviders() {
    val metadata = mergeProviderLoadMetadata(
      currentProviderLoadStates = mapOf(
        AgentSessionProvider.CODEX to AgentSessionProviderLoadState.LOADED,
        AgentSessionProvider.CLAUDE to AgentSessionProviderLoadState.LOADED,
      ),
      currentProvidersWithUnknownThreadCount = setOf(AgentSessionProvider.CODEX, AgentSessionProvider.CLAUDE),
      providerLoadStateUpdates = mapOf(AgentSessionProvider.CODEX to AgentSessionProviderLoadState.LOADED),
      updatedProvidersWithUnknownThreadCount = emptySet(),
    )

    assertThat(metadata.providerLoadStates)
      .containsEntry(AgentSessionProvider.CODEX, AgentSessionProviderLoadState.LOADED)
      .containsEntry(AgentSessionProvider.CLAUDE, AgentSessionProviderLoadState.LOADED)
    assertThat(metadata.providersWithUnknownThreadCount).containsExactly(AgentSessionProvider.CLAUDE)
  }

  @Test
  fun mergeProviderLoadMetadataClearsUnknownCountForLoadingProvider() {
    val metadata = mergeProviderLoadMetadata(
      currentProviderLoadStates = mapOf(
        AgentSessionProvider.CODEX to AgentSessionProviderLoadState.LOADED,
        AgentSessionProvider.CLAUDE to AgentSessionProviderLoadState.LOADED,
      ),
      currentProvidersWithUnknownThreadCount = setOf(AgentSessionProvider.CODEX, AgentSessionProvider.CLAUDE),
      providerLoadStateUpdates = mapOf(AgentSessionProvider.CODEX to AgentSessionProviderLoadState.LOADING),
      updatedProvidersWithUnknownThreadCount = emptySet(),
    )

    assertThat(metadata.providerLoadStates)
      .containsEntry(AgentSessionProvider.CODEX, AgentSessionProviderLoadState.LOADING)
      .containsEntry(AgentSessionProvider.CLAUDE, AgentSessionProviderLoadState.LOADED)
    assertThat(metadata.providersWithUnknownThreadCount).containsExactly(AgentSessionProvider.CLAUDE)
  }
}
