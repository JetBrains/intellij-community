package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadPreview
import com.intellij.agent.workbench.sessions.state.AgentSessionTreeUiStateService
import com.intellij.agent.workbench.sessions.state.DEFAULT_VISIBLE_THREAD_COUNT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionTreeUiStateServiceTest {
  @Test
  fun projectCollapseStateRoundTrip() {
    val uiState = AgentSessionTreeUiStateService()

    assertThat(uiState.setProjectCollapsed("/work/project-a", collapsed = true)).isTrue()
    assertThat(uiState.isProjectCollapsed("/work/project-a")).isTrue()
    assertThat(uiState.setProjectCollapsed("/work/project-a", collapsed = true)).isFalse()

    assertThat(uiState.setProjectCollapsed("/work/project-a", collapsed = false)).isTrue()
    assertThat(uiState.isProjectCollapsed("/work/project-a")).isFalse()
  }

  @Test
  fun visibleThreadCountStateRoundTrip() {
    val uiState = AgentSessionTreeUiStateService()

    assertThat(uiState.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT)

    assertThat(uiState.incrementVisibleThreadCount("/work/project-a", delta = 3)).isTrue()
    assertThat(uiState.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + 3)

    assertThat(uiState.resetVisibleThreadCount("/work/project-a")).isTrue()
    assertThat(uiState.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT)
    assertThat(uiState.resetVisibleThreadCount("/work/project-a")).isFalse()
  }

  @Test
  fun pathNormalizationIsAppliedToStoredState() {
    val uiState = AgentSessionTreeUiStateService()

    uiState.setProjectCollapsed("/work/project-a/", collapsed = true)
    assertThat(uiState.isProjectCollapsed("/work/project-a")).isTrue()

    uiState.incrementVisibleThreadCount("/work/project-b/", delta = 3)
    assertThat(uiState.getVisibleThreadCount("/work/project-b")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + 3)
  }

  @Test
  fun openProjectThreadPreviewCacheRoundTrip() {
    val uiState = AgentSessionTreeUiStateService()
    val threads = listOf(
      AgentSessionThreadPreview(id = "thread-1", title = "Thread 1", updatedAt = 5L, activity = AgentThreadActivity.READY),
      AgentSessionThreadPreview(id = "thread-2", title = "Thread 2", updatedAt = 10L, activity = AgentThreadActivity.UNREAD),
    )

    assertThat(uiState.setOpenProjectThreadPreviews("/work/project-a/", threads)).isTrue()

    val cached = uiState.getOpenProjectThreadPreviews("/work/project-a")
    assertThat(cached?.map { it.id }).isEqualTo(listOf("thread-2", "thread-1"))
    assertThat(cached?.map { it.activity }).isEqualTo(listOf(AgentThreadActivity.UNREAD, AgentThreadActivity.READY))

    assertThat(uiState.retainOpenProjectThreadPreviews(setOf("/work/project-b"))).isTrue()
    assertThat(uiState.getOpenProjectThreadPreviews("/work/project-a")).isNull()
  }

  @Test
  fun openProjectThreadPreviewCachePreservesProvider() {
    val uiState = AgentSessionTreeUiStateService()
    val threads = listOf(
      AgentSessionThreadPreview(
        id = "claude-thread",
        title = "Claude Thread",
        updatedAt = 5L,
        activity = AgentThreadActivity.UNREAD,
        provider = AgentSessionProvider.CLAUDE,
      )
    )

    assertThat(uiState.setOpenProjectThreadPreviews("/work/project-a", threads)).isTrue()
    assertThat(uiState.getOpenProjectThreadPreviews("/work/project-a")?.single()?.provider)
      .isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(uiState.getOpenProjectThreadPreviews("/work/project-a")?.single()?.activity)
      .isEqualTo(AgentThreadActivity.UNREAD)
  }

  @Test
  fun openProjectThreadPreviewCacheNormalizesBlankTitleToFallback() {
    val uiState = AgentSessionTreeUiStateService()
    val threads = listOf(
      AgentSessionThreadPreview(
        id = "blank-title-thread",
        title = "   \n ",
        updatedAt = 5L,
        activity = AgentThreadActivity.READY,
        provider = AgentSessionProvider.CODEX,
      )
    )

    assertThat(uiState.setOpenProjectThreadPreviews("/work/project-a", threads)).isTrue()
    assertThat(uiState.getOpenProjectThreadPreviews("/work/project-a")?.single()?.title)
      .isEqualTo("Thread blank-ti")
  }

  @Test
  fun treeStateFieldsSurviveServiceStateRoundTrip() {
    val original = AgentSessionTreeUiStateService()
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
          activity = AgentThreadActivity.UNREAD,
          provider = AgentSessionProvider.CLAUDE,
        ),
        AgentSessionThreadPreview(
          id = "codex-thread",
          title = "Codex Thread",
          updatedAt = 10,
          activity = AgentThreadActivity.READY,
          provider = AgentSessionProvider.CODEX,
        ),
      )
    )

    val reloaded = AgentSessionTreeUiStateService()
    reloaded.loadState(original.state)

    assertThat(reloaded.isProjectCollapsed("/work/project-a")).isTrue()
    assertThat(reloaded.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + 4)
    val previews = reloaded.getOpenProjectThreadPreviews("/work/project-a").orEmpty()
    assertThat(previews.map { it.id }).isEqualTo(listOf("claude-thread", "codex-thread"))
    assertThat(previews.map { it.provider }).isEqualTo(listOf(AgentSessionProvider.CLAUDE, AgentSessionProvider.CODEX))
    assertThat(previews.map { it.activity }).isEqualTo(listOf(AgentThreadActivity.UNREAD, AgentThreadActivity.READY))
  }
}
