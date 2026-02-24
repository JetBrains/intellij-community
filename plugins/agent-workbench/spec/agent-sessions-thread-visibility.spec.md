---
name: Agent Threads Visibility and More Row
description: Deterministic rendering and persisted visibility rules for thread rows and More-row behavior in Swing Agent Threads tree.
targets:
  - ../sessions/src/AgentSessionModels.kt
  - ../sessions/src/SessionTree.kt
  - ../sessions/src/AgentSessionsToolWindow.kt
  - ../sessions/src/AgentSessionsStateStore.kt
  - ../sessions/src/AgentSessionsTreeUiStateService.kt
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../sessions/testSrc/AgentSessionsSwingTreeCellRendererTest.kt
  - ../sessions/testSrc/AgentSessionsSwingTreeRenderingTest.kt
  - ../sessions/testSrc/AgentSessionsSwingTreeInteractionTest.kt
  - ../sessions/testSrc/AgentSessionsTreeSnapshotTest.kt
  - ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
---

# Agent Threads Visibility and More Row

Status: Draft
Date: 2026-02-24

## Summary
Define deterministic visibility rules for project/worktree thread rows so empty state, warning/error rows, and `More` rows never conflict. Shared visibility primitive semantics are canonical in `spec/agent-core-contracts.spec.md`; this spec owns rendering and precedence behavior.

## Goals
- Keep row visibility deterministic after refresh and on-demand loads.
- Support exact-count and unknown-count hidden-thread states.
- Prevent contradictory rows for the same node.

## Non-goals
- Provider-side pagination strategy.
- Aggregation/source-loading behavior.
- Command mapping or editor-tab contracts.

## Requirements
- Initial visible-thread count per normalized path must be `DEFAULT_VISIBLE_THREAD_COUNT` (`3`).
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt

- Visible-thread count lookup order per normalized path must be:
  - in-memory runtime entry,
  - persisted tree UI state entry,
  - default value.
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt

- For project rows, render `More` only when `project.threads.size > visibleCount`.
  [@test] ../sessions/testSrc/AgentSessionsTreeSnapshotTest.kt

- For worktree rows, render `More` only when `worktree.threads.size > visibleCount`.
  [@test] ../sessions/testSrc/AgentSessionsTreeSnapshotTest.kt

- When `hasUnknownThreadCount=true` for the node, `More` must render without explicit count (`toolwindow.action.more`).
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- When `hasUnknownThreadCount=false`, `More` must render with explicit hidden count (`toolwindow.action.more.count`) using `threads.size - visibleCount`.
  [@test] ../sessions/testSrc/AgentSessionsTreeSnapshotTest.kt

- `No recent activity yet.` must render only when:
  - `hasLoaded=true`,
  - no visible project threads,
  - no visible worktree rows with content/loading/error/warnings,
  - no project-level error,
  - no provider warnings.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- Project/worktree error rows must take precedence over warning and empty rows.
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- `showMoreThreads(path)` and `ensureThreadVisible(path, provider, threadId)` must follow runtime visibility increment contract defined in `spec/agent-core-contracts.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
  [@test] ../sessions/testSrc/AgentSessionsSwingTreeInteractionTest.kt

- Refresh bootstrap must keep runtime visible-thread counts above default for known project/worktree paths and must ignore persisted UI-state visible-thread counts.
  [@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt

- Persisted visibility key normalization must follow `spec/agent-core-contracts.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionsTreeUiStateServiceTest.kt

- Tree-side `More` click handling must not trigger backend loads directly; it only updates local visibility state.
  [@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt

## User Experience
- Exact-count case renders `More (N)`.
- Unknown-count case renders `More…`.
- `More` rows are rendered as muted helper rows without a leading icon.
- Empty helper row is mutually exclusive with `More`, warning, and error rows for the same node.
- Non-default visibility is runtime-only; refresh in the same runtime keeps it for known paths, but reopen starts from default.

## Data & Backend
- Unknown-count state is produced by service aggregation layer (`hasUnknownThreadCount`), not tree rendering.
- Tree rendering consumes pre-sorted thread lists and performs no additional ordering.

## Error Handling
- Visibility updates must remain safe when underlying state changes between interactions.
- Error/warning display must follow precedence rules without conflicting helper rows.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsSwingTreeRenderingTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsTreeSnapshotTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsServiceRefreshIntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsServiceOnDemandIntegrationTest'`

## Open Questions / Risks
- If providers begin exposing exact remote totals independent of loaded rows, count semantics may require richer model than current hidden-row math.

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-sessions.spec.md`
