package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSubAgent
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmPathSnapshot
import com.intellij.agent.workbench.sessions.state.AgentSessionWarmStateService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionWarmStateServiceTest {
  @Test
  fun warmSnapshotRoundTripPreservesProviderActivityAndSubAgents() {
    val warmState = AgentSessionWarmStateService()

    assertThat(
      warmState.setPathSnapshot(
        "/work/project-a/",
        AgentSessionWarmPathSnapshot(
          threads = listOf(
            thread(
              id = "claude-thread",
              title = "Claude Thread",
              updatedAt = 20,
              provider = AgentSessionProvider.CLAUDE,
              activity = AgentThreadActivity.UNREAD,
              subAgents = listOf(AgentSubAgent(id = "claude-sub-1", name = "Sub-agent 1")),
              cost = AgentSessionCost(
                amountUsd = BigDecimal("1.25"),
                kind = AgentSessionCostKind.EXACT,
                matchedModelId = "anthropic/claude-sonnet-4",
              ),
            ).copy(originBranch = "feature/x"),
            thread(
              id = "codex-thread",
              title = "Codex Thread",
              updatedAt = 10,
              provider = AgentSessionProvider.CODEX,
            ),
          ),
          providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE, AgentSessionProvider.CODEX),
          providersWithUnknownThreadCount = setOf(AgentSessionProvider.CLAUDE),
          updatedAt = 500,
        ),
      )
    ).isTrue()

    val snapshot = warmState.getPathSnapshot("/work/project-a")
    assertThat(snapshot?.hasUnknownThreadCount).isTrue()
    assertThat(snapshot?.threads?.map { it.id }).isEqualTo(listOf("claude-thread", "codex-thread"))
    assertThat(snapshot?.threads?.map { it.provider }).isEqualTo(listOf(AgentSessionProvider.CLAUDE, AgentSessionProvider.CODEX))
    assertThat(snapshot?.threads?.map { it.activity }).isEqualTo(listOf(AgentThreadActivity.UNREAD, AgentThreadActivity.READY))
    assertThat(snapshot?.threads?.first()?.subAgents?.map { it.id }).isEqualTo(listOf("claude-sub-1"))
    assertThat(snapshot?.threads?.first()?.originBranch).isEqualTo("feature/x")
    assertThat(snapshot?.threads?.first()?.cost).isEqualTo(
      AgentSessionCost(
        amountUsd = BigDecimal("1.25"),
        kind = AgentSessionCostKind.EXACT,
        matchedModelId = "anthropic/claude-sonnet-4",
      )
    )
  }

  @Test
  fun warmSnapshotNormalizationDropsPendingThreadsAndNormalizesBlankTitle() {
    val warmState = AgentSessionWarmStateService()

    warmState.setPathSnapshot(
      "/work/project-a",
      AgentSessionWarmPathSnapshot(
        threads = listOf(
          thread(id = "new-pending", title = "Pending", updatedAt = 100, provider = AgentSessionProvider.CODEX),
          thread(id = "blank-title-thread", title = "   \n ", updatedAt = 5, provider = AgentSessionProvider.CODEX),
        ),
        updatedAt = 200,
      ),
    )

    val snapshot = warmState.getPathSnapshot("/work/project-a")
    assertThat(snapshot?.threads?.map { it.id }).isEqualTo(listOf("blank-title-thread"))
    assertThat(snapshot?.threads?.single()?.title).isEqualTo("Thread blank-ti")
  }

  @Test
  fun warmSnapshotRetainPrunesClosedPaths() {
    val warmState = AgentSessionWarmStateService()
    warmState.setPathSnapshot(
      "/work/project-a",
      AgentSessionWarmPathSnapshot(
        threads = listOf(thread(id = "thread-a", updatedAt = 10, provider = AgentSessionProvider.CODEX)),
        updatedAt = 10,
      ),
    )
    warmState.setPathSnapshot(
      "/work/project-b",
      AgentSessionWarmPathSnapshot(
        threads = listOf(thread(id = "thread-b", updatedAt = 20, provider = AgentSessionProvider.CLAUDE)),
        updatedAt = 20,
      ),
    )

    assertThat(warmState.retainPathSnapshots(setOf("/work/project-b/"))).isTrue()
    assertThat(warmState.getPathSnapshot("/work/project-a")).isNull()
    assertThat(warmState.getPathSnapshot("/work/project-b")?.threads?.single()?.id).isEqualTo("thread-b")
  }

  @Test
  fun warmStateFieldsSurviveServiceStateRoundTrip() {
    val original = AgentSessionWarmStateService()
    original.setPathSnapshot(
      "/work/project-a/",
      AgentSessionWarmPathSnapshot(
        threads = listOf(
          thread(
            id = "claude-thread",
            updatedAt = 20,
            provider = AgentSessionProvider.CLAUDE,
            activity = AgentThreadActivity.UNREAD,
            cost = AgentSessionCost(
              amountUsd = BigDecimal("2.50"),
              kind = AgentSessionCostKind.ESTIMATED,
              matchedModelId = "openai/o3",
            ),
          ),
          thread(id = "codex-thread", updatedAt = 10, provider = AgentSessionProvider.CODEX),
        ),
        providerLoadStates = loadedProviderStates(AgentSessionProvider.CLAUDE, AgentSessionProvider.CODEX),
        providersWithUnknownThreadCount = setOf(AgentSessionProvider.CLAUDE),
        updatedAt = 200,
      ),
    )

    val reloaded = AgentSessionWarmStateService()
    reloaded.loadState(original.state)

    val snapshot = reloaded.getPathSnapshot("/work/project-a")
    assertThat(snapshot?.hasUnknownThreadCount).isTrue()
    assertThat(snapshot?.threads?.map { it.id }).isEqualTo(listOf("claude-thread", "codex-thread"))
    assertThat(snapshot?.threads?.map { it.provider }).isEqualTo(listOf(AgentSessionProvider.CLAUDE, AgentSessionProvider.CODEX))
    assertThat(snapshot?.threads?.map { it.activity }).isEqualTo(listOf(AgentThreadActivity.UNREAD, AgentThreadActivity.READY))
    assertThat(snapshot?.threads?.first()?.cost).isEqualTo(
      AgentSessionCost(
        amountUsd = BigDecimal("2.50"),
        kind = AgentSessionCostKind.ESTIMATED,
        matchedModelId = "openai/o3",
      )
    )
  }

  @Test
  fun warmStateRoundTripPreservesNonContributingSummaryActivity() {
    val original = AgentSessionWarmStateService()
    original.setPathSnapshot(
      "/work/project-a/",
      AgentSessionWarmPathSnapshot(
        threads = listOf(
          thread(
            id = "codex-sub-agent",
            updatedAt = 10,
            provider = AgentSessionProvider.CODEX,
            activity = AgentThreadActivity.UNREAD,
            summaryActivity = null,
          ),
        ),
        updatedAt = 200,
      ),
    )

    val reloaded = AgentSessionWarmStateService()
    reloaded.loadState(original.state)

    val thread = reloaded.getPathSnapshot("/work/project-a")?.threads?.single()
    assertThat(thread?.activity).isEqualTo(AgentThreadActivity.UNREAD)
    assertThat(thread?.summaryActivity).isNull()
  }
}
