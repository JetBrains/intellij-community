---
name: Codex Sessions Rollout Source
description: Requirements for Codex thread discovery, activity derivation, backend selection, and archive/write interoperability.
targets:
  - ../codex/sessions/src/**/*.kt
  - ../codex/common/src/CodexAppServerClient.kt
  - ../sessions/src/CodexSessionsCompatibility.kt
  - ../sessions/src/AgentSessionModels.kt
  - ../sessions/src/SessionTreeStyle.kt
  - ../sessions/src/AgentSessionsService.kt
  - ../codex/sessions/testSrc/*.kt
  - ../sessions/testSrc/CodexAppServerClientTest.kt
---

# Codex Sessions Rollout Source

Status: Draft
Date: 2026-02-23

## Summary
Define Codex thread-list behavior where discovery defaults to rollout files under `~/.codex/sessions`, while write/archive/unarchive operations continue to use app-server RPC. This spec owns rollout parsing, watcher semantics, backend selection, and Codex activity derivation.

## Goals
- Keep Codex thread indicators aligned with rollout activity data.
- Keep app-server implementation available as explicit compatibility backend.
- Support archive and archive-undo unarchive operations for rollout-discovered threads through shared app-server write path.
- Keep backend policy independent from new-thread UI contracts.

## Non-goals
- Archived-thread browsing and standalone unarchive entry points outside archive-undo flow.
- Claude backend behavior.
- Polling-based refresh loops.

## Requirements
- Codex session listing must be implemented behind `CodexSessionBackend` interface.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- `CodexRolloutSessionBackend` must be default backend; `CodexAppServerSessionBackend` must remain available as alternate backend.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Backend selection must switch to app-server only when `agent.workbench.codex.sessions.backend=app-server` is explicitly set.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Unknown backend override values must log warning and fall back to rollout.
  [@test] ../codex/sessions/testSrc/CodexSessionBackendSelectorTest.kt

- Rollout backend scan scope must be limited to `~/.codex/sessions/**/rollout-*.jsonl`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Rollout change detection must use `AgentWorkbenchDirectoryWatcher` stack; Java NIO `WatchService` must not be used.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt

- Rollout refresh behavior must be event-driven; periodic polling timers are not allowed.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt

- Rollout backend must filter sessions by normalized `cwd` matching requested project/worktree path.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Multi-path prefetch must use a shared scan and return per-path filtered thread lists.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Cache invalidation must be path-scoped for rollout file changes; full rescan is allowed only for overflow/ambiguous directory events.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionsWatcherTest.kt

- Path-scoped invalidation must force reparse of dirty rollout paths even when file size and mtime are unchanged.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt

- Non-rollout file events under `~/.codex/sessions` must still trigger event-driven refresh (without forced full reparse) to support atomic temp/rename write patterns.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendFileWatchIntegrationTest.kt

- Thread id source must be `session_meta.payload.id`; rollout filename fallback is forbidden.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Files missing `session_meta.payload.id` must be skipped.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Title extraction must use first qualifying `event_msg` with `payload.type=user_message`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- `thread_name_updated` messages with non-blank `payload.thread_name` must override derived title.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Title normalization must strip `## My request for Codex:` marker when present, ignore session-prefix messages (`<environment_context>`, `<turn_aborted>`), normalize whitespace, and apply bounded trim.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- If no qualifying title is found, fallback title must be `Thread <id-prefix>`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Activity precedence must be `unread > reviewing > processing > ready`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Session-tree indicator colors for Codex activity must map to:
  - unread: `#4DA3FF`,
  - reviewing: `#2FD1C4`,
  - processing: `#FF9F43`,
  - ready: `#3FE47E`.
  [@test] ../codex/sessions/testSrc/CodexRolloutSessionBackendTest.kt

- Codex provider bridge must advertise archive capability and route archive/unarchive calls through shared app-server service when unarchive is supported.
  [@test] ../codex/sessions/testSrc/CodexAgentSessionProviderBridgeTest.kt
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Shared app-server process must start lazily on first request.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Shared app-server process must stop after configurable idle timeout when no requests are in flight; default timeout is 60 seconds.
  [@test] ../sessions/testSrc/CodexAppServerClientTest.kt

- Paging seed logic must guard against cursor loops and no-progress iterations; it must terminate safely without infinite looping and preserve already collected thread results.
  [@test] ../codex/sessions/testSrc/CodexSessionsPagingLogicTest.kt

- New-thread and resume command mapping must follow `spec/agent-core-contracts.spec.md`.
  [@test] ../sessions/testSrc/AgentSessionCliTest.kt

## User Experience
- Codex activity indicators should reflect recent rollout activity consistently.
- Archive action remains available for Codex threads discovered from rollout source.
- Archive undo should be available when Codex unarchive is supported by the active provider bridge.

## Data & Backend
- `updatedAt` derives from latest event timestamp with file mtime fallback.
- `response_item` contributes to activity timing but not title source extraction.
- Branch value comes from rollout session metadata when present; no branch fallback store is used.
- Listing stays rollout-backed by default; write operations (`thread/start`, `thread/archive`, `thread/unarchive`, persistence calls) remain app-server RPC.

## Error Handling
- Invalid override values must not disable Codex listing; fallback to rollout must apply.
- Parse failures must isolate to failing files and preserve valid thread rows from other files.
- Watcher overflow events may trigger full rescan to recover correctness.

## Testing / Local Run
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionBackendFileWatchIntegrationTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexRolloutSessionsWatcherTest'`
- `./tests.cmd '-Dintellij.build.test.patterns=com.intellij.agent.workbench.codex.sessions.CodexSessionBackendSelectorTest'`

## Open Questions / Risks
- Cross-platform filesystem event differences can still produce edge-case rescan spikes under heavy write churn.

## References
- `spec/agent-core-contracts.spec.md`
- `spec/agent-sessions.spec.md`
- `spec/actions/new-thread.spec.md`
