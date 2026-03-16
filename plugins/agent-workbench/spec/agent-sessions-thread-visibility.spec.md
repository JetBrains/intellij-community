---
name: Agent Threads Visibility and More Row
description: Deterministic rules for thread row visibility and More-row rendering in Agent Threads.
targets:
  - ../sessions/src/AgentSessionModels.kt
  - ../sessions/src/SessionTree.kt
  - ../sessions/src/SessionTreeRows.kt
  - ../sessions/resources/messages/AgentSessionsBundle.properties
  - ../sessions/testSrc/AgentSessionsToolWindowTest.kt
  - ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
  - ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt
---

# Agent Threads Visibility and More Row

Status: Draft
Date: 2026-02-16

## Summary
Define one consistent visibility model for project/worktree thread rows so empty state, warning/error rows, and `More` rows never conflict.

## Goals
- Keep row visibility deterministic after refresh and on-demand loads.
- Support both exact and unknown hidden-thread counts.
- Avoid contradictory rows (`No recent activity yet.` together with warnings/errors/More).

## Non-goals
- Provider-side pagination strategy.
- Changing default visible step size.

## Requirements
- Initial visible thread count per path is `DEFAULT_VISIBLE_THREAD_COUNT` (3).
- Visible thread count lookup order per normalized path is:
  - in-memory runtime `visibleThreadCounts` entry,
  - persisted tree UI state visible count,
  - default value (`DEFAULT_VISIBLE_THREAD_COUNT`).
- For project rows, show `More` row only when `project.threads.size > visibleCount`.
- For worktree rows, show `More` row only when `worktree.threads.size > visibleCount`.
- If `hasUnknownThreadCount=true` for the corresponding node, `More` row must render without count (`toolwindow.action.more`).
- If `hasUnknownThreadCount=false`, `More` row must render with count (`toolwindow.action.more.count`) using `threads.size - visibleCount`.
- `No recent activity yet.` row is shown only when:
  - `hasLoaded=true`,
  - no project-level threads are visible,
  - no visible worktree rows with content/loading/error/warnings,
  - no project error,
  - no provider warnings.
- Project/worktree error rows take precedence over provider warning and empty rows.
- Clicking a `More` row must increase visible count for that path by +3.
- Clicking a `More` row must persist that +3 increment to tree UI state for the same normalized path.
- `ensureThreadVisible(path, provider, threadId)` must raise visible count in +3 steps until the target thread is visible and persist the increment.
- Refresh bootstrap must restore persisted visible counts greater than default for known project/worktree paths.
- Persisted visibility keys must use normalized paths so `/path` and `/path/` resolve to one entry.
- Tree-side `More` click handling must not issue backend calls directly; it only updates visibility state.

[@test] ../sessions/testSrc/AgentSessionsToolWindowTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceRefreshIntegrationTest.kt
[@test] ../sessions/testSrc/AgentSessionsServiceOnDemandIntegrationTest.kt

## User Experience
- Exact count case: `More (N)`.
- Unknown count case: `More…`.
- Empty row is muted helper text and mutually exclusive with `More` for the same node.
- Non-default per-path thread visibility must persist across refresh/reopen for the same normalized path.

## Data & Backend
- Unknown-count state is produced by the service aggregation layer (`hasUnknownThreadCount`), not by tree rendering logic.
- Tree rendering consumes already-sorted thread lists and does no reordering.

## Error Handling
- If load fails, error/warning presentation follows service state and visibility rules above.
- Visibility updates from `More` must be safe when state changes between clicks (no crashes on missing nodes).

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsToolWindowTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.sessions.AgentSessionsServiceRefreshIntegrationTest'`

## Open Questions / Risks
- If future providers expose precise totals independently of loaded rows, UI may need richer count semantics than current hidden-row based count.

## References
- `spec/agent-sessions.spec.md`
