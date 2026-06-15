---
name: Codex Fast Rollout Path
description: Requirements for app-server rollout-path persistence, lazy Codex rollout reads, and frozen Codex cost reuse.
targets:
  - ../../codex/common/src/*.kt
  - ../../codex/sessions/src/**/*.kt
  - ../../codex/sessions/testSrc/**/*.kt
  - ../../sessions/src/service/*.kt
  - ../../sessions/testSrc/*.kt
---

# Codex Fast Rollout Path

Status: Approved
Date: 2026-05-28

## Summary

Codex thread listing must stay app-server backed and fast on startup. Rollout files remain valuable for fallback activity and cost estimation, but Agent Workbench must stop depending on a cold global rollout scan in the normal thread-list path.

The app-server already exposes each thread's rollout file path. Agent Workbench should persist that lightweight mapping, use it to read only the exact rollout files needed for a visible thread or a scoped repair pass, and freeze any computed Codex cost until the corresponding thread updates.

## Goals

- Keep Codex first paint fast by removing rollout global scan from the normal startup listing path.
- Preserve enough persisted rollout metadata to resolve cost and rollout-backed activity without rediscovering every file from scratch.
- Reuse once-computed Codex cost until the thread's `updatedAt` changes, including across IDE restarts.
- Support active and archived Codex thread cost with the same exact-path rollout loading path.

## Non-goals

- A second persisted Codex thread store that duplicates the full Agent Threads state.
- Continuous repricing of old Codex sessions when the OpenRouter snapshot changes.
- Depending on a real `codex` binary in normal CI coverage.
- A mandatory full `.codex/sessions` rescan during startup.

## Requirements

- `CodexThread` must preserve the rollout file path returned by the Codex app-server.
  [@test] ../../sessions/testSrc/CodexAppServerClientTest.kt

- Agent Workbench must maintain a small persisted Codex thread path index keyed by thread id. Each entry must be able to retain the normalized cwd, rollout path, parent thread id when known, thread `updatedAt`, and the last computed frozen cost for that exact thread version.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceTest.kt

- The path index must be updated from app-server-backed Codex thread reads that already know the rollout path, including normal thread listing, archived thread listing, and thread-scoped refresh paths.
  [@test] ../../codex/sessions/testSrc/CodexAppServerSessionBackendTest.kt

- Normal Codex thread listing and multi-path prefetch must not require a cold rollout directory scan. Startup thread rows must be renderable from warm snapshot plus app-server data alone.
  [@test] ../../sessions/testSrc/AgentSessionRefreshServiceIntegrationTest.kt

- Rollout-backed Codex cost loading must prefer exact rollout paths from the persisted path index and must read only the rollout files required for the requested visible or archived threads.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt

- When a visible Codex parent thread has folded sub-agents, rollout-backed cost loading must aggregate usage from the parent rollout and the known sub-agent rollout files that belong to that row.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt

- Once Agent Workbench computes a Codex thread cost for a specific thread id and `updatedAt`, that frozen cost must be reused until `updatedAt` changes. The IDE must not reprice an unchanged Codex thread after an OpenRouter price snapshot refresh or IDE restart.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshServiceIntegrationTest.kt
  [@test] ../../sessions/testSrc/AgentArchivedSessionsServiceTest.kt

- Archived Codex threads must use the same frozen-cost and exact-rollout-path resolution path as active Codex threads.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceTest.kt

- If the exact rollout path is missing, unreadable, or stale, Agent Workbench may fall back to a broader rollout recovery path, but that recovery path must stay off the normal startup listing flow.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt

- Real-Codex integration tests may remain guarded for local development, but CI ownership for this feature must stay with deterministic unit/integration tests that use fake app-server payloads and sanitized rollout fixtures.
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRealAppServerIntegrationTest.kt
  [@test] ../../codex/sessions/testSrc/CodexSessionSourceRolloutIntegrationTest.kt

## User Experience

- Codex rows may appear initially without rollout-repaired activity or cost; background scoped repair may fill those details later.
- A previously computed Codex cost must remain stable for an unchanged archived or active thread even if the current price catalog differs from the one that produced the stored amount.

## Data & Backend

- The persisted Codex path index should remain lightweight and must not duplicate thread titles, activity, or full rollout contents.
- Frozen Codex cost should be stored in a provider-neutral shape compatible with `AgentSessionCost`.
- Exact-path rollout loading may reuse the existing rollout parser/index implementation, but it must be able to target a provided set of rollout paths without discovering unrelated files.

## Error Handling

- Missing or malformed persisted path-index entries degrade to targeted rollout recovery or unavailable cost; they must not block thread listing.
- A failed rollout read for one Codex thread must not invalidate frozen costs already stored for other threads.
- Recovery scans should log at a non-intrusive level and must not run on the EDT.

## Testing / Local Run

- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexSessionSourceTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexSessionSourceRolloutIntegrationTest`
- `./tests.cmd --module intellij.agent.workbench.codex.sessions.tests --test com.intellij.agent.workbench.codex.sessions.CodexAppServerSessionBackendTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionRefreshServiceIntegrationTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentArchivedSessionsServiceTest`

## References

- `agent-sessions-codex-rollout-source.spec.md`
- `agent-sessions-codex-rollout-hints.spec.md`
- `agent-sessions-refresh.spec.md`
- `agent-sessions-cost-and-jbcentral-quota.spec.md`
