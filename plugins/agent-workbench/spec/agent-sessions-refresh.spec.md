---
name: Agent Threads Refresh and Loading
description: Provider aggregation, refresh scheduling, warm snapshot, and source-update requirements for Agent Threads.
targets:
  - ../sessions-core/src/providers/*.kt
  - ../sessions/src/service/*.kt
  - ../sessions/src/state/AgentSessionWarmStateService.kt
  - ../codex/sessions/src/**/*.kt
  - ../claude/sessions/src/**/*.kt
  - ../junie/sessions/src/**/*.kt
  - ../filewatch/src/**/*.kt
  - ../sessions/testSrc/*.kt
  - ../codex/sessions/testSrc/**/*.kt
  - ../claude/sessions/testSrc/**/*.kt
  - ../filewatch/testSrc/**/*.kt
---

# Agent Threads Refresh and Loading

Status: Draft
Date: 2026-05-09

## Summary
Session refresh is event-driven and provider-agnostic. It merges provider results per normalized path, keeps useful partial data, and uses warm snapshots only as startup/bootstrap state.

## Requirements
- Path loads must merge provider rows by normalized project/worktree path and sort threads by `updatedAt` descending.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt

- Partial provider failures must keep successful provider rows visible and surface provider-local warning rows. If every provider fails for a path, the path shows one blocking error instead.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt

- Before invoking a session source for a path, the load pipeline must consult `AgentSessionProviderDescriptor.isCliAvailable`. When the descriptor reports the CLI as missing, the source is short-circuited with `AgentProviderCliMissingException` so the aggregation produces a provider warning resolved through `cliMissingMessageKey` (e.g. "Junie CLI not found. …") — even for filesystem-backed sources (Claude/Junie) that would otherwise return an empty list silently.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Unknown provider totals must propagate to tree state so `More` rows can distinguish exact and unknown counts.
  [@test] ../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../sessions-toolwindow/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- Warm bootstrap must seed currently open project/worktree paths immediately, prune closed-path entries, and avoid overwriting usable warm snapshots with blocking refresh errors.
  [@test] ../sessions/testSrc/AgentSessionRefreshServiceIntegrationTest.kt
  [@test] ../sessions/testSrc/AgentSessionWarmStateServiceTest.kt

- Explicit refresh and on-demand loads must scope loading indicators to affected paths and deduplicate concurrent requests for the same normalized path.
  [@test] ../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt
  [@test] ../sessions/testSrc/AgentSessionRefreshConcurrencyIntegrationTest.kt

- Source update observation must be event-driven. Observer failures must restart collection so one provider signal failure cannot permanently stop refresh delivery.
  [@test] ../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Thread-targeted source updates must refresh only resolvable loaded/open paths and must not widen unresolved thread-only events into a full refresh.
  [@test] ../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Provider refresh outcomes may be complete path snapshots or partial thread updates/removals. Partial outcomes must not remove unrelated provider rows or shared thread presentation.
  [@test] ../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Provider refresh publishes shared thread presentation keyed by normalized path and canonical thread identity so Agent Threads and open editor tabs show the same title/activity.
  [@test] ../sessions/testSrc/AgentSessionThreadPresentationTest.kt
  [@test] ../chat/testSrc/AgentChatEditorServiceTest.kt

- File-backed provider watchers must recover after watch-loop failures while the owning watcher remains active.
  [@test] ../filewatch/testSrc/AgentWorkbenchDirectoryWatcherTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test "com.intellij.agent.workbench.sessions.AgentSessionRefresh*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest`
- `./tests.cmd --module intellij.agent.workbench.filewatch.tests --test com.intellij.agent.workbench.filewatch.AgentWorkbenchDirectoryWatcherTest`

## References
- `spec/agent-sessions.spec.md`
- `spec/agent-sessions-tree.spec.md`
- `spec/agent-sessions-thread-visibility.spec.md`
- `spec/agent-chat-editor.spec.md`
