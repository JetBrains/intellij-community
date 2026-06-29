---
name: Agent Threads Refresh and Loading
description: Provider aggregation, refresh scheduling, warm snapshot, and source-update requirements for Agent Threads.
targets:
  - ../../lib-agent/sessions-core/src/providers/*.kt
  - ../../sessions/src/service/*.kt
  - ../../sessions/src/state/AgentSessionWarmStateService.kt
  - ../../lib-agent/providers/codex/sessions/src/**/*.kt
  - ../../lib-agent/providers/claude/sessions/src/**/*.kt
  - ../../lib-agent/providers/junie/sessions/src/**/*.kt
  - ../../lib-agent/filewatch/src/**/*.kt
  - ../../sessions/testSrc/*.kt
  - ../../lib-agent/providers/codex/sessions/testSrc/**/*.kt
  - ../../lib-agent/providers/claude/sessions/testSrc/**/*.kt
  - ../../lib-agent/filewatch/testSrc/**/*.kt
---

# Agent Threads Refresh and Loading

Status: Draft
Date: 2026-06-28

## Summary
Session refresh is event-driven and provider-agnostic. It merges provider results per normalized path, keeps useful partial data, and uses warm snapshots only as startup/bootstrap state.

## Requirements
- Path loads must merge provider rows by normalized project/worktree path and sort threads by `updatedAt` descending.
  [@test] ../../sessions/testSrc/AgentSessionLoadAggregationTest.kt

- Partial provider failures must keep successful provider rows visible and surface provider-local warning rows. If every provider fails for a path, the path shows one blocking error instead.
  [@test] ../../sessions/testSrc/AgentSessionLoadAggregationTest.kt

- Before invoking a session source for a path, the load pipeline must consult `AgentSessionProviderDescriptor.isCliAvailable`. When the descriptor reports the CLI as missing, the source is skipped so the tree does not get per-path warning rows; the tool window banner surfaces the provider-level `cliMissingMessageKey` message instead — even for filesystem-backed sources (Claude/Junie) that would otherwise return an empty list silently.
  [@test] ../../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Unknown provider totals must propagate to tree state so `More` rows can distinguish exact and unknown counts.
  [@test] ../../sessions/testSrc/AgentSessionLoadAggregationTest.kt
  [@test] ../../sessions-toolwindow/testSrc/AgentSessionsSwingTreeRenderingTest.kt

- Warm bootstrap must seed currently open project/worktree paths immediately, prune closed-path entries, and avoid overwriting usable warm snapshots with blocking refresh errors.
  [@test] ../../sessions/testSrc/AgentSessionRefreshServiceIntegrationTest.kt
  [@test] ../../sessions/testSrc/AgentSessionWarmStateServiceTest.kt

- Explicit refresh and on-demand loads must scope loading indicators to affected paths and deduplicate concurrent requests for the same normalized path.
  [@test] ../../sessions/testSrc/AgentSessionRefreshOnDemandIntegrationTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshConcurrencyIntegrationTest.kt

- When a path has a separate project directory, refresh and on-demand loading must invoke provider sources with that directory while merging results, warm snapshots, loading state, and pending-tab rebind requests under the identity path. Refresh hints and thread-scoped source updates must remap provider source paths back to identity paths, and rebind targets must retain the project directory for resume launch planning.
  [@test] ../../sessions/testSrc/AgentSessionThreadLoadSupportTest.kt
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Source update observation must be event-driven. Observer failures must restart collection so one provider signal failure cannot permanently stop refresh delivery.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Source updates must schedule IDE VFS refresh only when the update carries provider evidence that project files may have changed, and must scope that refresh to resolved open/loaded project or worktree paths. Status-only activity or hint updates must still refresh provider session state without refreshing VFS. Evidence is raised on a newly completed mutating tool: for Codex, native `exec_command` / `apply_patch` function calls (including their `ExecCommandBegin/End` and `PatchApplyBegin/End` event-msg pairs); for Claude Code, completion of any of `Bash`, `Edit`, `MultiEdit`, `Write`, `NotebookEdit` — `Bash` is treated as mutating regardless of command content because the rollout cannot reliably classify shell intent. Provider/tool implementations that mutate project files through IDE or MCP APIs own their own refresh semantics and must not rely on session-source inference.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt
  [@test] ../../lib-agent/providers/codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt
  [@test] ../../lib-agent/providers/claude/sessions/testSrc/ClaudeStoreSessionBackendTest.kt

- Thread-targeted source updates must refresh only resolvable loaded/open paths and must not widen unresolved thread-only events into a full refresh.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- Refresh and rebind flows may inspect open pending Agent Chat tabs for materialization or rebind, but synthetic pending rows must not be persisted to `AgentSessionsStateStore`.

- Provider refresh outcomes may be complete path snapshots or partial thread updates/removals. Partial outcomes must not remove unrelated provider rows or shared thread presentation.
  [@test] ../../sessions/testSrc/AgentSessionRefreshCoordinatorTest.kt

- File-backed provider index refresh must plan invalidation, apply parsed-cache updates, and materialize backend threads atomically so
  concurrent project loads cannot observe or prune each other's partial rescan state.
  [@test] ../../lib-agent/providers/claude/sessions/testSrc/ClaudeStoreSessionBackendTest.kt

- Provider refresh publishes shared thread presentation keyed by normalized path and canonical thread identity so Agent Threads and open editor tabs show the same title/activity report. Agent Threads rows use canonical row activity, while activity counters use canonical chrome activity; shared presentation may refresh titles without changing counter buckets.
  [@test] ../../sessions/testSrc/AgentSessionThreadPresentationTest.kt
  [@test] ../../chat/testSrc/AgentChatEditorServiceTest.kt

- Activity and presentation updates carry evidence authority. Provisional provider signals may update row activity, but must not clear an existing attention-bucket chrome activity. Semantic provider signals and authoritative snapshots may clear attention when they explicitly report a non-attention activity.
  [@test] ../../sessions/testSrc/service/AgentSessionRefreshSchedulerTest.kt

- File-backed provider watchers must recover after watch-loop failures while the owning watcher remains active.
  [@test] ../../lib-agent/filewatch/testSrc/AgentWorkbenchDirectoryWatcherTest.kt

## Testing / Local Run
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test "com.intellij.agent.workbench.sessions.AgentSessionRefresh*Test"`
- `./tests.cmd --module intellij.agent.workbench.sessions.tests --test com.intellij.agent.workbench.sessions.AgentSessionLoadAggregationTest`
- `./tests.cmd --module intellij.platform.ai.agent.claude.sessions.tests --test com.intellij.platform.ai.agent.claude.sessions.ClaudeStoreSessionBackendTest`
- `./tests.cmd --module intellij.platform.ai.agent.filewatch.tests --test com.intellij.platform.ai.agent.filewatch.AgentWorkbenchDirectoryWatcherTest`

## References
- `agent-sessions.spec.md`
- `agent-sessions-tree.spec.md`
- `agent-sessions-thread-visibility.spec.md`
- `../chat/agent-chat-editor.spec.md`
