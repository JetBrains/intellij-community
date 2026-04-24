package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.state.AgentSessionTreeUiStateService
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
  fun pathNormalizationIsAppliedToCollapsedState() {
    val uiState = AgentSessionTreeUiStateService()

    uiState.setProjectCollapsed("/work/project-a/", collapsed = true)
    assertThat(uiState.isProjectCollapsed("/work/project-a")).isTrue()
  }

  @Test
  fun treeUiStateRoundTripStoresOnlyCollapsedProjects() {
    val original = AgentSessionTreeUiStateService()
    val path = "/work/project-a/"
    original.setProjectCollapsed(path, collapsed = true)

    val reloaded = AgentSessionTreeUiStateService()
    reloaded.loadState(original.state)

    assertThat(reloaded.isProjectCollapsed("/work/project-a")).isTrue()
    assertThat(reloaded.state.collapsedProjectPaths).containsExactly("/work/project-a")
    assertThat(reloaded.state::class.java.declaredFields.map { it.name })
      .doesNotContain("visibleThreadCountByProject")
  }
}
