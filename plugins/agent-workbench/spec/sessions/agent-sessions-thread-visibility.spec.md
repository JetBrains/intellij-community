---
name: Agent Threads Visibility and More Row
description: Runtime visibility and More-row behavior for Agent Threads.
targets:
  - ../../sessions/src/state/AgentSessionsVisibilityDefaults.kt
  - ../../sessions/src/state/AgentSessionsStateStore.kt
  - ../../sessions-toolwindow/src/**/*.kt
  - ../../sessions/testSrc/AgentSessionRefreshServiceIntegrationTest.kt
  - ../../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt
  - ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeRenderingTest.kt
  - ../../sessions-toolwindow/testSrc/AgentSessionsTreeSnapshotTest.kt
---

# Agent Threads Visibility and More Row

Status: Draft
Date: 2026-05-09

## Summary
Agent Threads keeps thread/project visibility in runtime state so large project lists and thread lists stay bounded until the user asks for more or search reveals hidden matches.

## Requirements
- Default project visibility includes all open projects and up to three closed recent projects; additional closed projects appear behind `More`.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- Per-path thread visibility starts from the configured default and grows in fixed +3 increments through shared visibility primitives.
  [@test] ../../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

- `More` rows must preserve exact versus unknown remaining-count semantics and must not appear when error/warning precedence suppresses helper rows.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- `ensureThreadVisible(path, provider, threadId)` expands visibility until the target loaded thread is visible without reordering provider-sorted thread rows.
  [@test] ../../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt

- Speed search may reveal hidden matching projects/threads by using the same visibility primitives, then refreshes active selection once rows materialize.
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsTreeSnapshotTest.kt

- Runtime visibility state is not persisted as thread content; collapsed project paths and warm session rows remain in their separate state services.
  [@test] ../../sessions/testSrc/AgentSessionTreeUiStateServiceTest.kt
  [@test] ../../sessions/testSrc/AgentSessionWarmStateServiceTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsSwingTreeRenderingTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.toolwindow.tests --test com.intellij.agent.workbench.sessions.toolwindow.AgentSessionsTreeSnapshotTest`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionRefreshOnDemandIntegrationTest`

## References
- `agent-sessions.spec.md`
- `agent-sessions-tree.spec.md`
- `agent-sessions-refresh.spec.md`
