package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AgentSessionsTreeUiStateServiceTest {
  @Test
  fun projectCollapseStateRoundTrip() {
    val uiState = AgentSessionsTreeUiStateService()

    assertThat(uiState.setProjectCollapsed("/work/project-a", collapsed = true)).isTrue()
    assertThat(uiState.isProjectCollapsed("/work/project-a")).isTrue()
    assertThat(uiState.setProjectCollapsed("/work/project-a", collapsed = true)).isFalse()

    assertThat(uiState.setProjectCollapsed("/work/project-a", collapsed = false)).isTrue()
    assertThat(uiState.isProjectCollapsed("/work/project-a")).isFalse()
  }

  @Test
  fun visibleThreadCountStateRoundTrip() {
    val uiState = AgentSessionsTreeUiStateService()

    assertThat(uiState.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT)

    assertThat(uiState.incrementVisibleThreadCount("/work/project-a", delta = 3)).isTrue()
    assertThat(uiState.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + 3)

    assertThat(uiState.resetVisibleThreadCount("/work/project-a")).isTrue()
    assertThat(uiState.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT)
    assertThat(uiState.resetVisibleThreadCount("/work/project-a")).isFalse()
  }

  @Test
  fun pathNormalizationIsAppliedToStoredState() {
    val uiState = AgentSessionsTreeUiStateService()

    uiState.setProjectCollapsed("/work/project-a/", collapsed = true)
    assertThat(uiState.isProjectCollapsed("/work/project-a")).isTrue()

    uiState.incrementVisibleThreadCount("/work/project-b/", delta = 3)
    assertThat(uiState.getVisibleThreadCount("/work/project-b")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + 3)
  }

  @Test
  fun openProjectThreadPreviewCacheRoundTrip() {
    val uiState = AgentSessionsTreeUiStateService()
    val threads = listOf(
      AgentSessionThreadPreview(id = "thread-1", title = "Thread 1", updatedAt = 5L),
      AgentSessionThreadPreview(id = "thread-2", title = "Thread 2", updatedAt = 10L),
    )

    assertThat(uiState.setOpenProjectThreadPreviews("/work/project-a/", threads)).isTrue()

    val cached = uiState.getOpenProjectThreadPreviews("/work/project-a")
    assertThat(cached?.map { it.id }).isEqualTo(listOf("thread-2", "thread-1"))

    assertThat(uiState.retainOpenProjectThreadPreviews(setOf("/work/project-b"))).isTrue()
    assertThat(uiState.getOpenProjectThreadPreviews("/work/project-a")).isNull()
  }

  @Test
  fun openProjectThreadPreviewCachePreservesProvider() {
    val uiState = AgentSessionsTreeUiStateService()
    val threads = listOf(
      AgentSessionThreadPreview(
        id = "claude-thread",
        title = "Claude Thread",
        updatedAt = 5L,
        provider = AgentSessionProvider.CLAUDE,
      )
    )

    assertThat(uiState.setOpenProjectThreadPreviews("/work/project-a", threads)).isTrue()
    assertThat(uiState.getOpenProjectThreadPreviews("/work/project-a")?.single()?.provider)
      .isEqualTo(AgentSessionProvider.CLAUDE)
  }

  @Test
  fun openProjectThreadPreviewCacheNormalizesBlankTitleToFallback() {
    val uiState = AgentSessionsTreeUiStateService()
    val threads = listOf(
      AgentSessionThreadPreview(
        id = "blank-title-thread",
        title = "   \n ",
        updatedAt = 5L,
        provider = AgentSessionProvider.CODEX,
      )
    )

    assertThat(uiState.setOpenProjectThreadPreviews("/work/project-a", threads)).isTrue()
    assertThat(uiState.getOpenProjectThreadPreviews("/work/project-a")?.single()?.title)
      .isEqualTo("Thread blank-ti")
  }

  @Test
  fun treeStateFieldsSurviveServiceStateRoundTrip() {
    val original = AgentSessionsTreeUiStateService()
    val path = "/work/project-a/"
    original.setProjectCollapsed(path, collapsed = true)
    original.incrementVisibleThreadCount(path, delta = 4)
    original.setOpenProjectThreadPreviews(
      path,
      listOf(
        AgentSessionThreadPreview(
          id = "claude-thread",
          title = "Claude Thread",
          updatedAt = 20,
          provider = AgentSessionProvider.CLAUDE,
        ),
        AgentSessionThreadPreview(
          id = "codex-thread",
          title = "Codex Thread",
          updatedAt = 10,
          provider = AgentSessionProvider.CODEX,
        ),
      )
    )

    val reloaded = AgentSessionsTreeUiStateService()
    reloaded.loadState(original.state)

    assertThat(reloaded.isProjectCollapsed("/work/project-a")).isTrue()
    assertThat(reloaded.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + 4)
    val previews = reloaded.getOpenProjectThreadPreviews("/work/project-a").orEmpty()
    assertThat(previews.map { it.id }).isEqualTo(listOf("claude-thread", "codex-thread"))
    assertThat(previews.map { it.provider }).isEqualTo(listOf(AgentSessionProvider.CLAUDE, AgentSessionProvider.CODEX))
  }

  @Test
  fun lastUsedProviderDefaultsToNull() {
    val uiState = AgentSessionsTreeUiStateService()
    assertThat(uiState.getLastUsedProvider()).isNull()
  }

  @Test
  fun claudeQuotaHintDefaultsToDisabledAndUnacknowledged() {
    val uiState = AgentSessionsTreeUiStateService()

    assertThat(uiState.state.claudeQuotaHintEligible).isFalse()
    assertThat(uiState.state.claudeQuotaHintAcknowledged).isFalse()
    assertThat(uiState.claudeQuotaHintEligibleFlow.value).isFalse()
    assertThat(uiState.claudeQuotaHintAcknowledgedFlow.value).isFalse()
  }

  @Test
  fun claudeQuotaHintStateRoundTrip() {
    val uiState = AgentSessionsTreeUiStateService()

    uiState.markClaudeQuotaHintEligible()
    assertThat(uiState.state.claudeQuotaHintEligible).isTrue()
    assertThat(uiState.claudeQuotaHintEligibleFlow.value).isTrue()

    uiState.acknowledgeClaudeQuotaHint()
    assertThat(uiState.state.claudeQuotaHintAcknowledged).isTrue()
    assertThat(uiState.claudeQuotaHintAcknowledgedFlow.value).isTrue()
  }

  @Test
  fun setAndGetLastUsedProvider() {
    val uiState = AgentSessionsTreeUiStateService()

    uiState.setLastUsedProvider(AgentSessionProvider.CLAUDE)
    assertThat(uiState.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CLAUDE)

    uiState.setLastUsedProvider(AgentSessionProvider.CODEX)
    assertThat(uiState.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CODEX)
  }

  @Test
  fun lastUsedProviderFlowUpdatesOnSet() {
    val uiState = AgentSessionsTreeUiStateService()

    assertThat(uiState.lastUsedProviderFlow.value).isNull()

    uiState.setLastUsedProvider(AgentSessionProvider.CLAUDE)
    assertThat(uiState.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CLAUDE)

    uiState.setLastUsedProvider(AgentSessionProvider.CODEX)
    assertThat(uiState.lastUsedProviderFlow.value).isEqualTo(AgentSessionProvider.CODEX)
  }

  @Test
  fun lastUsedProviderSurvivesRoundTrip() {
    val uiState = AgentSessionsTreeUiStateService()

    uiState.setLastUsedProvider(AgentSessionProvider.CLAUDE)
    val storedName = uiState.state.lastUsedProvider
    assertThat(storedName).isEqualTo("claude")

    // Verify the provider can be reconstructed from stored name
    assertThat(uiState.getLastUsedProvider()).isEqualTo(AgentSessionProvider.CLAUDE)
  }
}
