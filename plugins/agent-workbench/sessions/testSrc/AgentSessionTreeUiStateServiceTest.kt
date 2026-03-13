package com.intellij.agent.workbench.sessions

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
  fun treeUiStateRoundTripExcludesSessionContent() {
    val original = AgentSessionTreeUiStateService()
    val path = "/work/project-a/"
    original.setProjectCollapsed(path, collapsed = true)
    original.incrementVisibleThreadCount(path, delta = 4)

    val reloaded = AgentSessionTreeUiStateService()
    reloaded.loadState(original.state)

    assertThat(reloaded.isProjectCollapsed("/work/project-a")).isTrue()
    assertThat(reloaded.getVisibleThreadCount("/work/project-a")).isEqualTo(DEFAULT_VISIBLE_THREAD_COUNT + 4)
    assertThat(reloaded.state::class.java.declaredFields.map { it.name })
      .doesNotContain("openProjectThreadPreviewsByProject")
  }
}
